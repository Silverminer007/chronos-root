import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { json, jwt, startHarness, type Harness } from './test/helpers.ts';

const ACCESS_EXP = Math.floor(Date.now() / 1000) + 300;
const REFRESH_EXP = Math.floor(Date.now() / 1000) + 3600;
const tokens = () => ({
  access_token: jwt({ exp: ACCESS_EXP, iss: 'https://kc/realms/r', sub: 'user-1' }),
  refresh_token: jwt({ exp: REFRESH_EXP }),
});

let h: Harness;

beforeAll(async () => {
  h = await startHarness();
});
afterAll(() => h.close());
beforeEach(() => {
  h.keycloak.requests.length = 0;
  h.backend.requests.length = 0;
});

const get = (path: string, cookie?: string) =>
  fetch(h.baseUrl + path, { redirect: 'manual', headers: cookie ? { cookie } : {} });
const setCookies = (res: Response) => res.headers.getSetCookie();
const cookieNamed = (res: Response, name: string) =>
  setCookies(res).find((c) => c.startsWith(`${name}=`));

describe('GET /api/auth/login und /register', () => {
  it('leitet auf den Keycloak-Authorization-Endpoint um', async () => {
    const res = await get('/api/auth/login');
    expect(res.status).toBe(302);
    const location = new URL(res.headers.get('location')!);
    expect(location.origin + location.pathname).toBe(
      `${h.config.authIssuer}/protocol/openid-connect/auth`,
    );
    expect(location.searchParams.get('client_id')).toBe('chronos-web');
    expect(location.searchParams.get('redirect_uri')).toBe(h.config.authRedirectUri);
    expect(location.searchParams.get('response_type')).toBe('code');
    expect(location.searchParams.get('scope')).toBe('openid profile email');
  });

  it('leitet für die Registrierung auf den Registrations-Endpoint um', async () => {
    const res = await get('/api/auth/register');
    expect(res.status).toBe(302);
    const location = new URL(res.headers.get('location')!);
    expect(location.pathname).toMatch(/\/protocol\/openid-connect\/registrations$/);
    expect(location.searchParams.get('redirect_uri')).toBe(
      `${h.config.authRedirectUri}?registered=true`,
    );
  });
});

describe('GET /api/auth/callback', () => {
  it('tauscht den Code gegen Token, setzt Cookies und leitet auf /agenda', async () => {
    const t = tokens();
    h.keycloak.handler = (_req, res) => json(res, 200, t);
    const res = await get('/api/auth/callback?code=abc');

    expect(res.status).toBe(302);
    expect(res.headers.get('location')).toBe('/agenda');
    const request = h.keycloak.requests[0];
    expect(request.method).toBe('POST');
    expect(request.url).toBe('/realms/chronos/protocol/openid-connect/token');
    const form = new URLSearchParams(request.body);
    expect(form.get('grant_type')).toBe('authorization_code');
    expect(form.get('code')).toBe('abc');
    expect(form.get('client_id')).toBe('chronos-web');
    expect(form.get('redirect_uri')).toBe(h.config.authRedirectUri);

    const access = cookieNamed(res, 'kc_access')!;
    expect(access).toContain(t.access_token);
    expect(access).toContain('HttpOnly');
    expect(access).toContain('Secure');
    expect(access).toContain('SameSite=Lax');
    expect(access).toContain('Path=/');
    expect(new Date(/Expires=([^;]+)/.exec(access)![1]).getTime()).toBe(ACCESS_EXP * 1000);
    const refresh = cookieNamed(res, 'kc_refresh')!;
    expect(refresh).toContain('HttpOnly');
    expect(new Date(/Expires=([^;]+)/.exec(refresh)![1]).getTime()).toBe(REFRESH_EXP * 1000);
    const expires = cookieNamed(res, 'kc_expires')!;
    expect(expires).toContain(`kc_expires=${ACCESS_EXP}`);
    expect(expires).not.toContain('HttpOnly');
  });

  it('antwortet 400 ohne Code', async () => {
    const res = await get('/api/auth/callback');
    expect(res.status).toBe(400);
    expect(h.keycloak.requests).toHaveLength(0);
  });

  it('antwortet 502, wenn Keycloak den Austausch ablehnt', async () => {
    h.keycloak.handler = (_req, res) => json(res, 400, { error: 'invalid_grant' });
    const res = await get('/api/auth/callback?code=bad');
    expect(res.status).toBe(502);
    expect(setCookies(res)).toHaveLength(0);
  });

  it('antwortet 502, wenn die Token-Antwort unvollständig ist', async () => {
    h.keycloak.handler = (_req, res) => json(res, 200, { access_token: tokens().access_token });
    const res = await get('/api/auth/callback?code=abc');
    expect(res.status).toBe(502);
  });
});

