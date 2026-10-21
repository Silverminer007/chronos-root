export type BrowserType = 'chrome' | 'safari' | 'firefox' | 'unknown';

const DEFAULT_ORDER: BrowserType[] = ['chrome', 'safari', 'firefox'];

/** Port von `detectBrowser` aus `install.vue`: Safari zuerst (aber nicht Chrome auf iOS), dann Firefox, dann Chromium. */
export function detectBrowser(userAgent: string): BrowserType {
  const ua = userAgent.toLowerCase();
  if (ua.includes('safari') && !ua.includes('chrome') && !ua.includes('chromium')) return 'safari';
  if (ua.includes('firefox')) return 'firefox';
  if (ua.includes('chrome') || ua.includes('chromium') || ua.includes('edg')) return 'chrome';
  return 'unknown';
}

/** Der erkannte Browser steht zuerst, die übrigen behalten ihre Reihenfolge. */
export function browserOrder(detected: BrowserType): BrowserType[] {
  return detected === 'unknown' ? [...DEFAULT_ORDER] : [detected, ...DEFAULT_ORDER.filter((b) => b !== detected)];
}
