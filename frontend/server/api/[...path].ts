import {getCookie} from 'h3'
import {$fetch} from 'ofetch'

export default defineEventHandler(async (event) => {
    const config = useRuntimeConfig()
    const access = getCookie(event, 'kc_access')

    const path = event.context.params.path
    const query = getQuery(event)
    const quarkusUrl = `${config.quarkusUrl}/api/v2/${path}`
    console.log("Proxing " + event.method + " request to " + quarkusUrl)

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