import { HttpErrorResponse } from '@angular/common/http';
import { getErrorMessage, getErrorStatus } from './errors';

describe('errors', () => {
  describe('getErrorMessage', () => {
    it('bevorzugt die Backend-Meldung aus dem Antwort-Body', () => {
      const err = new HttpErrorResponse({ status: 400, error: { message: 'Name fehlt' } });
      expect(getErrorMessage(err, 'Fallback')).toBe('Name fehlt');
    });

    it('nutzt data.message wie beim Nuxt-Fehlerobjekt', () => {
      expect(getErrorMessage({ data: { message: 'Daten' }, message: 'Allgemein' }, 'Fallback')).toBe('Daten');
    });

    it('nutzt bei HttpErrorResponse ohne JSON-Body den Fallback statt der englischen Angular-Meldung', () => {
      expect(getErrorMessage(new HttpErrorResponse({ status: 500, error: 'text' }), 'Fallback')).toBe('Fallback');
      expect(getErrorMessage(new HttpErrorResponse({ status: 500 }), 'Fallback')).toBe('Fallback');
    });

    it('nutzt message eines normalen Fehlers', () => {
      expect(getErrorMessage(new Error('Kaputt'), 'Fallback')).toBe('Kaputt');
    });

    it('nutzt den Fallback ohne Meldung oder bei Nicht-Objekten', () => {
      expect(getErrorMessage({}, 'Fallback')).toBe('Fallback');
      expect(getErrorMessage(null, 'Fallback')).toBe('Fallback');
      expect(getErrorMessage('text', 'Fallback')).toBe('Fallback');
    });
  });

  describe('getErrorStatus', () => {
    it('liest status, statusCode oder response.status', () => {
      expect(getErrorStatus({ status: 404 })).toBe(404);
      expect(getErrorStatus({ statusCode: 500 })).toBe(500);
      expect(getErrorStatus({ response: { status: 403 } })).toBe(403);
    });

    it('liefert undefined ohne Status', () => {
      expect(getErrorStatus({})).toBeUndefined();
      expect(getErrorStatus(undefined)).toBeUndefined();
    });
  });
});
