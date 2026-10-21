/** Liefert die Backend-/Fehlermeldung oder den deutschen Fallback (Port von `utils/errors.ts`). */
export function getErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object') {
    const { data, error, message } = err as {
      data?: { message?: string };
      error?: { message?: string } | null;
      message?: string;
    };
    return data?.message || error?.message || message || fallback;
  }
  return fallback;
}

/** Liefert den HTTP-Status eines Fehlers, sofern vorhanden. */
export function getErrorStatus(err: unknown): number | undefined {
  if (err && typeof err === 'object') {
    const { status, statusCode, response } = err as {
      status?: number;
      statusCode?: number;
      response?: { status?: number };
    };
    return status ?? statusCode ?? response?.status;
  }
  return undefined;
}
