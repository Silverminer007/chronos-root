export function decodeJwt(token: string) {
    try {
        const payload = token.split('.')[1]
        const json = Buffer.from(payload, 'base64').toString('utf8')
        return JSON.parse(json)
    } catch {
        return null
    }
}