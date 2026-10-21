import { Appointment, UserParticipant } from '../../core/models';

/** Gemeinsame Testdaten der Termindetail-Specs. */
export const participant = (id: number, overrides: Partial<UserParticipant> = {}): UserParticipant => ({
  user_id: id,
  name: `Person ${id}`,
  profile_picture_url: '',
  role: 'ATTENDANT',
  status: 'PENDING',
  via_group_id: null,
  via_group_name: null,
  ...overrides,
});

export const appointment = (overrides: Partial<Appointment> = {}): Appointment => ({
  id: 5,
  name: 'Zeltlager',
  description: 'Packliste beachten',
  start: '2026-10-21T18:00:00',
  end: '2026-10-21T21:00:00',
  venue: 'Waldwiese',
  status: 'PLANNED',
  minimal_attendees: 3,
  participants: [],
  group_participants: [],
  messages: [],
  ...overrides,
});

export const click = (el: Element | null | undefined) => (el as HTMLElement).click();

export const buttonByText = (root: HTMLElement, text: string) =>
  Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes(text)) as HTMLButtonElement | undefined;
