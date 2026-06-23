export default defineEventHandler((event) => {
    const config = useRuntimeConfig()

    const redirectUri = encodeURIComponent(config.auth.redirectUri + '?registered=true')
    const kcAuthUrl =
        `${config.auth.issuer}/protocol/openid-connect/registrations` +
        `?client_id=${config.auth.clientId}` +
        `&redirect_uri=${redirectUri}` +
        `&response_type=code` +
        `&scope=openid profile email`

    return sendRedirect(event, kcAuthUrl, 302)
})