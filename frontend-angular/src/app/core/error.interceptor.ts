import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from './toast.service';

const SESSION_CHECK_URL = '/api/auth/isLoggedIn';
const HTTP_FORBIDDEN = 403;
const HTTP_SERVER_ERROR = 500;

/**
 * Opt-in für den globalen Fehler-Toast. Standard ist `false`: Wie im Nuxt-Frontend zeigen die Aufrufer Fehler selbst per
 * `getErrorMessage` an, ein globaler Toast würde doppelt erscheinen und die spezifische Backend-Meldung überdecken.
 * Nur Aufrufe ohne eigene Fehlerbehandlung setzen `context: new HttpContext().set(GLOBAL_ERROR_TOAST, true)`.
 */
export const GLOBAL_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

function messageFor(status: number): string | undefined {
  if (status === 0) return 'Keine Verbindung zum Server. Bitte prüfe deine Internetverbindung.';
  if (status === HTTP_FORBIDDEN) return 'Dazu hast du keine Berechtigung.';
  if (status >= HTTP_SERVER_ERROR) return 'Der Server ist derzeit nicht erreichbar oder hat einen Fehler gemeldet.';
  return undefined;
}

/**
 * Zeigt für übergreifende Fehler (Verbindung, 403, 5xx) nur auf Anforderung (`GLOBAL_ERROR_TOAST`) einen deutschen Toast. Der Fehler wird unverändert
 * weitergereicht, damit Stores/Komponenten 4xx selbst per `getErrorMessage` behandeln können.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && req.context.get(GLOBAL_ERROR_TOAST) && !req.url.startsWith(SESSION_CHECK_URL)) {
        const detail = messageFor(err.status);
        if (detail) toast.error('Fehler', detail);
      }
      return throwError(() => err);
    }),
  );
};
