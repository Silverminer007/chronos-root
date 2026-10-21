/** Pfad des handgeschriebenen Service Workers (`public/push-sw.js`). */
export const SW_URL = '/push-sw.js';
export const SW_UPDATE_INTERVAL_MS = 60 * 60 * 1000;

interface ServiceWorkerLike {
  register(url: string): Promise<{ update(): unknown }>;
}

/** Registriert den Service Worker und prüft stündlich auf eine neue Version (Port von `register-sw.client.ts`). */
export async function registerServiceWorker(
  container: ServiceWorkerLike | undefined = typeof navigator === 'undefined'
    ? undefined
    : navigator.serviceWorker,
): Promise<void> {
  if (!container) return;
  try {
    const registration = await container.register(SW_URL);
    setInterval(() => void registration.update(), SW_UPDATE_INTERVAL_MS);
  } catch (err) {
    console.error('Service worker registration failed', err);
  }
}
