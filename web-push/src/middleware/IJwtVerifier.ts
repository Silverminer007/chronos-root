export interface VerifiedToken {
  sub: string
  scope?: string
  realm_access?: { roles?: string[] }
}

export interface IJwtVerifier {
  verify(token: string): Promise<VerifiedToken>
}
