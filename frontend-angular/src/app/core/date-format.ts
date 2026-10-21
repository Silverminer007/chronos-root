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
