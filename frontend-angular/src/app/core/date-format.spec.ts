import { formatDate, formatTimeRange } from './date-format';

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
});
