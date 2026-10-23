import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Appointment } from '../../../core/models';
import { ToastService } from '../../../core/toast.service';
import { AppointmentDemoCardComponent } from './appointment-demo-card.component';
import { DEMO_OWN_USER_ID } from './demo-appointments';

const participant = (id: number, status: 'APPROVED' | 'REJECTED' | 'PENDING') => ({
  user_id: id,
  name: String.fromCharCode(64 + id),
  profile_picture_url: '',
  role: 'ATTENDANT' as const,
  status,
  via_group_id: null,
  via_group_name: null,
});

const appointment = (over: Partial<Appointment> = {}): Appointment => ({
  id: 0,
  name: 'Sommerfest',
  description: 'Sommerfest mit allen Mitgliedern',
  start: '2026-11-21T18:00:00',
  end: '2026-11-21T21:00:00',
  status: 'PLANNED',
  venue: '',
  minimal_attendees: 8,
  participants: [participant(DEMO_OWN_USER_ID, 'REJECTED'), ...[2, 3, 4, 5, 6, 7].map((i) => participant(i, 'APPROVED'))],
  group_participants: [],
  messages: [],
  ...over,
});

describe('AppointmentDemoCardComponent', () => {
  let ref: ComponentRef<AppointmentDemoCardComponent>;
  let el: HTMLElement;

  async function create(a: Appointment) {
    const fixture = TestBed.createComponent(AppointmentDemoCardComponent);
    ref = fixture.componentRef;
    ref.setInput('appointment', a);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
    return fixture;
  }

  const button = (label: string) =>
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(label)) as HTMLButtonElement;

  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }),
  );

  it('zeigt Name, Zeitraum, Beschreibung und Zusagen/Mindestzahl', async () => {
    await create(appointment());
    expect(el.querySelector('h3')?.textContent).toContain('Sommerfest');
    expect(el.textContent).toContain('Sa., 21.11.2026, 18:00 - 21:00 Uhr');
    expect(el.textContent).toContain('Sommerfest mit allen Mitgliedern');
    expect(el.querySelector('.attendance')?.textContent?.replace(/\s/g, '')).toBe('6/8Zusagen');
  });

  it('zeigt höchstens vier Avatare und „+n“ für den Rest', async () => {
    await create(appointment());
    expect(el.querySelectorAll('.avatar:not(.avatar-more)').length).toBe(4);
    expect(el.querySelector('.avatar-more')?.textContent?.trim()).toBe('+2');
  });

  it('blendet je Status das Banner ein', async () => {
    await create(appointment({ status: 'CANCELLED' }));
    expect(el.querySelector('.status-banner')?.textContent).toContain('Termin abgesagt');
    await create(appointment({ status: 'NOT_ENOUGH_ATTENDEES' }));
    expect(el.querySelector('.status-banner')?.textContent).toContain('Zu wenig Teilnehmende');
    await create(appointment());
    expect(el.querySelector('.status-banner')).toBeNull();
  });

  it('zeigt Ort nur, wenn gesetzt', async () => {
    await create(appointment({ venue: 'Gemeindehaus' }));
    expect(el.textContent).toContain('Gemeindehaus');
  });

  it('ändert den eigenen Status in der Demo auch anonym ohne Anmeldung', async () => {
    const fixture = await create(appointment());
    expect(button('Absagen').disabled).toBe(true);
    expect(button('Zusagen').disabled).toBe(false);
    button('Zusagen').click();
    await fixture.whenStable();
    expect(button('Zusagen').disabled).toBe(true);
    expect(button('Absagen').disabled).toBe(false);
    expect(el.querySelector('.attendance')?.textContent?.replace(/\s/g, '')).toBe('7/8Zusagen');
  });

  it('ändert die Teilnehmer mit echten ids nicht', async () => {
    const fixture = await create(appointment());
    button('Zusagen').click();
    await fixture.whenStable();
    expect(el.querySelector('.attendance')?.textContent?.replace(/\s/g, '')).toBe('7/8Zusagen');
  });

  it('öffnet den Nachrichtendialog und meldet den Versand per Toast', async () => {
    const fixture = await create(appointment());
    const info = vi.spyOn(TestBed.inject(ToastService), 'info');
    expect(el.querySelector('app-message-dialog .modal')).toBeNull();
    button('Nachricht').click();
    await fixture.whenStable();
    const dialog = el.querySelector('app-message-dialog .modal');
    expect(dialog?.textContent).toContain('Sommerfest 21.11.2026');
    expect(dialog?.textContent).toContain('7 Personen');
    const textarea = el.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Hallo';
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    (el.querySelector('app-message-dialog .btn-gradient') as HTMLButtonElement).click();
    await vi.waitFor(() => expect(info).toHaveBeenCalledWith('Die Nachricht wurde versendet'), { timeout: 2000 });
    await fixture.whenStable();
    expect(el.querySelector('app-message-dialog .modal')).toBeNull();
  });
});
