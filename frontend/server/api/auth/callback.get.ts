import { setCookie } from 'h3'
import { $fetch } from 'ofetch'

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
    )

    setCookie(event, "kc_access", tokenResponse.access_token, {
        httpOnly: true,
        secure: true,
        sameSite: "lax",
        path: "/",
        maxAge: 60 * 5 // 5 Minuten reichen für Access Tokens
    })

    setCookie(event, "kc_refresh", tokenResponse.refresh_token, {
        httpOnly: true,
        secure: true,
        sameSite: "lax",
        path: "/",
        maxAge: 60 * 60 * 24 * 30 // Maximal alle 30 Tage neu anmelden
    })

    return sendRedirect(event, "/")
})