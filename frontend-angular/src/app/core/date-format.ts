const LOCALE = 'de-DE';
const DATE = new Intl.DateTimeFormat(LOCALE, { day: '2-digit', month: '2-digit', year: 'numeric' });
const WEEKDAY_DATE = new Intl.DateTimeFormat(LOCALE, {
  weekday: 'short',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});
const TIME = new Intl.DateTimeFormat(LOCALE, { hour: '2-digit', minute: '2-digit' });

const dateTime = (date: Date) => `${WEEKDAY_DATE.format(date)}, ${TIME.format(date)}`;

/** `21.10.2026` (Port von `useDateFormatter.formatDate`). */
export function formatDate(iso: string): string {
  return DATE.format(new Date(iso));
}

/** `Mi., 21.10.2026, 18:00 - 21:00 Uhr` bzw. bei mehreren Tagen beide Zeitpunkte (Port von `formatTimeRange`). */
export function formatTimeRange(start: string, end: string): string {
  const from = new Date(start);
  const to = new Date(end);
  if (DATE.format(from) === DATE.format(to)) {
    return `${dateTime(from)} - ${TIME.format(to)} Uhr`;
  }
  return `${dateTime(from)} Uhr - ${dateTime(to)} Uhr`;
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
