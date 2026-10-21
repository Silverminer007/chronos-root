import { TestBed } from '@angular/core/testing';
import { AppointmentDetailsCardComponent } from './appointment-details-card.component';
import { AppointmentShareService } from './appointment-share.service';
import { appointment, click } from './testing';

describe('AppointmentDetailsCardComponent', () => {
  const share = { share: vi.fn() };

  const render = (overrides = {}) => {
    TestBed.configureTestingModule({ providers: [{ provide: AppointmentShareService, useValue: share }] });
    const fixture = TestBed.createComponent(AppointmentDetailsCardComponent);
    fixture.componentRef.setInput('appointment', appointment(overrides));
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  };

  it('zeigt Name, Datum, Uhrzeit, Ort, Beschreibung und Mindestteilnehmerzahl', () => {
    const { el } = render();
    const text = el.textContent ?? '';
    expect(el.querySelector('h2')?.textContent).toContain('Zeltlager');
    expect(text).toContain('21.10.2026');
    expect(text).toContain('18:00 Uhr - 21:00 Uhr');
    expect(text).toContain('Waldwiese');
    expect(text).toContain('Packliste beachten');
    expect(text).toContain('Mindestens 3 Teilnehmer erforderlich');
  });

  it('lässt Ort und Beschreibung ohne Wert weg und zeigt bei geplanten Terminen keinen Status', () => {
    const { el } = render({ venue: null, description: null });
    expect(el.textContent).not.toContain('Beschreibung');
    expect(el.querySelector('.bi-geo-alt')).toBeNull();
    expect(el.querySelector('[data-testid="status"]')).toBeNull();
  });

  it.each([
    ['CANCELLED', 'Abgesagt'],
    ['NOT_ENOUGH_ATTENDEES', 'Zu wenig Teilnehmer'],
    ['DELETED', 'Gelöscht'],
  ] as const)('zeigt für %s den Status „%s“', (status, label) => {
    const { el } = render({ status });
    expect(el.querySelector('[data-testid="status"]')?.textContent?.trim()).toBe(label);
  });

  it('teilt den Termin über den Teilen-Button', () => {
    const { el } = render();
    click(el.querySelector('button[title="Link teilen"]'));
    expect(share.share).toHaveBeenCalledWith(expect.objectContaining({ id: 5 }));
  });

  it('zeigt keinen Bearbeiten-Button (kommt mit dem Ticket „Termin anlegen und bearbeiten“)', () => {
    const { el } = render();
    expect(el.querySelector('button[title="Termin bearbeiten"]')).toBeNull();
  });
});
