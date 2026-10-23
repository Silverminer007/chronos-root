import { demoAppointments } from './demo-appointments';

describe('demoAppointments', () => {
  const now = new Date(2026, 0, 31, 9, 0);
  const [summer, meeting, group] = demoAppointments(now);

  it('liefert die drei Demo-Termine der Landingpage', () => {
    expect([summer.name, meeting.name, group.name]).toEqual(['Sommerfest', 'Team-Meeting', 'Gruppenstunde']);
    expect([summer.status, meeting.status, group.status]).toEqual(['PLANNED', 'CANCELLED', 'NOT_ENOUGH_ATTENDEES']);
    expect([summer.minimal_attendees, meeting.minimal_attendees, group.minimal_attendees]).toEqual([8, 5, 2]);
  });

  it('zählt die Zusagen wie im Nuxt-Frontend (10 von 11, 4 von 5, 1 von 5)', () => {
    const approved = (a: typeof summer) => a.participants.filter((p) => p.status === 'APPROVED').length;
    expect([approved(summer), approved(meeting), approved(group)]).toEqual([10, 4, 1]);
    expect([summer.participants.length, meeting.participants.length, group.participants.length]).toEqual([11, 5, 5]);
  });

  it('legt die Termine einen bzw. zwei Monate in die Zukunft (Monatsende wird begrenzt)', () => {
    expect(new Date(summer.start).getMonth()).toBe(1);
    expect(new Date(summer.start).getDate()).toBe(28);
    expect(new Date(meeting.start).getMonth()).toBe(2);
    expect(new Date(meeting.start).getDate()).toBe(31);
  });

  it('endet am selben Tag drei Stunden nach dem Beginn', () => {
    expect(new Date(summer.end).getTime() - new Date(summer.start).getTime()).toBe(3 * 3600_000);
  });
});
