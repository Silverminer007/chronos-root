import {
  formatAppointmentRange,
  formatDate,
  formatDateTime,
  formatRelativeTime,
  formatTime,
} from './date-format';

describe('date-format', () => {
  it('formatiert ein Datum deutsch', () => {
    expect(formatDate('2026-10-21T18:00:00')).toBe('21.10.2026');
  });

  it('formatiert einen Zeitraum am selben Tag wie in Figma (ohne Jahr und „Uhr“)', () => {
    expect(formatAppointmentRange('2025-07-12T14:00:00', '2025-07-12T22:00:00')).toBe(
      'Sa., 12. Juli · 14:00 – 22:00',
    );
  });

  it('formatiert einen Zeitraum über mehrere Tage mit beiden Zeitpunkten', () => {
    expect(formatAppointmentRange('2025-07-12T14:00:00', '2025-07-13T10:00:00')).toBe(
      'Sa., 12. Juli · 14:00 – So., 13. Juli · 10:00',
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
