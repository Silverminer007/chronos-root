import { resolve } from 'node:path';

export interface ImpressumConfig {
  name: string;
  street: string;
  city: string;
  email: string;
  phone: string;
}

export interface ServerConfig {
  port: number;
  staticDir: string;
  cookieSecure: boolean;
  authIssuer: string;
  authClientId: string;
  authRedirectUri: string;
  quarkusUrl: string;
  impressum: ImpressumConfig;
}

type Env = Record<string, string | undefined>;

const DEFAULT_PORT = 3000;
const DEFAULT_STATIC_DIR = resolve(import.meta.dirname, '..', 'dist', 'browser');

/** `CHRONOS_<name>` mit Fallback auf die alten `NUXT_<name>`-Namen (Helm-Chart bleibt unverändert). */
function setting(env: Env, name: string): string {
  return env[`CHRONOS_${name}`] ?? env[`NUXT_${name}`] ?? '';
}

function withoutTrailingSlash(url: string): string {
  return url.replace(/\/+$/, '');
}

export function loadConfig(env: Env): ServerConfig {
  return {
    port: Number(env['CHRONOS_PORT'] ?? env['PORT'] ?? DEFAULT_PORT),
    staticDir: env['CHRONOS_STATIC_DIR'] ?? DEFAULT_STATIC_DIR,
    cookieSecure: env['CHRONOS_COOKIE_SECURE'] !== 'false',
    authIssuer: withoutTrailingSlash(setting(env, 'AUTH_ISSUER')),
    authClientId: setting(env, 'AUTH_CLIENT_ID'),
    authRedirectUri: setting(env, 'AUTH_REDIRECT_URI'),
    quarkusUrl: withoutTrailingSlash(setting(env, 'QUARKUS_URL')),
    impressum: {
      name: setting(env, 'PUBLIC_IMPRESSUM_NAME'),
      street: setting(env, 'PUBLIC_IMPRESSUM_STREET'),
      city: setting(env, 'PUBLIC_IMPRESSUM_CITY'),
      email: setting(env, 'PUBLIC_IMPRESSUM_EMAIL'),
      phone: setting(env, 'PUBLIC_IMPRESSUM_PHONE'),
    },
  };
}

/** Namen der Pflicht-Umgebungsvariablen, die nicht gesetzt sind. */
export function missingSettings(config: ServerConfig): string[] {
  const required: [string, string][] = [
    ['CHRONOS_AUTH_ISSUER', config.authIssuer],
    ['CHRONOS_AUTH_CLIENT_ID', config.authClientId],
    ['CHRONOS_AUTH_REDIRECT_URI', config.authRedirectUri],
    ['CHRONOS_QUARKUS_URL', config.quarkusUrl],
  ];
  return required.filter(([, value]) => !value).map(([name]) => name);
}
