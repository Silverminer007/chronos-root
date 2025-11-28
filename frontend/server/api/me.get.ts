import { getCookie } from 'h3'
import { $fetch } from 'ofetch'

export default defineEventHandler(async (event) => {
    const access = getCookie(event, "kc_access")
    if (!access) return { authenticated: false }

    const config = useRuntimeConfig()

    const userInfo = await $fetch(
        `${config.auth.issuer}/protocol/openid-connect/userinfo`,
        {
            headers: { Authorization: `Bearer ${access}` }
        }
    )

    return {
        authenticated: true,
        user: userInfo
    }
})