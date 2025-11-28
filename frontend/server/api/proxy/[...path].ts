import { getCookie } from 'h3'
import { $fetch } from 'ofetch'

export default defineEventHandler(async (event) => {
    const config = useRuntimeConfig()
    const access = getCookie(event, 'kc_access')

    const path = event.context.params.path
    const quarkusUrl = `${config.quarkusUrl}/${path}`

    return await $fetch(quarkusUrl, {
        method: event.method,
        headers: {
            Authorization: `Bearer ${access}`
        },
        body: await readBody(event)
    })
})