describe('GET /api/auth/isLoggedIn', () => {
  it('antwortet 204 bei gültigem kc_expires', async () => {
    const res = await get('/api/auth/isLoggedIn', `kc_expires=${ACCESS_EXP}`);
    expect(res.status).toBe(204);
  });

  it('antwortet 401 ohne Cookie', async () => {
    expect((await get('/api/auth/isLoggedIn')).status).toBe(401);
  });

  it('antwortet 401 bei abgelaufenem kc_expires', async () => {
    const expired = Math.floor(Date.now() / 1000) - 5;
    expect((await get('/api/auth/isLoggedIn', `kc_expires=${expired}`)).status).toBe(401);
  });

  it('antwortet 401 bei ungültigem kc_expires', async () => {
    expect((await get('/api/auth/isLoggedIn', 'kc_expires=abc')).status).toBe(401);
  });
});

describe('POST /api/auth/refresh', () => {
  const post = (path: string, cookie?: string) =>
    fetch(h.baseUrl + path, { method: 'POST', headers: cookie ? { cookie } : {} });

  it('erneuert die Token und setzt alle drei Cookies', async () => {
    const t = tokens();
    h.keycloak.handler = (_req, res) => json(res, 200, t);
    const res = await post('/api/auth/refresh', 'kc_refresh=old-refresh');

    expect(res.status).toBe(204);
    const form = new URLSearchParams(h.keycloak.requests[0].body);
    expect(form.get('grant_type')).toBe('refresh_token');
    expect(form.get('refresh_token')).toBe('old-refresh');
    expect(form.get('client_id')).toBe('chronos-web');
    expect(cookieNamed(res, 'kc_access')).toContain(t.access_token);
    expect(cookieNamed(res, 'kc_refresh')).toContain(t.refresh_token);
    expect(cookieNamed(res, 'kc_expires')).toContain(`kc_expires=${ACCESS_EXP}`);
  });

  it('antwortet 401 ohne Refresh-Token und löscht die Cookies', async () => {
    const res = await post('/api/auth/refresh');
    expect(res.status).toBe(401);
    expect(cookieNamed(res, 'kc_access')).toMatch(/Expires=Thu, 01 Jan 1970/);
    expect(cookieNamed(res, 'kc_refresh')).toMatch(/Expires=Thu, 01 Jan 1970/);
    expect(h.keycloak.requests).toHaveLength(0);
  });

  it('antwortet 502, wenn Keycloak die Erneuerung ablehnt', async () => {
    h.keycloak.handler = (_req, res) => json(res, 400, { error: 'invalid_grant' });
    expect((await post('/api/auth/refresh', 'kc_refresh=x')).status).toBe(502);
  });

  it('antwortet 502 bei unvollständiger Token-Antwort', async () => {
    h.keycloak.handler = (_req, res) => json(res, 200, {});
    expect((await post('/api/auth/refresh', 'kc_refresh=x')).status).toBe(502);
  });

  it('antwortet 401 und löscht Cookies bei nicht lesbaren Token', async () => {
    h.keycloak.handler = (_req, res) =>
      json(res, 200, { access_token: 'kaputt', refresh_token: 'kaputt' });
    const res = await post('/api/auth/refresh', 'kc_refresh=x');
    expect(res.status).toBe(401);
    expect(cookieNamed(res, 'kc_expires')).toMatch(/Expires=Thu, 01 Jan 1970/);
  });
});

describe('POST /api/auth/logout', () => {
  it('meldet bei Keycloak ab und löscht die Cookies', async () => {
    h.keycloak.handler = (_req, res) => {
      res.writeHead(204);
      res.end();
    };
    const res = await fetch(h.baseUrl + '/api/auth/logout', {
      method: 'POST',
      headers: { cookie: 'kc_refresh=r1; kc_access=a1; kc_expires=1' },
    });
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ success: true });
    expect(h.keycloak.requests[0].url).toBe('/realms/chronos/protocol/openid-connect/logout');
    expect(new URLSearchParams(h.keycloak.requests[0].body).get('refresh_token')).toBe('r1');
    for (const name of ['kc_access', 'kc_refresh', 'kc_expires']) {
      expect(cookieNamed(res, name)).toMatch(/Expires=Thu, 01 Jan 1970/);
    }
  });

  it('löscht Cookies auch ohne Refresh-Token ohne Keycloak-Aufruf', async () => {
    const res = await fetch(h.baseUrl + '/api/auth/logout', { method: 'POST' });
    expect(res.status).toBe(200);
    expect(h.keycloak.requests).toHaveLength(0);
  });

  it('löscht Cookies, auch wenn Keycloak fehlschlägt', async () => {
    h.keycloak.handler = (_req, res) => json(res, 500, {});
    const res = await fetch(h.baseUrl + '/api/auth/logout', {
      method: 'POST',
      headers: { cookie: 'kc_refresh=r1' },
    });
    expect(res.status).toBe(200);
    expect(cookieNamed(res, 'kc_refresh')).toMatch(/Expires=Thu, 01 Jan 1970/);
  });
});

