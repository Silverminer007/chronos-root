export default defineEventHandler(async (event): Promise<unknown> => {
    const config = useRuntimeConfig()
    const access = getCookie(event, 'kc_access')

    const path = event.context.params?.path
    const query = getQuery(event)
    const quarkusUrl = `${config.quarkusUrl}/api/v2/${path}`

    let body
    if (['POST', 'PUT', 'PATCH'].includes(event.req.method)) {
        body = await readBody(event)
    }

    return await $fetch<unknown>(quarkusUrl, {
        method: event.req.method,
        headers: {
            Authorization: `Bearer ${access}`
        },
        query,
        body
    })
})