export default defineEventHandler((event) => {
    const exp = getCookie(event, 'kc_expires')
    if (!exp || parseInt(exp) * 1000 <= Date.now()) {
        throw createError({ status: 401 })
    }
})