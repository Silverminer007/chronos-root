import { Appointment, AppointmentStatus, ParticipationStatus, UserParticipant } from '../../../core/models';

const START_HOUR = 18;
const DURATION_HOURS = 3;

/** Wie Luxon `plus({ month })`: bei zu kurzem Zielmonat wird auf dessen letzten Tag begrenzt. */
function addMonths(date: Date, months: number): Date {
  const result = new Date(date.getFullYear(), date.getMonth() + months, 1, START_HOUR);
  const lastDay = new Date(result.getFullYear(), result.getMonth() + 1, 0).getDate();
  result.setDate(Math.min(date.getDate(), lastDay));
  return result;
}

/** Lokale ISO-Zeit ohne Zeitzone (`2026-11-21T18:00:00`), damit der Tag unabhängig von der Zeitzone stimmt. */
function localIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:00:00`;
}

function participants(statuses: ParticipationStatus[]): UserParticipant[] {
  return statuses.map((status, index) => ({
    user_id: index + 1,
    name: String.fromCharCode(65 + index),
    profile_picture_url: '',
    role: 'ATTENDANT',
    status,
    via_group_id: null,
    via_group_name: null,
  }));
}

const repeat = (status: ParticipationStatus, count: number): ParticipationStatus[] => Array(count).fill(status);

function demo(
  id: number,
  name: string,
  description: string,
  status: AppointmentStatus,
  minimalAttendees: number,
  months: number,
  now: Date,
  statuses: ParticipationStatus[],
): Appointment {
  const start = addMonths(now, months);
  const end = new Date(start.getTime() + DURATION_HOURS * 3600_000);
  return {
    id,
    name,
    description,
    start: localIso(start),
    end: localIso(end),
    status,
    venue: '',
    minimal_attendees: minimalAttendees,
    participants: participants(statuses),
    group_participants: [],
    messages: [],
  };
}

/** Die drei Demo-Termine der Landingpage (Port der `demoAppointment1..3` aus `pages/index.vue`). */
export function demoAppointments(now: Date): Appointment[] {
  return [
    demo(0, 'Sommerfest', 'Sommerfest mit allen Mitgliedern', 'PLANNED', 8, 1, now, [
      'REJECTED',
      ...repeat('APPROVED', 10),
    ]),
    demo(1, 'Team-Meeting', 'Wir müssen reflektieren, wie das Sommerfest gelaufen ist', 'CANCELLED', 5, 2, now, [
      'APPROVED',
      'APPROVED',
      'REJECTED',
      'APPROVED',
      'APPROVED',
    ]),
    demo(2, 'Gruppenstunde', '', 'NOT_ENOUGH_ATTENDEES', 2, 2, now, ['APPROVED', ...repeat('REJECTED', 4)]),
  ];
}
