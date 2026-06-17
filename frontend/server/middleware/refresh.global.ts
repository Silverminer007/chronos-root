import {getCookie, setCookie, deleteCookie, type H3Event} from "h3"
import {decodeJwt} from "../utils/decodeJwt"
import {$fetch} from "ofetch"

// Deduplicates parallel refresh attempts for the same refresh token.
// When multiple SSR requests arrive simultaneously with the same (not yet rotated)
// refresh token, only the first triggers a Keycloak call — the rest await the same Promise.
// The entry is removed as soon as the refresh settles, so there is no stale state.
const inflightRefreshes = new Map<string, Promise<string | null>>()

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
            return sendRedirect(event, '/', 301)
        }
        return
    }

    const config = useRuntimeConfig()

    // Kein Access Token → direkt refreshen
    if (!access) {
        const newAccessToken = await refreshWithDedup(refresh, event, config)
        if (newAccessToken) {
            event.context.accessToken = newAccessToken
        }
        return
    }

    // Access Token parsen
    const jwt = decodeJwt(access)
    if (!jwt || !jwt.exp) {
        const newAccessToken = await refreshWithDedup(refresh, event, config)
        if (newAccessToken) {
            event.context.accessToken = newAccessToken
        }
        return
    }

    const now = Math.floor(Date.now() / 1000)
    const expiresIn = jwt.exp - now
    const threshold = 30

    // Token erneuern, wenn weniger als 30 Sekunden übrig sind
    if (expiresIn < threshold) {
        const newAccessToken = await refreshWithDedup(refresh, event, config)
        if (newAccessToken) {
            event.context.accessToken = newAccessToken
        }
        return
    }

    event.context.accessToken = access
})

// Shares a single in-flight Keycloak refresh call across parallel requests
// that arrive with the same refresh token. Returns the new access token,
// or null if the refresh failed (cookies are cleared inside doRefresh).
function refreshWithDedup(
    refreshToken: string,
    event: H3Event,
    config: ReturnType<typeof useRuntimeConfig>
): Promise<string | null> {
    const existing = inflightRefreshes.get(refreshToken)
    if (existing) {
        return existing
    }

    const promise = doRefresh(event, config, refreshToken).finally(() => {
        inflightRefreshes.delete(refreshToken)
    })

    inflightRefreshes.set(refreshToken, promise)
    return promise
}

async function doRefresh(
    event: H3Event,
    config: ReturnType<typeof useRuntimeConfig>,
    refreshToken: string
): Promise<string | null> {
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

        setCookie(event, "kc_access", tokenResponse.access_token, {
            httpOnly: true, secure: true, sameSite: "none", path: "/", maxAge: 60 * 5
        })
        setCookie(event, "kc_refresh", tokenResponse.refresh_token, {
            httpOnly: true, secure: true, sameSite: "none", path: "/", maxAge: 60 * 60 * 24 * 30
        })

        return tokenResponse.access_token

    } catch (err) {
        console.error("Token refresh failed", err)

        deleteCookie(event, "kc_access")
        deleteCookie(event, "kc_refresh")

        if (!event.node.req.originalUrl?.startsWith('/api')) {
            await sendRedirect(event, '/', 301)
        }

        return null
    }
}