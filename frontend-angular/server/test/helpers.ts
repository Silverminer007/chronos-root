import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { mkdtempSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createApp } from '../app.ts';
import type { ServerConfig } from '../config.ts';

export interface RecordedRequest {
  method: string;
  url: string;
  headers: IncomingMessage['headers'];
  body: string;
}

export type MockHandler = (req: RecordedRequest, res: ServerResponse) => void;

export interface MockServer {
  url: string;
  requests: RecordedRequest[];
  handler: MockHandler;
  close: () => Promise<void>;
}

export function jwt(payload: Record<string, unknown>): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'none' })}.${encode(payload)}.signature`;
}

export async function listen(server: Server): Promise<string> {
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  return `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
}

export async function startMock(initial: MockHandler): Promise<MockServer> {
  const mock: MockServer = {
    url: '',
    requests: [],
    handler: initial,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
  const server = createServer((req, res) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk: Buffer) => chunks.push(chunk));
    req.on('end', () => {
      const recorded = {
        method: req.method ?? '',
        url: req.url ?? '',
        headers: req.headers,
        body: Buffer.concat(chunks).toString(),
      };
      mock.requests.push(recorded);
      mock.handler(recorded, res);
    });
  });
  mock.url = await listen(server);
  return mock;
}

export function json(res: ServerResponse, status: number, body: unknown): void {
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(JSON.stringify(body));
}

export function makeStaticDir(): string {
  const dir = mkdtempSync(join(tmpdir(), 'bff-static-'));
  writeFileSync(join(dir, 'index.html'), '<!doctype html><title>SPA</title>');
  writeFileSync(join(dir, 'main.js'), 'console.log(1)');
  mkdirSync(join(dir, 'assets'));
  return dir;
}

export interface Harness {
  baseUrl: string;
  keycloak: MockServer;
  backend: MockServer;
  config: ServerConfig;
  close: () => Promise<void>;
}

export async function startHarness(overrides: Partial<ServerConfig> = {}): Promise<Harness> {
  const keycloak = await startMock((_req, res) => json(res, 500, {}));
  const backend = await startMock((_req, res) => json(res, 200, {}));
  const config: ServerConfig = {
    port: 0,
    staticDir: makeStaticDir(),
    cookieSecure: true,
    authIssuer: `${keycloak.url}/realms/chronos`,
    authClientId: 'chronos-web',
    authRedirectUri: 'https://app.example/api/auth/callback',
    quarkusUrl: backend.url,
    impressum: { name: 'Max', street: 'Weg 1', city: '12345 Ort', email: 'a@b.de', phone: '123' },
    ...overrides,
  };
  const app = createApp(config);
  const server = createServer(app);
  const baseUrl = await listen(server);
  return {
    baseUrl,
    keycloak,
    backend,
    config,
    close: async () => {
      await new Promise<void>((resolve) => server.close(() => resolve()));
      server.closeAllConnections();
      await keycloak.close();
      await backend.close();
    },
  };
}