describe('GET /api/config', () => {
  it('liefert die öffentlichen Impressum-Angaben', async () => {
    const res = await get('/api/config');
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ impressum: h.config.impressum });
  });
});

describe('/api/v2 Proxy', () => {
  it('leitet Methode, Pfad, Query und Bearer-Token an das Backend weiter', async () => {
    h.backend.handler = (_req, res) => json(res, 200, [{ id: 1 }]);
    const res = await get('/api/v2/appointments/42?page=2&size=5', 'kc_access=tok123');

    expect(res.status).toBe(200);
    expect(await res.json()).toEqual([{ id: 1 }]);
    const request = h.backend.requests[0];
    expect(request.method).toBe('GET');
    expect(request.url).toBe('/api/v2/appointments/42?page=2&size=5');
    expect(request.headers.authorization).toBe('Bearer tok123');
  });

  it('leitet JSON-Bodies bei POST/PUT/PATCH weiter', async () => {
    h.backend.handler = (_req, res) => json(res, 201, { ok: true });
    for (const method of ['POST', 'PUT', 'PATCH']) {
      const res = await fetch(h.baseUrl + '/api/v2/groups', {
        method,
        headers: { cookie: 'kc_access=t', 'content-type': 'application/json' },
        body: JSON.stringify({ name: 'Gruppe' }),
      });
      expect(res.status).toBe(201);
    }
    expect(h.backend.requests.map((r) => r.method)).toEqual(['POST', 'PUT', 'PATCH']);
    for (const request of h.backend.requests) {
      expect(JSON.parse(request.body)).toEqual({ name: 'Gruppe' });
      expect(request.headers['content-type']).toBe('application/json');
    }
  });

  it('leitet DELETE ohne Body weiter', async () => {
    h.backend.handler = (_req, res) => {
      res.writeHead(204);
      res.end();
    };
    const res = await fetch(h.baseUrl + '/api/v2/groups/1', {
      method: 'DELETE',
      headers: { cookie: 'kc_access=t' },
    });
    expect(res.status).toBe(204);
    expect(h.backend.requests[0].method).toBe('DELETE');
  });

  it('reicht Fehlerstatus und Body des Backends unverändert durch', async () => {
    h.backend.handler = (_req, res) => json(res, 404, { message: 'nicht gefunden' });
    const res = await get('/api/v2/appointments/9', 'kc_access=t');
    expect(res.status).toBe(404);
    expect(await res.json()).toEqual({ message: 'nicht gefunden' });
  });

  it('sendet ohne kc_access keinen Authorization-Header', async () => {
    await get('/api/v2/appointments', 'kc_expires=1');
    expect(h.backend.requests[0].headers.authorization).toBeUndefined();
  });

  it('übernimmt keine Browser-Cookies zum Backend', async () => {
    await get('/api/v2/appointments', 'kc_access=t; kc_refresh=secret');
    expect(h.backend.requests[0].headers.cookie).toBeUndefined();
  });

  it('antwortet 502, wenn das Backend nicht erreichbar ist', async () => {
    const unreachable = await startHarness({ quarkusUrl: 'http://127.0.0.1:1' });
    try {
      const res = await fetch(unreachable.baseUrl + '/api/v2/x', {
        headers: { cookie: 'kc_access=t' },
      });
      expect(res.status).toBe(502);
    } finally {
      await unreachable.close();
    }
  });
});

describe('statisches Hosting und Redirects', () => {
  it('liefert die Startseite ohne Cookies', async () => {
    const res = await get('/');
    expect(res.status).toBe(200);
    expect(await res.text()).toContain('<title>SPA</title>');
  });

  it('liefert statische Dateien ohne Cookies', async () => {
    const res = await get('/main.js');
    expect(res.status).toBe(200);
    expect(await res.text()).toBe('console.log(1)');
  });

  for (const route of ['/agenda', '/settings', '/profile', '/friends', '/groups', '/agenda/42']) {
    it(`leitet ${route} ohne Cookies per 301 auf / um`, async () => {
      const res = await get(route);
      expect(res.status).toBe(301);
      expect(res.headers.get('location')).toBe('/');
    });
  }

  it('liefert /public/* ohne Cookies per SPA-Fallback', async () => {
    const res = await get('/public/impressum');
    expect(res.status).toBe(200);
    expect(await res.text()).toContain('<title>SPA</title>');
  });

  it('liefert geschützte Routen mit Sitzungs-Cookie per SPA-Fallback', async () => {
    const res = await get('/agenda', `kc_expires=${ACCESS_EXP}`);
    expect(res.status).toBe(200);
    expect(await res.text()).toContain('<title>SPA</title>');
  });

  it('antwortet 404 für fehlende Dateien statt der Startseite', async () => {
    expect((await get('/fehlt.js')).status).toBe(404);
  });

  it('beantwortet unbekannte /api-Pfade mit 404', async () => {
    expect((await get('/api/unbekannt')).status).toBe(404);
  });
});
