import express, { type Express, type NextFunction, type Request, type Response } from 'express';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import type { ServerConfig } from './config.ts';
import { decodeJwt } from './jwt.ts';

const ACCESS_COOKIE = 'kc_access';
const REFRESH_COOKIE = 'kc_refresh';
const EXPIRES_COOKIE = 'kc_expires';
const SESSION_COOKIES = [ACCESS_COOKIE, REFRESH_COOKIE, EXPIRES_COOKIE];
const SCOPE = 'openid profile email';
const PROXIED_REQUEST_HEADERS = ['content-type', 'accept', 'accept-language'];
const BODY_METHODS = ['POST', 'PUT', 'PATCH'];

interface TokenResponse {
  access_token?: string;
  refresh_token?: string;
}

function parseCookies(header: string | undefined): Record<string, string> {
  const cookies: Record<string, string> = {};
  for (const part of (header ?? '').split(';')) {
    const index = part.indexOf('=');
    if (index < 1) continue;
    try {
      cookies[part.slice(0, index).trim()] = decodeURIComponent(part.slice(index + 1).trim());
    } catch {
      // Cookie mit ungültiger Kodierung ignorieren
    }
  }
  return cookies;
}

export function createApp(config: ServerConfig): Express {
  const app = express();
  app.disable('x-powered-by');

  const cookieOptions = (expires: Date, httpOnly: boolean) => ({
    httpOnly,
    secure: config.cookieSecure,
    sameSite: 'lax' as const,
    path: '/',
    expires,
  });

  const clearSession = (res: Response) => {
    for (const name of SESSION_COOKIES) res.clearCookie(name, { path: '/' });
  };

  async function requestTokens(params: Record<string, string>): Promise<Required<TokenResponse>> {
    const response = await fetch(`${config.authIssuer}/protocol/openid-connect/token`, {
      method: 'POST',
      body: new URLSearchParams({ client_id: config.authClientId, ...params }),
    });
    if (!response.ok) throw new Error(`Keycloak antwortete mit ${response.status}`);
    const tokens = (await response.json()) as TokenResponse;
    if (!tokens.access_token || !tokens.refresh_token) {
      throw new Error('Keycloak lieferte eine unvollständige Token-Antwort');
    }
    return tokens as Required<TokenResponse>;
  }

  /** Setzt die drei Sitzungs-Cookies; wirft, wenn ein Token nicht lesbar ist. */
  function setSession(res: Response, tokens: Required<TokenResponse>): string {
    const access = decodeJwt(tokens.access_token);
    const refresh = decodeJwt(tokens.refresh_token);
    if (!access || !refresh) throw new Error('Token nicht lesbar');
    const refreshExpiry = new Date(refresh.exp * 1000);
    res.cookie(
      ACCESS_COOKIE,
      tokens.access_token,
      cookieOptions(new Date(access.exp * 1000), true),
    );
    res.cookie(REFRESH_COOKIE, tokens.refresh_token, cookieOptions(refreshExpiry, true));
    res.cookie(EXPIRES_COOKIE, String(access.exp), cookieOptions(refreshExpiry, false));
    return access.sub ?? '';
  }

  const keycloakRedirect = (endpoint: string, redirectUri: string) =>
    `${config.authIssuer}/protocol/openid-connect/${endpoint}` +
    `?client_id=${encodeURIComponent(config.authClientId)}` +
    `&redirect_uri=${encodeURIComponent(redirectUri)}` +
    `&response_type=code&scope=${encodeURIComponent(SCOPE)}`;

  app.get('/api/auth/login', (_req, res) => {
    res.redirect(302, keycloakRedirect('auth', config.authRedirectUri));
  });

  app.get('/api/auth/register', (_req, res) => {
    res.redirect(
      302,
      keycloakRedirect('registrations', `${config.authRedirectUri}?registered=true`),
    );
  });

  app.get('/api/auth/callback', async (req, res) => {
    const code = req.query['code'];
    if (typeof code !== 'string' || !code) {
      res.status(400).json({ message: 'Autorisierungscode fehlt' });
      return;
    }
    try {
      const tokens = await requestTokens({
        grant_type: 'authorization_code',
        redirect_uri: config.authRedirectUri,
        code,
      });
      setSession(res, tokens);
    } catch (err) {
      console.error('Login callback failed', err);
      res.status(502).json({ message: 'Keycloak returned invalid token response' });
      return;
    }
    res.redirect(302, '/agenda');
  });

  app.get('/api/auth/isLoggedIn', (req, res) => {
    const exp = parseInt(parseCookies(req.headers.cookie)[EXPIRES_COOKIE] ?? '', 10);
    res.sendStatus(exp * 1000 > Date.now() ? 204 : 401);
  });

  app.post('/api/auth/refresh', async (req, res) => {
    const refresh = parseCookies(req.headers.cookie)[REFRESH_COOKIE];
    if (!refresh) {
      res.clearCookie(ACCESS_COOKIE, { path: '/' });
      res.clearCookie(REFRESH_COOKIE, { path: '/' });
      res.status(401).json({ message: 'Please log in again' });
      return;
    }
    let tokens: Required<TokenResponse>;
    try {
      tokens = await requestTokens({ grant_type: 'refresh_token', refresh_token: refresh });
    } catch (err) {
      console.error('Token refresh failed', err);
      res.status(502).json({ message: 'Keycloak returned invalid token response' });
      return;
    }
    try {
      setSession(res, tokens);
    } catch (err) {
      clearSession(res);
      console.error('Token refresh failed', err);
      res.status(401).json({ message: 'Please log in again' });
      return;
    }
    res.sendStatus(204);
  });

  app.post('/api/auth/logout', async (req, res) => {
    const refresh = parseCookies(req.headers.cookie)[REFRESH_COOKIE];
    if (refresh) {
      try {
        await fetch(`${config.authIssuer}/protocol/openid-connect/logout`, {
          method: 'POST',
          body: new URLSearchParams({ client_id: config.authClientId, refresh_token: refresh }),
        });
      } catch (err) {
        // Fehler ignorieren, lokal wird trotzdem abgemeldet
        console.error('Keycloak logout error:', err);
      }
    }
    clearSession(res);
    res.json({ success: true });
  });

  app.get('/api/config', (_req, res) => {
    res.json({ impressum: config.impressum });
  });

  app.use('/api/v2', express.raw({ type: () => true, limit: '10mb' }), async (req, res) => {
    const headers = new Headers();
    for (const name of PROXIED_REQUEST_HEADERS) {
      const value = req.headers[name];
      if (typeof value === 'string') headers.set(name, value);
    }
    const access = parseCookies(req.headers.cookie)[ACCESS_COOKIE];
    if (access) headers.set('authorization', `Bearer ${access}`);

    const hasBody = BODY_METHODS.includes(req.method) && Buffer.isBuffer(req.body);
    try {
      const upstream = await fetch(`${config.quarkusUrl}/api/v2${req.url}`, {
        method: req.method,
        headers,
        body: hasBody ? new Uint8Array(req.body) : undefined,
      });
      const contentType = upstream.headers.get('content-type');
      if (contentType) res.type(contentType);
      res.status(upstream.status).send(Buffer.from(await upstream.arrayBuffer()));
    } catch (err) {
      console.error('Backend request failed', err);
      res.status(502).json({ message: 'Backend nicht erreichbar' });
    }
  });

  app.use('/api', (_req, res) => {
    res.status(404).json({ message: 'Nicht gefunden' });
  });

  app.use(redirectUnauthenticated);
  app.use(express.static(config.staticDir, { index: false }));
  app.use((req: Request, res: Response, next: NextFunction) => {
    const index = join(config.staticDir, 'index.html');
    if (
      (req.method !== 'GET' && req.method !== 'HEAD') ||
      isStaticAsset(req.path) ||
      !existsSync(index)
    ) {
      next();
      return;
    }
    res.sendFile(index);
  });

  return app;
}

/** Anfragen ohne Sitzungs-Cookies auf geschützte Seiten gehen per 301 auf die Startseite. */
function redirectUnauthenticated(req: Request, res: Response, next: NextFunction): void {
  const isPage = req.method === 'GET' || req.method === 'HEAD';
  const exempt = req.path === '/' || req.path === '/public' || req.path.startsWith('/public/');
  const cookies = parseCookies(req.headers.cookie);
  const hasSession = SESSION_COOKIES.some((name) => name in cookies);
  if (isPage && !exempt && !hasSession && !isStaticAsset(req.path)) {
    res.redirect(301, '/');
    return;
  }
  next();
}

/** Dateien mit Endung (Bundles, Manifest, Service Worker, Icons) bleiben ohne Anmeldung erreichbar. */
function isStaticAsset(path: string): boolean {
  return /\.[a-z0-9]+$/i.test(path.split('/').pop() ?? '');
}
