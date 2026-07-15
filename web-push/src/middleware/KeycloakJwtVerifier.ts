import { createRemoteJWKSet, jwtVerify } from 'jose'
import type { IJwtVerifier, VerifiedToken } from './IJwtVerifier.js'

export class KeycloakJwtVerifier implements IJwtVerifier {
  private readonly issuer: string
  private readonly jwks: ReturnType<typeof createRemoteJWKSet>

  constructor(issuer: string) {
    this.issuer = issuer
    this.jwks = createRemoteJWKSet(new URL(`${issuer}/protocol/openid-connect/certs`))
  }

  async verify(token: string): Promise<VerifiedToken> {
    const { payload } = await jwtVerify(token, this.jwks, { issuer: this.issuer })

    if (typeof payload.sub !== 'string') {
      throw new Error('JWT missing sub claim')
    }

    return {
      sub: payload.sub,
      scope: typeof payload.scope === 'string' ? payload.scope : undefined,
      realm_access:
        payload.realm_access != null &&
        typeof payload.realm_access === 'object' &&
        'roles' in payload.realm_access
          ? (payload.realm_access as { roles: string[] })
          : undefined,
    }
  }
}
