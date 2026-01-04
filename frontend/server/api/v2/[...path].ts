import {getCookie} from 'h3'
import {$fetch} from 'ofetch'

export default defineEventHandler(async (event) => {
    const config = useRuntimeConfig()
    const contextToken = event.context.accessToken
    const cookieToken = getCookie(event, 'kc_access')
    const access = contextToken || cookieToken

    const path = event.context.params.path
    const query = getQuery(event)
    const quarkusUrl = `${config.quarkusUrl}/api/v2/${path}`
    console.log(`Proxying ${event.method} to ${quarkusUrl} | token from: ${contextToken ? 'context' : cookieToken ? 'cookie' : 'NONE'}`)

    let body
    if (['POST', 'PUT', 'PATCH'].includes(event.method)) {
        body = await readBody(event)
    }

    return await $fetch(quarkusUrl, {
        method: event.method,
        headers: {
            Authorization: `Bearer ${access}`
        },
        query,
        body
    })
})