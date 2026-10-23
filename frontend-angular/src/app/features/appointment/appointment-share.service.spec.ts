import { TestBed } from '@angular/core/testing';
import { Appointment, UserParticipant } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentShareService } from './appointment-share.service';

const participant = (id: number, status: UserParticipant['status']): UserParticipant => ({
  user_id: id,
  name: `Person ${id}`,
  profile_picture_url: '',
  role: 'ATTENDANT',
  status,
  via_group_id: null,
  via_group_name: null,
});

const appointment: Appointment = {
  id: 7,
  name: 'Zeltlager',
  description: 'Packliste beachten',
  start: '2026-10-21T18:00:00',
  end: '2026-10-21T21:00:00',
  venue: 'Waldwiese',
  status: 'PLANNED',
  minimal_attendees: 3,
  participants: [participant(1, 'APPROVED'), participant(2, 'APPROVED'), participant(3, 'REJECTED'), participant(4, 'PENDING')],
  group_participants: [],
  messages: [],
};

describe('AppointmentShareService', () => {
  let service: AppointmentShareService;
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
  const url = `${window.location.origin}/appointment/7`;

  beforeEach(() => {
    toast = { success: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({ providers: [{ provide: ToastService, useValue: toast }] });
    service = TestBed.inject(AppointmentShareService);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, 'share');
  });

  describe('buildShareText', () => {
    it('enthält Details, Rückmeldungen und Link', () => {
      expect(service.buildShareText(appointment, url)).toBe(
        [
          '*Zeltlager*',
          'Beschreibung: Packliste beachten',
          'Start: 21.10.2026 18:00 Uhr',
          'Ende: 21.10.2026 21:00 Uhr',
          'Ort: Waldwiese',
          '',
          '*Bisherige Rückmeldungen*:',
          'Zusagen: 2',
          'Absagen: 1',
          'Teilnehmer insgesamt: 4',
          'Mindest Teilnehmer: 3',
          '',
          `Termindetails & Rückmeldung geben: ${url}`,
        ].join('\n'),
      );
    });

    it('lässt leere Beschreibung, Ort und Mindestteilnehmerzahl weg', () => {
      const text = service.buildShareText({ ...appointment, description: null, venue: null, minimal_attendees: 0 }, url);
      expect(text).not.toContain('Beschreibung');
      expect(text).not.toContain('Ort:');
      expect(text).not.toContain('Mindest');
    });
  });

  describe('share', () => {
    it('nutzt die Web-Share-API, wenn vorhanden', async () => {
      const share = vi.fn().mockResolvedValue(undefined);
      Object.defineProperty(navigator, 'share', { value: share, configurable: true });

      await service.share(appointment);

      expect(share).toHaveBeenCalledWith({ title: 'Zeltlager', text: service.buildShareText(appointment, url) });
      expect(toast.error).not.toHaveBeenCalled();
    });

    it('meldet einen Fehler der Web-Share-API, außer beim Abbrechen', async () => {
      const share = vi.fn().mockRejectedValue(new DOMException('x', 'AbortError'));
      Object.defineProperty(navigator, 'share', { value: share, configurable: true });
      await service.share(appointment);
      expect(toast.error).not.toHaveBeenCalled();

      share.mockRejectedValue(new Error('kaputt'));
      await service.share(appointment);
      expect(toast.error).toHaveBeenCalledWith('Fehler', 'Teilen fehlgeschlagen');
    });

    it('kopiert ohne Web-Share-API in die Zwischenablage', async () => {
      const writeText = vi.fn().mockResolvedValue(undefined);
      vi.stubGlobal('navigator', { clipboard: { writeText } });

      await service.share(appointment);

      expect(writeText).toHaveBeenCalledWith(service.buildShareText(appointment, url));
      expect(toast.success).toHaveBeenCalledWith('Link kopiert', 'Der Termin-Link wurde in die Zwischenablage kopiert');
    });

    it('meldet, wenn das Kopieren scheitert', async () => {
      vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error('x')) } });

      await service.share(appointment);

      expect(toast.error).toHaveBeenCalledWith('Fehler', 'Link konnte nicht kopiert werden');
    });
  });
});
