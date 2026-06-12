export function getErrorMessage(err: unknown, fallback: string): string {
    if (err && typeof err === 'object') {
        const {data, message} = err as { data?: { message?: string }; message?: string }
        return data?.message || message || fallback
    }
    return fallback
}

export function getErrorStatus(err: unknown): number | undefined {
    if (err && typeof err === 'object') {
        const {status, statusCode, response} = err as {
            status?: number
            statusCode?: number
            response?: { status?: number }
        }
        return status ?? statusCode ?? response?.status
    }
    return undefined
}