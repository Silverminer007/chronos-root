export default defineEventHandler(async (event) => {
    const config = useRuntimeConfig()

    const refreshToken = getCookie(event, 'kc_refresh')

    // Falls kein Refresh Token existiert, nur Cookies löschen
    if (refreshToken) {
        try {
            await $fetch(
                `${config.auth.issuer}/protocol/openid-connect/logout`,
                {
                    method: 'POST',
                    body: new URLSearchParams({
                        client_id: config.auth.clientId,
                        client_secret: config.auth.clientSecret,
                        refresh_token: refreshToken
                    }),
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    }
                }
            )
        } catch (err) {
            console.error('Keycloak logout error:', err)
            // Fehler ignorieren – wir loggen lokal trotzdem aus
        }
    }

    // Lokale Cookies entfernen
    deleteCookie(event, 'kc_access')
    deleteCookie(event, 'kc_refresh')

    return { success: true }
})