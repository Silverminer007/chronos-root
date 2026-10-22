import { formatDate, formatDateTime, formatRelativeTime, formatTime, formatTimeRange } from './date-format';

describe('date-format', () => {
  it('formatiert ein Datum deutsch', () => {
    expect(formatDate('2026-10-21T18:00:00')).toBe('21.10.2026');
  });

  it('formatiert einen Zeitraum am selben Tag mit Endzeit und „Uhr“', () => {
    expect(formatTimeRange('2026-10-21T18:00:00', '2026-10-21T21:00:00')).toBe('Mi., 21.10.2026, 18:00 - 21:00 Uhr');
  });

  it('formatiert einen Zeitraum über mehrere Tage mit beiden Zeitpunkten', () => {
    expect(formatTimeRange('2026-10-21T18:00:00', '2026-10-22T10:00:00')).toBe(
      'Mi., 21.10.2026, 18:00 Uhr - Do., 22.10.2026, 10:00 Uhr',
    );
  });

  it('formatiert eine Uhrzeit mit „Uhr“', () => {
    expect(formatTime('2026-10-21T18:05:00')).toBe('18:05 Uhr');
  });

  it('formatiert Datum und Uhrzeit mit „Uhr“', () => {
    expect(formatDateTime('2026-10-21T18:05:00')).toBe('21.10.2026 18:05 Uhr');
  });
});

describe('formatRelativeTime', () => {
  const now = new Date('2026-10-21T12:00:00Z');

  it.each([
    ['2026-10-21T11:58:00Z', 'vor 2 Minuten'],
    ['2026-10-21T09:00:00Z', 'vor 3 Stunden'],
    ['2026-10-19T12:00:00Z', 'vor 2 Tagen'],
    ['2026-10-21T12:30:00Z', 'in 30 Minuten'],
  ])('%s → %s', (iso, expected) => {
    expect(formatRelativeTime(iso, now)).toBe(expected);
  });

  it('liefert bei ungültigem Datum einen leeren Text', () => {
    expect(formatRelativeTime('kaputt', now)).toBe('');
  });
});
