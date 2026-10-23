import {
  DURATION_CHIPS,
  buildAppointmentForm,
  defaultStart,
  durationLabel,
  effectiveDuration,
  patchFormFromAppointment,
  toDatetimeLocal,
  toPayload,
} from './appointment-form';

const appointment = (start: string, end: string, extra = {}) => ({
  name: 'Zeltlager',
  description: 'Packliste',
  venue: 'Wiese',
  minimal_attendees: 4,
  start,
  end,
  ...extra,
});

describe('appointment-form', () => {
  it('formatiert Datum für datetime-local in Ortszeit', () => {
    expect(toDatetimeLocal(new Date(2026, 0, 5, 7, 3))).toBe('2026-01-05T07:03');
  });

  it('startet standardmäßig zur nächsten vollen Stunde', () => {
    expect(defaultStart(new Date(2026, 9, 21, 17, 42, 10))).toBe('2026-10-21T18:00');
  });

  describe('Validierung', () => {
    it('verlangt einen nicht leeren Namen', () => {
      const form = buildAppointmentForm();
      expect(form.controls.name.valid).toBe(false);
      form.controls.name.setValue('   ');
      expect(form.controls.name.valid).toBe(false);
      form.controls.name.setValue('Sommerfest');
      expect(form.controls.name.valid).toBe(true);
    });

    it('verlangt einen Startzeitpunkt', () => {
      const form = buildAppointmentForm();
      form.controls.start.setValue('');
      expect(form.controls.start.valid).toBe(false);
    });

    it('verlangt eine eigene Dauer größer 0', () => {
      const form = buildAppointmentForm();
      form.patchValue({ customDuration: true, durationMinutes: null, customValue: 0 });
      expect(form.hasError('duration')).toBe(true);
      form.patchValue({ customValue: 2, customUnit: 'hours' });
      expect(form.hasError('duration')).toBe(false);
    });

    it('verlangt eine gewählte Dauer, wenn keine eigene aktiv ist', () => {
      const form = buildAppointmentForm();
      form.patchValue({ durationMinutes: null });
      expect(form.hasError('duration')).toBe(true);
    });
  });

  describe('effectiveDuration', () => {
    it('nutzt den gewählten Chip', () => {
      expect(
        effectiveDuration({ ...buildAppointmentForm().getRawValue(), durationMinutes: 90 }),
      ).toBe(90);
    });

    it.each([
      ['minutes', 45, 45],
      ['hours', 2, 120],
      ['days', 3, 4320],
    ] as const)('rechnet eigene Dauer in %s um', (customUnit, customValue, expected) => {
      const v = {
        ...buildAppointmentForm().getRawValue(),
        customDuration: true,
        durationMinutes: null,
        customUnit,
        customValue,
      };
      expect(effectiveDuration(v)).toBe(expected);
    });

    it('liefert null bei ungültiger eigener Dauer', () => {
      const v = { ...buildAppointmentForm().getRawValue(), customDuration: true, customValue: 0 };
      expect(effectiveDuration(v)).toBeNull();
    });
  });

  describe('durationLabel', () => {
    it('nutzt die Chip-Beschriftung', () =>
      expect(durationLabel(DURATION_CHIPS[1].minutes)).toBe('1 Std'));
    it('zeigt Tage, Stunden, Minuten', () => {
      expect(durationLabel(4320)).toBe('3 Tage');
      expect(durationLabel(180)).toBe('3 Std');
      expect(durationLabel(45)).toBe('45 Min');
    });
  });

  describe('toPayload', () => {
    it('erzeugt ISO-Zeitpunkte, trimmt Texte und setzt leere Texte auf null', () => {
      const form = buildAppointmentForm();
      form.patchValue({
        name: '  Fest ',
        start: '2026-10-21T18:00',
        durationMinutes: 60,
        description: ' ',
        venue: ' Halle ',
      });
      expect(toPayload(form.getRawValue())).toEqual({
        name: 'Fest',
        description: null,
        venue: 'Halle',
        start: new Date('2026-10-21T18:00').toISOString(),
        end: new Date('2026-10-21T19:00').toISOString(),
        minimal_attendees: 0,
      });
    });

    it('übernimmt die Mindestteilnehmerzahl nur, wenn aktiviert', () => {
      const form = buildAppointmentForm();
      form.patchValue({ name: 'A', hasAttendees: true, minimalAttendees: 7 });
      expect(toPayload(form.getRawValue())?.minimal_attendees).toBe(7);
    });

    it('liefert null, wenn das Formular unvollständig ist', () => {
      const form = buildAppointmentForm();
      form.patchValue({ name: 'A', start: '' });
      expect(toPayload(form.getRawValue())).toBeNull();
    });
  });

  describe('patchFormFromAppointment', () => {
    it('wählt den passenden Chip', () => {
      const form = buildAppointmentForm();
      patchFormFromAppointment(form, appointment('2026-10-21T18:00', '2026-10-21T19:30'));
      expect(form.getRawValue()).toMatchObject({
        name: 'Zeltlager',
        description: 'Packliste',
        venue: 'Wiese',
        start: '2026-10-21T18:00',
        durationMinutes: 90,
        customDuration: false,
        hasAttendees: true,
        minimalAttendees: 4,
      });
    });

    it.each([
      ['2026-10-21T18:00', '2026-10-24T18:00', 'days', 3],
      ['2026-10-21T18:00', '2026-10-21T23:00', 'hours', 5],
      ['2026-10-21T18:00', '2026-10-21T18:45', 'minutes', 45],
    ])('übernimmt abweichende Dauer %s – %s als eigene Dauer', (start, end, unit, value) => {
      const form = buildAppointmentForm();
      patchFormFromAppointment(form, appointment(start, end));
      expect(form.getRawValue()).toMatchObject({
        customDuration: true,
        durationMinutes: null,
        customUnit: unit,
        customValue: value,
      });
    });

    it('setzt ohne Mindestteilnehmer den Standardwert 5 und leere Texte', () => {
      const form = buildAppointmentForm();
      patchFormFromAppointment(
        form,
        appointment('2026-10-21T18:00', '2026-10-21T19:00', {
          minimal_attendees: 0,
          description: null,
          venue: null,
        }),
      );
      expect(form.getRawValue()).toMatchObject({
        hasAttendees: false,
        minimalAttendees: 5,
        description: '',
        venue: '',
      });
    });
  });
});
