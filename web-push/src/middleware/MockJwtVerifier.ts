import type { IJwtVerifier, VerifiedToken } from './IJwtVerifier.js'

export class MockJwtVerifier implements IJwtVerifier {
  private tokens: Map<string, VerifiedToken> = new Map()

  register(token: string, payload: VerifiedToken): void {
    this.tokens.set(token, payload)
  }

  async verify(token: string): Promise<VerifiedToken> {
    const payload = this.tokens.get(token)
    if (!payload) throw new Error('Invalid or unknown token')
    return payload
  }
}
