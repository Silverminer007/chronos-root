import { describe, it, expect, vi, beforeEach } from 'vitest'
import { KeycloakJwtVerifier } from '../../middleware/KeycloakJwtVerifier.js'

const ISSUER = 'https://auth.example.com/realms/chronos'
const MOCK_JWKS_FN = vi.fn()

vi.mock('jose', () => ({
  createRemoteJWKSet: vi.fn(() => MOCK_JWKS_FN),
  jwtVerify: vi.fn(),
}))

async function getJoseMocks() {
  const jose = await import('jose')
  return {
    createRemoteJWKSet: jose.createRemoteJWKSet as ReturnType<typeof vi.fn>,
    jwtVerify: jose.jwtVerify as ReturnType<typeof vi.fn>,
  }
}

describe('KeycloakJwtVerifier constructor', () => {
  it('initialises the JWKS client from the Keycloak certs endpoint', async () => {
    const { createRemoteJWKSet } = await getJoseMocks()
    new KeycloakJwtVerifier(ISSUER)
    expect(createRemoteJWKSet).toHaveBeenCalledWith(
      new URL(`${ISSUER}/protocol/openid-connect/certs`),
    )
  })
})

describe('KeycloakJwtVerifier.verify()', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    // Re-register the JWKS mock after clearAllMocks resets the factory
    const { createRemoteJWKSet } = await getJoseMocks()
    createRemoteJWKSet.mockReturnValue(MOCK_JWKS_FN)
  })

  it('returns a VerifiedToken with sub and scope on a valid token', async () => {
    const { jwtVerify } = await getJoseMocks()
    jwtVerify.mockResolvedValue({
      payload: { sub: 'user-uuid-abc', scope: 'openid profile push:send' },
    })

    const verifier = new KeycloakJwtVerifier(ISSUER)
    const result = await verifier.verify('valid.jwt.token')

    expect(result.sub).toBe('user-uuid-abc')
    expect(result.scope).toBe('openid profile push:send')
    expect(result.realm_access).toBeUndefined()
  })

  it('extracts realm_access.roles when present', async () => {
    const { jwtVerify } = await getJoseMocks()
    jwtVerify.mockResolvedValue({
      payload: {
        sub: 'user-uuid-abc',
        realm_access: { roles: ['offline_access', 'uma_authorization'] },
      },
    })

    const verifier = new KeycloakJwtVerifier(ISSUER)
    const result = await verifier.verify('valid.jwt.token')

    expect(result.realm_access?.roles).toEqual(['offline_access', 'uma_authorization'])
  })

  it('passes the issuer to jwtVerify for validation', async () => {
    const { jwtVerify } = await getJoseMocks()
    jwtVerify.mockResolvedValue({ payload: { sub: 'abc' } })

    const verifier = new KeycloakJwtVerifier(ISSUER)
    await verifier.verify('a.b.c')

    expect(jwtVerify).toHaveBeenCalledWith('a.b.c', MOCK_JWKS_FN, { issuer: ISSUER })
  })

  it('throws when jwtVerify rejects (expired token)', async () => {
    const { jwtVerify } = await getJoseMocks()
    jwtVerify.mockRejectedValue(new Error('JWTExpired: token is expired'))

    const verifier = new KeycloakJwtVerifier(ISSUER)
    await expect(verifier.verify('expired.jwt.token')).rejects.toThrow('JWTExpired')
  })

  it('throws when jwtVerify rejects (wrong issuer)', async () => {
    const { jwtVerify } = await getJoseMocks()
    jwtVerify.mockRejectedValue(new Error('JWTClaimValidationFailed: issuer mismatch'))

    const verifier = new KeycloakJwtVerifier(ISSUER)
    await expect(verifier.verify('wrong-issuer.jwt')).rejects.toThrow('JWTClaimValidationFailed')
  })

  it('throws when jwtVerify rejects (invalid signature)', async () => {
    const { jwtVerify } = await getJoseMocks()
    jwtVerify.mockRejectedValue(new Error('JWSSignatureVerificationFailed'))

    const verifier = new KeycloakJwtVerifier(ISSUER)
    await expect(verifier.verify('tampered.jwt')).rejects.toThrow('JWSSignatureVerificationFailed')
  })

  it('throws when the JWT payload is missing the sub claim', async () => {
    const { jwtVerify } = await getJoseMocks()
    jwtVerify.mockResolvedValue({ payload: { scope: 'openid' } })

    const verifier = new KeycloakJwtVerifier(ISSUER)
    await expect(verifier.verify('no-sub.jwt')).rejects.toThrow('JWT missing sub claim')
  })

  it('omits scope when it is not a string in the payload', async () => {
    const { jwtVerify } = await getJoseMocks()
    jwtVerify.mockResolvedValue({ payload: { sub: 'abc', scope: 42 } })

    const verifier = new KeycloakJwtVerifier(ISSUER)
    const result = await verifier.verify('valid.jwt')
    expect(result.scope).toBeUndefined()
  })

  it('omits realm_access when it has no roles field', async () => {
    const { jwtVerify } = await getJoseMocks()
    jwtVerify.mockResolvedValue({
      payload: { sub: 'abc', realm_access: { other_field: true } },
    })

    const verifier = new KeycloakJwtVerifier(ISSUER)
    const result = await verifier.verify('valid.jwt')
    expect(result.realm_access).toBeUndefined()
  })
})
