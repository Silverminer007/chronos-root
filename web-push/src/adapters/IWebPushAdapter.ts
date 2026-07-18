export type SendResult =
  | { ok: true }
  | { ok: false; gone: boolean; error: Error }

export interface IWebPushAdapter {
  getPublicKey(): string
  send(endpoint: string, auth: string, p256dh: string, payload: string): Promise<SendResult>
}
