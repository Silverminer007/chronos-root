const LOCALE = 'de-DE';
const DATE = new Intl.DateTimeFormat(LOCALE, { day: '2-digit', month: '2-digit', year: 'numeric' });
const CARD_DATE = new Intl.DateTimeFormat(LOCALE, {
  weekday: 'short',
  day: 'numeric',
  month: 'long',
});
const TIME = new Intl.DateTimeFormat(LOCALE, { hour: '2-digit', minute: '2-digit' });

/** `21.10.2026` (Port von `useDateFormatter.formatDate`). */
export function formatDate(iso: string): string {
  return DATE.format(new Date(iso));
}

/**
 * `Sa., 12. Juli · 14:00 – 22:00` (Figma-Termin-Karte: Kurz-Wochentag, Tag + Monatsname, ohne Jahr und „Uhr“);
 * bei mehreren Tagen `Sa., 12. Juli · 14:00 – So., 13. Juli · 10:00`.
 */
export function formatAppointmentRange(start: string, end: string): string {
  const from = new Date(start);
  const to = new Date(end);
  const first = `${CARD_DATE.format(from)} · ${TIME.format(from)}`;
  if (DATE.format(from) === DATE.format(to)) {
    return `${first} – ${TIME.format(to)}`;
  }
  return `${first} – ${CARD_DATE.format(to)} · ${TIME.format(to)}`;
}

/** `18:00 Uhr` (Port von `useDateFormatter.formatTime`). */
export function formatTime(iso: string): string {
  return `${TIME.format(new Date(iso))} Uhr`;
}

/** `21.10.2026 18:00 Uhr` (Port von `useDateFormatter.formatDateTime`). */
export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return `${DATE.format(date)} ${TIME.format(date)} Uhr`;
}

const RELATIVE = new Intl.RelativeTimeFormat(LOCALE, { numeric: 'always' });
const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 86400],
  ['month', 30 * 86400],
  ['day', 86400],
  ['hour', 3600],
  ['minute', 60],
];

/** `vor 3 Stunden` (Port von Luxon `toRelative` mit Locale `de`); leer bei ungültigem Datum. */
export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const seconds = Math.trunc((new Date(iso).getTime() - now.getTime()) / 1000);
  if (Number.isNaN(seconds)) return '';
  const [unit, size] = RELATIVE_UNITS.find(([, s]) => Math.abs(seconds) >= s) ?? ['second', 1];
  const value = Math.trunc(seconds / size);
  return RELATIVE.format(value === 0 ? -0 : value, unit);
}
