import {getCookie, setCookie, deleteCookie, type H3Event} from "h3"
import {decodeJwt} from "../utils/decodeJwt"
import {$fetch} from "ofetch"

// Cache refreshed tokens during SSR to avoid race conditions
// When multiple parallel requests try to refresh with the same token,
// only the first succeeds (Keycloak refresh token rotation)
// Key: first 32 chars of refresh token, Value: { accessToken, timestamp }
const tokenCache = new Map<string, { accessToken: string, timestamp: number }>()
const CACHE_TTL = 10000 // 10 seconds - enough for SSR to complete

function getCacheKey(refreshToken: string): string {
    return refreshToken.substring(0, 32)
}

function getCachedToken(refreshToken: string): string | null {
    const key = getCacheKey(refreshToken)
    const cached = tokenCache.get(key)
    if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
        return cached.accessToken
    }
    // Cleanup expired entry
    if (cached) {
        tokenCache.delete(key)
    }
    return null
}

function setCachedToken(refreshToken: string, accessToken: string) {
    const key = getCacheKey(refreshToken)
    tokenCache.set(key, { accessToken, timestamp: Date.now() })
}

export default defineEventHandler(async (event) => {
    const access = getCookie(event, "kc_access")
    const refresh = getCookie(event, "kc_refresh")

    // Ohne Refresh Token können wir nichts tun
    if (!refresh) {
        deleteCookie(event, "kc_access")
        deleteCookie(event, "kc_refresh")
        if (!event.node.req.originalUrl?.startsWith('/api')
            && event.node.req.originalUrl !== '/'
            && !event.node.req.originalUrl?.startsWith('/public')) {
            return sendRedirect(event, '/', 301);
        } else {
            return;
        }
    }

    // Check if we have a recently refreshed token in cache
    // This handles parallel SSR requests that would otherwise race
    const cachedToken = getCachedToken(refresh)
    if (cachedToken) {
        event.context.accessToken = cachedToken
        return
    }

    const config = useRuntimeConfig()

    // Wenn wir kein Access Token haben → Refresh versuchen
    if (!access) {
        return await refreshTokens(event, config, refresh)
    }

    // Access Token parsen
    const jwt = decodeJwt(access)
    if (!jwt || !jwt.exp) {
        return await refreshTokens(event, config, refresh)
    }

    const now = Math.floor(Date.now() / 1000)

    // Token erneuern, wenn weniger als 30 Sekunden übrig sind
    const expiresIn = jwt.exp - now
    const threshold = 30

    if (expiresIn < threshold) {
        return await refreshTokens(event, config, refresh)
    }

    event.context.accessToken = access
})

async function refreshTokens(event: H3Event, config: ReturnType<typeof useRuntimeConfig>, refreshToken: string) {
    try {
        const tokenResponse = await $fetch(
            `${config.auth.issuer}/protocol/openid-connect/token`,
            {
                method: "POST",
                body: new URLSearchParams({
                    grant_type: "refresh_token",
                    client_id: config.auth.clientId,
                    refresh_token: refreshToken
                })
            }
        )

        // Cache the new token for parallel requests
        setCachedToken(refreshToken, tokenResponse.access_token)

        // Neue Tokens setzen
        setCookie(event, "kc_access", tokenResponse.access_token, {
            httpOnly: true, secure: true, sameSite: "none", path: "/", maxAge: 60 * 5
        })

        setCookie(event, "kc_refresh", tokenResponse.refresh_token, {
            httpOnly: true, secure: true, sameSite: "none", path: "/", maxAge: 60 * 60 * 24 * 30
        })
        event.context.accessToken = tokenResponse.access_token

    } catch (err) {
        console.error("Token refresh failed", err)

        // Check if another request already refreshed - use cached token
        const cachedToken = getCachedToken(refreshToken)
        if (cachedToken) {
            event.context.accessToken = cachedToken
            return
        }

        deleteCookie(event, "kc_access")
        deleteCookie(event, "kc_refresh")

        // Redirect to login for page requests, let API calls fail with 401
        if (!event.node.req.originalUrl?.startsWith('/api')) {
            return sendRedirect(event, '/', 301)
        }
    }
}
