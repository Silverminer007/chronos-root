import {
  Appointment,
  AppointmentStatus,
  ParticipationStatus,
  UserParticipant,
} from '../../../core/models';

/** Feste id des eigenen Demo-Teilnehmers; negativ, damit sie nie mit echten Benutzer-ids kollidiert. */
export const DEMO_OWN_USER_ID = -1;

/** Wie Luxon `plus({ month })`: bei zu kurzem Zielmonat wird auf dessen letzten Tag begrenzt. */
function addMonths(date: Date, months: number, hour: number): Date {
  const result = new Date(date.getFullYear(), date.getMonth() + months, 1, hour);
  const lastDay = new Date(result.getFullYear(), result.getMonth() + 1, 0).getDate();
  result.setDate(Math.min(date.getDate(), lastDay));
  return result;
}

/** Lokale ISO-Zeit ohne Zeitzone (`2026-11-21T18:00:00`), damit der Tag unabhängig von der Zeitzone stimmt. */
function localIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
}

function participants(statuses: ParticipationStatus[]): UserParticipant[] {
  return statuses.map((status, index) => ({
    user_id: index === 0 ? DEMO_OWN_USER_ID : index + 1,
    name: String.fromCharCode(65 + index),
    profile_picture_url: '',
    role: 'ATTENDANT',
    status,
    via_group_id: null,
    via_group_name: null,
  }));
}

const repeat = (status: ParticipationStatus, count: number): ParticipationStatus[] =>
  Array(count).fill(status);

interface DemoSpec {
  id: number;
  name: string;
  description: string;
  venue: string | null;
  status: AppointmentStatus;
  minimalAttendees: number;
  months: number;
  /** Beginn und Ende als `[Stunde, Minute]` am selben Tag. */
  from: [number, number];
  to: [number, number];
  statuses: ParticipationStatus[];
}

function demo(spec: DemoSpec, now: Date): Appointment {
  const start = addMonths(now, spec.months, spec.from[0]);
  start.setMinutes(spec.from[1]);
  const end = new Date(
    start.getFullYear(),
    start.getMonth(),
    start.getDate(),
    spec.to[0],
    spec.to[1],
  );
  return {
    id: spec.id,
    name: spec.name,
    description: spec.description,
    start: localIso(start),
    end: localIso(end),
    status: spec.status,
    venue: spec.venue,
    minimal_attendees: spec.minimalAttendees,
    participants: participants(spec.statuses),
    group_participants: [],
    messages: [],
  };
}

/** Die drei Demo-Termine der Landingpage nach Figma (Landing-Vorschau 20:49); Daten relativ zu `now`. */
export function demoAppointments(now: Date): Appointment[] {
  return [
    demo(
      {
        id: 0,
        name: 'Sommerfest',
        description: 'Sommerfest mit allen Mitgliedern',
        venue: 'Vereinsheim',
        status: 'PLANNED',
        minimalAttendees: 8,
        months: 1,
        from: [14, 0],
        to: [22, 0],
        statuses: ['REJECTED', ...repeat('APPROVED', 4)],
      },
      now,
    ),
    demo(
      {
        id: 1,
        name: 'Reflexionstreffen',
        description: '',
        venue: null,
        status: 'NOT_ENOUGH_ATTENDEES',
        minimalAttendees: 5,
        months: 2,
        from: [19, 0],
        to: [20, 0],
        statuses: ['APPROVED', 'APPROVED', 'REJECTED', 'REJECTED'],
      },
      now,
    ),
    demo(
      {
        id: 2,
        name: 'Gruppenstunde',
        description: '',
        venue: 'Jugendraum',
        status: 'PLANNED',
        minimalAttendees: 2,
        months: 2,
        from: [18, 0],
        to: [19, 30],
        statuses: ['APPROVED', ...repeat('REJECTED', 2)],
      },
      now,
    ),
  ];
}
