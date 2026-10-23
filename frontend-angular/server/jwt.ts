export interface JwtPayload {
  exp: number;
  iss?: string;
  sub?: string;
}

/** Liest die Claims eines JWT ohne Signaturprüfung (die Token stammen direkt von Keycloak). */
export function decodeJwt(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const claims = JSON.parse(Buffer.from(payload, 'base64url').toString());
    return typeof claims?.exp === 'number' ? claims : null;
  } catch {
    return null;
  }
}
