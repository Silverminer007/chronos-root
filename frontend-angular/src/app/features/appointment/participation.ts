import { Appointment, ParticipationStatus, Role, UserParticipant } from '../../core/models';

/** Rollen, die Organisierende vergeben können (Reihenfolge wie im Rollenmenü und in den Dialogen). */
export const ASSIGNABLE_ROLES: Exclude<Role, 'NONE'>[] = ['RESPONSIBLE', 'ATTENDANT', 'HELPER', 'GUEST'];

const ROLE_LABELS: Record<Role, string> = {
  RESPONSIBLE: 'Organisator',
  ATTENDANT: 'Teilnehmer',
  HELPER: 'Helfer',
  GUEST: 'Gast',
  NONE: '',
};

const STATUS_LABELS: Record<ParticipationStatus, string> = {
  APPROVED: 'Zugesagt',
  REJECTED: 'Abgesagt',
  PENDING: 'Ausstehend',
};

const ROLE_ORDER: Record<Role, number> = { RESPONSIBLE: 0, ATTENDANT: 1, HELPER: 2, GUEST: 3, NONE: 4 };

const STATUS_ORDER: Record<ParticipationStatus, number> = { APPROVED: 0, REJECTED: 1, PENDING: 2 };

export const roleLabel = (role: Role): string => ROLE_LABELS[role] ?? role;

export const statusLabel = (status: ParticipationStatus): string => STATUS_LABELS[status] ?? status;

export function isResponsible(appointment: Appointment, userId: number | undefined): boolean {
  return userId !== undefined && appointment.participants.some((p) => p.user_id === userId && p.role === 'RESPONSIBLE');
}

/** Zugesagt vor abgesagt vor ausstehend, innerhalb eines Status alphabetisch. */
export function sortByStatusThenName(participants: UserParticipant[]): UserParticipant[] {
  return [...participants].sort(compareStatusThenName);
}

/** Organisator, Teilnehmer, Helfer, Gast; innerhalb einer Rolle wie `sortByStatusThenName`. */
export function sortByRoleThenStatusThenName(participants: UserParticipant[]): UserParticipant[] {
  return [...participants].sort(
    (a, b) => (ROLE_ORDER[a.role] ?? ROLE_ORDER.NONE) - (ROLE_ORDER[b.role] ?? ROLE_ORDER.NONE) || compareStatusThenName(a, b),
  );
}

const compareStatusThenName = (a: UserParticipant, b: UserParticipant) =>
  (STATUS_ORDER[a.status] ?? 2) - (STATUS_ORDER[b.status] ?? 2) || a.name.localeCompare(b.name);
