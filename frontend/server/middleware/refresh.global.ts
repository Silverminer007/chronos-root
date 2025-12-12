import {getCookie, setCookie} from "h3"
import {decodeJwt} from "../utils/decodeJwt"
import {$fetch} from "ofetch"

export default defineEventHandler(async (event) => {
    const access = getCookie(event, "kc_access")
    const refresh = getCookie(event, "kc_refresh")

    // Ohne Refresh Token können wir nichts tun
    if (!refresh) {
        deleteCookie(event, "kc_access")
        deleteCookie(event, "kc_refresh")
        if (!event.node.req.originalUrl?.startsWith('/api') && event.node.req.originalUrl !== '/') {
            return sendRedirect(event, '/', 301);
        } else {
            return;
        }
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

    // 🧠 Token erneuern, wenn weniger als 30 Sekunden übrig sind
    const expiresIn = jwt.exp - now
    const threshold = 30

    if (expiresIn < threshold) {
        return await refreshTokens(event, config, refresh)
    }

    event.context.accessToken = access
})

async function refreshTokens(event, config, refreshToken) {
    try {
        console.log("Trying to refresh tokens")
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

        // Neue Tokens setzen
        setCookie(event, "kc_access", tokenResponse.access_token, {
            httpOnly: true, secure: true, sameSite: "none", path: "/", maxAge: 60 * 5
        })

        setCookie(event, "kc_refresh", tokenResponse.refresh_token, {
            httpOnly: true, secure: true, sameSite: "none", path: "/", maxAge: 60 * 60 * 24 * 30
        })
        event.context.accessToken = tokenResponse.access_token
        console.log("Token refresh succesful")

    } catch (err) {
        console.error("Token refresh failed", err)
        deleteCookie(event, "kc_access")
        deleteCookie(event, "kc_refresh")
    }
}