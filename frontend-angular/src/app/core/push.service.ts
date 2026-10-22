import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { base64ToUint8Array } from './base64';

const SHOWN_AT_STORAGE_KEY = 'pushPromptShownAt';
const ANSWER_STORAGE_KEY = 'pushPromptAnswer';
const ASK_AGAIN_AFTER_MS = 7 * 24 * 60 * 60 * 1000;

export type PushAnswer = 'granted' | 'denied' | 'dismissed';

/** Web-Push-Abo und Berechtigungs-Abfrage (Port von `usePush`). */
@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly http = inject(HttpClient);

  /** Browser-Berechtigung; wird bei jeder Abfrage über `requestPermission` aktualisiert. */
  readonly permission = signal<NotificationPermission>(
    this.isPushAvailable() ? Notification.permission : 'default',
  );

  isPushAvailable(): boolean {
    return (
      typeof navigator !== 'undefined' &&
      'serviceWorker' in navigator &&
      'PushManager' in window &&
      typeof Notification !== 'undefined'
    );
  }

  /** Aktiv, wenn der Browser es erlaubt und der Nutzer es in Chronos zugesagt hat. */
  isPushEnabled(): boolean {
    return (
      this.isPushAvailable() &&
      Notification.permission === 'granted' &&
      localStorage.getItem(ANSWER_STORAGE_KEY) === 'granted'
    );
  }

  async requestPermission(): Promise<NotificationPermission> {
    if (!this.isPushAvailable()) return 'denied';
    const result = await Notification.requestPermission();
    this.permission.set(result);
    return result;
  }

  async subscribe(): Promise<PushSubscription | null> {
    if (!this.isPushAvailable()) {
      console.warn('Push not supported');
      return null;
    }
    const registration = await navigator.serviceWorker.ready;
    const vapidPublicKey = await firstValueFrom(
      this.http.get('/api/v2/push/public-key', { responseType: 'text' }),
    );
    const subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: base64ToUint8Array(vapidPublicKey),
    });
    const keys = subscription.toJSON().keys;
    await firstValueFrom(
      this.http.post('/api/v2/push/subscribe', {
        endpoint: subscription.endpoint,
        keys: { p256dh: keys?.['p256dh'], auth: keys?.['auth'] },
      }),
    );
    return subscription;
  }

  /**
   * Ob der Dialog zur Push-Berechtigung gezeigt werden soll. Hat der Nutzer zugesagt, wird nebenbei
   * das Abo geprüft und bei Bedarf neu angelegt.
   */
  async shouldAsk(): Promise<boolean> {
    if (!this.isPushAvailable()) return false;
    if (Notification.permission === 'denied') return false;

    const answer = localStorage.getItem(ANSWER_STORAGE_KEY);
    if (answer === 'denied') return false;

    if (answer === 'granted') {
      if (Notification.permission !== 'granted') return true;
      await this.ensureSubscription();
      return false;
    }

    const lastAsked = localStorage.getItem(SHOWN_AT_STORAGE_KEY);
    return !lastAsked || Date.now() - Number(lastAsked) > ASK_AGAIN_AFTER_MS;
  }

  markAsked(answer: PushAnswer): void {
    localStorage.setItem(SHOWN_AT_STORAGE_KEY, Date.now().toString());
    const saved = localStorage.getItem(ANSWER_STORAGE_KEY);
    if (!saved || saved === 'dismissed') {
      localStorage.setItem(ANSWER_STORAGE_KEY, answer);
    }
  }

  private async ensureSubscription(): Promise<void> {
    const registration = await navigator.serviceWorker.ready;
    const existing = await registration.pushManager.getSubscription();
    if (!existing) {
      await this.subscribe();
      return;
    }
    const { exists } = await firstValueFrom(
      this.http.get<{ exists: boolean }>('/api/v2/push/status', { params: { endpoint: existing.endpoint } }),
    );
    if (!exists) {
      console.warn('Server lost push subscription, reconnecting');
      await existing.unsubscribe();
      await this.subscribe();
    }
  }
}
