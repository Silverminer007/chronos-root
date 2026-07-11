export function decodeJwt(token: string) {
    try {
        const payload = token.split('.')[1]
        if (!payload) return null
        const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
        return JSON.parse(json)
    } catch {
        return null
    }
}