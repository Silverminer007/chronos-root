/** Dekodiert den URL-sicheren Base64-VAPID-Schlüssel für `pushManager.subscribe` (Port von `utils/base64.ts`). */
export function base64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const raw = atob((base64String + padding).replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}
