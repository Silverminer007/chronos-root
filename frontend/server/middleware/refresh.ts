import { getCookie, setCookie } from "h3"
import { decodeJwt } from "../utils/decodeJwt"
import { $fetch } from "ofetch"

export default defineEventHandler(async (event) => {
    const access = getCookie(event, "kc_access")
    const refresh = getCookie(event, "kc_refresh")

    // Ohne Refresh Token können wir nichts tun
    if (!refresh) return

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
})

async function refreshTokens(event, config, refreshToken) {
    try {
        const tokenResponse = await $fetch(
            `${config.auth.issuer}/protocol/openid-connect/token`,
            {
                method: "POST",
                body: new URLSearchParams({
                    grant_type: "refresh_token",
                    client_id: config.auth.clientId,
                    client_secret: config.auth.clientSecret,
                    refresh_token: refreshToken
                })
            }
        )

        // Neue Tokens setzen
        setCookie(event, "kc_access", tokenResponse.access_token, {
            httpOnly: true, secure: true, sameSite: "strict", path: "/", maxAge: 60 * 5
        })

        setCookie(event, "kc_refresh", tokenResponse.refresh_token, {
            httpOnly: true, secure: true, sameSite: "strict", path: "/", maxAge: 60 * 60 * 24 * 30
        })

    } catch (err) {
        console.error("Token refresh failed", err)
        // Refresh fehlgeschlagen → Session endet
    }
}