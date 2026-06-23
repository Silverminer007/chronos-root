import {decodeJwt} from "#server/utils/decodeJwt";
import type {TokenResponse} from "#server/utils/types"

export default defineEventHandler(async (event) => {
    const config = useRuntimeConfig()
    const code = getQuery(event).code as string

    const tokenResponse = await $fetch(
        `${config.auth.issuer}/protocol/openid-connect/token`,
        {
            method: "POST",
            body: new URLSearchParams({
                grant_type: "authorization_code",
                client_id: config.auth.clientId,
                redirect_uri: config.auth.redirectUri,
                code,
            })
        }
    ) as TokenResponse;

    if (!tokenResponse || !tokenResponse.access_token || !tokenResponse.refresh_token) {
        return
    }

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
    console.log(`User logged in: ${access_token.iss}`)
    return sendRedirect(event, "/agenda")
})