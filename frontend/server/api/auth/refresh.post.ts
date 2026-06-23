import {decodeJwt} from "#server/utils/decodeJwt"
import type {TokenResponse} from "#server/utils/types"

export default defineEventHandler(async (event) => {
    const refresh = getCookie(event, "kc_refresh")

    // Ohne Refresh Token können wir nichts tun
    if (!refresh) {
        deleteCookie(event, "kc_access")
        deleteCookie(event, "kc_refresh")
        throw createError({
            status: 401,
            statusText: "Please log in again",
        })
    }

    const config = useRuntimeConfig()

    let tokenResponse: TokenResponse | undefined;
    try {
        tokenResponse = await $fetch(
            `${config.auth.issuer}/protocol/openid-connect/token`,
            {
                method: "POST",
                body: new URLSearchParams({
                    grant_type: "refresh_token",
                    client_id: config.auth.clientId,
                    refresh_token: refresh
                })
            }
        );
    } catch {
        throw createError({
            status: 502,
            statusText: "Keycloak returned invalid token response",
        })
    }
    if (!tokenResponse || !tokenResponse.access_token || !tokenResponse.refresh_token) {
        throw createError({
            status: 502,
            statusText: "Keycloak returned invalid token response",
        })
    }
    try {
        const access_token = decodeJwt(tokenResponse.access_token);
        const refresh_token = decodeJwt(tokenResponse.refresh_token);

        setCookie(event, "kc_access", tokenResponse.access_token, {
            httpOnly: true,
            secure: true,
            sameSite: "none",
            path: "/",
            expires: new Date(access_token.exp * 1000)
        })

        setCookie(event, "kc_refresh", tokenResponse.refresh_token, {
            httpOnly: true,
            secure: true,
            sameSite: "none",
            path: "/",
            expires: new Date(refresh_token.exp * 1000)
        })

        setCookie(event, "kc_expires", access_token.exp, {
            httpOnly: false,
            secure: true,
            sameSite: "none",
            path: "/",
            expires: new Date(refresh_token.exp * 1000)
        })
        console.log(`User refreshed token: ${access_token.iss}`)
    } catch (err) {
        deleteCookie(event, "kc_expires")
        deleteCookie(event, "kc_access")
        deleteCookie(event, "kc_refresh")
        console.error("Token refresh failed", err)
        throw createError({
            status: 401,
            statusText: "Please log in again",
        })
    }
})