import { demoAppointments } from './demo-appointments';

describe('demoAppointments', () => {
  const now = new Date(2026, 0, 31, 9, 0);
  const [summer, meeting, group] = demoAppointments(now);

  it('liefert die drei Demo-Termine der Landingpage wie in Figma', () => {
    expect([summer.name, meeting.name, group.name]).toEqual([
      'Sommerfest',
      'Reflexionstreffen',
      'Gruppenstunde',
    ]);
    expect([summer.status, meeting.status, group.status]).toEqual([
      'PLANNED',
      'NOT_ENOUGH_ATTENDEES',
      'PLANNED',
    ]);
    expect([summer.venue, meeting.venue, group.venue]).toEqual(['Vereinsheim', null, 'Jugendraum']);
    expect([summer.description, meeting.description, group.description]).toEqual([
      'Sommerfest mit allen Mitgliedern',
      '',
      '',
    ]);
  });

  it('setzt die Uhrzeiten wie in Figma (14–22, 19–20, 18–19:30 Uhr)', () => {
    const range = (a: typeof summer) => [
      new Date(a.start).getHours() * 60 + new Date(a.start).getMinutes(),
      new Date(a.end).getHours() * 60 + new Date(a.end).getMinutes(),
    ];
    expect(range(summer)).toEqual([14 * 60, 22 * 60]);
    expect(range(meeting)).toEqual([19 * 60, 20 * 60]);
    expect(range(group)).toEqual([18 * 60, 19 * 60 + 30]);
  });

  it('zeigt bei Sommerfest 4 von 8 Zusagen', () => {
    const approved = (a: typeof summer) =>
      a.participants.filter((p) => p.status === 'APPROVED').length;
    expect(approved(summer)).toBe(4);
    expect(summer.minimal_attendees).toBe(8);
  });

  it('legt die Termine einen bzw. zwei Monate in die Zukunft (Monatsende wird begrenzt)', () => {
    expect(new Date(summer.start).getMonth()).toBe(1);
    expect(new Date(summer.start).getDate()).toBe(28);
    expect(new Date(meeting.start).getMonth()).toBe(2);
    expect(new Date(meeting.start).getDate()).toBe(31);
  });
});
