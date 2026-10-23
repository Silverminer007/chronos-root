import { describe, expect, it } from 'vitest';
import { loadConfig, missingSettings } from './config.ts';

describe('loadConfig', () => {
  it('liest CHRONOS_*-Variablen', () => {
    const config = loadConfig({
      CHRONOS_AUTH_ISSUER: 'https://kc/realms/r/',
      CHRONOS_AUTH_CLIENT_ID: 'web',
      CHRONOS_AUTH_REDIRECT_URI: 'https://app/api/auth/callback',
      CHRONOS_QUARKUS_URL: 'http://backend:8080/',
      CHRONOS_PUBLIC_IMPRESSUM_NAME: 'Max',
      CHRONOS_PUBLIC_IMPRESSUM_STREET: 'Weg 1',
      CHRONOS_PUBLIC_IMPRESSUM_CITY: 'Ort',
      CHRONOS_PUBLIC_IMPRESSUM_EMAIL: 'a@b.de',
      CHRONOS_PUBLIC_IMPRESSUM_PHONE: '1',
    });
    expect(config.authIssuer).toBe('https://kc/realms/r');
    expect(config.authClientId).toBe('web');
    expect(config.authRedirectUri).toBe('https://app/api/auth/callback');
    expect(config.quarkusUrl).toBe('http://backend:8080');
    expect(config.impressum).toEqual({
      name: 'Max',
      street: 'Weg 1',
      city: 'Ort',
      email: 'a@b.de',
      phone: '1',
    });
  });

  it('fällt auf NUXT_*-Variablen zurück', () => {
    const config = loadConfig({
      NUXT_AUTH_ISSUER: 'https://kc/realms/old',
      NUXT_AUTH_CLIENT_ID: 'old',
      NUXT_AUTH_REDIRECT_URI: 'https://old/cb',
      NUXT_QUARKUS_URL: 'http://old:8080',
      NUXT_PUBLIC_IMPRESSUM_NAME: 'Alt',
    });
    expect(config.authIssuer).toBe('https://kc/realms/old');
    expect(config.authClientId).toBe('old');
    expect(config.quarkusUrl).toBe('http://old:8080');
    expect(config.impressum.name).toBe('Alt');
  });

  it('bevorzugt CHRONOS_* vor NUXT_*', () => {
    const config = loadConfig({ CHRONOS_AUTH_CLIENT_ID: 'new', NUXT_AUTH_CLIENT_ID: 'old' });
    expect(config.authClientId).toBe('new');
  });

  it('nutzt Standardwerte für Port, Statikverzeichnis und Secure-Cookies', () => {
    const config = loadConfig({});
    expect(config.port).toBe(3000);
    expect(config.staticDir).toMatch(/dist[\\/]browser$/);
    expect(config.cookieSecure).toBe(true);
  });

  it('liest Port, Statikverzeichnis und Cookie-Secure', () => {
    const config = loadConfig({
      CHRONOS_PORT: '8081',
      CHRONOS_STATIC_DIR: '/srv/www',
      CHRONOS_COOKIE_SECURE: 'false',
    });
    expect(config.port).toBe(8081);
    expect(config.staticDir).toBe('/srv/www');
    expect(config.cookieSecure).toBe(false);
  });

  it('meldet fehlende Pflichteinstellungen', () => {
    expect(missingSettings(loadConfig({}))).toEqual([
      'CHRONOS_AUTH_ISSUER',
      'CHRONOS_AUTH_CLIENT_ID',
      'CHRONOS_AUTH_REDIRECT_URI',
      'CHRONOS_QUARKUS_URL',
    ]);
    expect(
      missingSettings(
        loadConfig({
          CHRONOS_AUTH_ISSUER: 'a',
          CHRONOS_AUTH_CLIENT_ID: 'b',
          CHRONOS_AUTH_REDIRECT_URI: 'c',
          CHRONOS_QUARKUS_URL: 'd',
        }),
      ),
    ).toEqual([]);
  });
});
