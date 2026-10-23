import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth.service';
import { User } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { GroupParticipantsCardComponent } from './group-participants-card.component';
import { GroupStore } from '../groups/group.store';
import { appointment, buttonByText, click, participant } from './testing';

describe('GroupParticipantsCardComponent', () => {
  let store: { addGroupParticipant: ReturnType<typeof vi.fn> };
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
  const user = signal<User | null>(null);

  const render = (userId: number, groups = true) => {
    user.set({ id: userId, first_name: 'M', last_name: 'M', email: null });
    store = { addGroupParticipant: vi.fn().mockResolvedValue(undefined) };
    toast = { success: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AppointmentStore, useValue: store },
        { provide: AuthService, useValue: { user } },
        { provide: ToastService, useValue: toast },
        { provide: GroupStore, useValue: { searchGroups: vi.fn().mockResolvedValue([]) } },
      ],
    });
    const fixture = TestBed.createComponent(GroupParticipantsCardComponent);
    fixture.componentRef.setInput(
      'appointment',
      appointment({
        participants: [
          participant(1, { role: 'RESPONSIBLE', status: 'APPROVED' }),
          participant(2, { name: 'Zoe', status: 'PENDING', via_group_id: 10 }),
          participant(3, { name: 'Bea', status: 'APPROVED', via_group_id: 10 }),
          participant(4, { name: 'Anna', status: 'REJECTED', via_group_id: 11 }),
        ],
        group_participants: groups
          ? [
              { id: 10, name: 'Jugend', members: [] },
              { id: 11, name: 'Leiter', members: [] },
            ]
          : [],
      }),
    );
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  };

  it('zeigt Anzahl der Gruppen und Zusagen je Gruppe', () => {
    const { el } = render(2);
    expect(el.textContent).toContain('2 Gruppen eingeladen');
    const groups = Array.from(el.querySelectorAll('[data-testid="group"]'));
    expect(groups[0].textContent).toContain('1/2 zugesagt');
    expect(groups[1].textContent).toContain('0/1 zugesagt');
  });

  it('verwendet im Singular „Gruppe“', () => {
    const { fixture, el } = render(2);
    fixture.componentRef.setInput(
      'appointment',
      appointment({ group_participants: [{ id: 10, name: 'Jugend', members: [] }] }),
    );
    fixture.detectChanges();
    expect(el.textContent).toContain('1 Gruppe eingeladen');
  });

  it('klappt Mitglieder (zugesagt vor ausstehend) per Klick auf und zu', () => {
    const { fixture, el } = render(2);
    expect(el.querySelector('[data-testid="member"]')).toBeNull();
    click(el.querySelector('[data-testid="group-toggle"]'));
    fixture.detectChanges();
    const members = Array.from(el.querySelectorAll('[data-testid="member"] .small')).map((m) => m.textContent?.trim());
    expect(members).toEqual(['Bea', 'Zoe']);
    click(el.querySelector('[data-testid="group-toggle"]'));
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="member"]')).toBeNull();
  });

  it('zeigt ohne Gruppen den Leerzustand', () => {
    const { el } = render(2, false);
    expect(el.textContent).toContain('Keine Gruppen eingeladen');
    expect(el.textContent).toContain('Teilnehmer wurden einzeln hinzugefügt');
  });

  it('bietet „Gruppe hinzufügen“ nur Organisatoren an', () => {
    expect(render(2).el.querySelector('button[title="Gruppe hinzufügen"]')).toBeNull();
    TestBed.resetTestingModule();
    expect(render(1).el.querySelector('button[title="Gruppe hinzufügen"]')).not.toBeNull();
  });

  it('fügt über den Dialog eine Gruppe hinzu und schließt ihn', async () => {
    const { fixture, el } = render(1);
    click(el.querySelector('button[title="Gruppe hinzufügen"]'));
    fixture.detectChanges();
    expect(el.querySelector('app-add-group-dialog .modal')).not.toBeNull();

    const dialog = fixture.debugElement.query((d) => d.name === 'app-add-group-dialog');
    dialog.componentInstance.added.emit({ id: 12, name: 'Neu', role: 'ATTENDANT' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(store.addGroupParticipant).toHaveBeenCalledWith(5, 12, 'ATTENDANT');
    expect(toast.success).toHaveBeenCalledWith('Gruppe hinzugefügt', '"Neu" wurde zum Termin hinzugefügt');
    expect(el.querySelector('app-add-group-dialog .modal')).toBeNull();
  });

  it('meldet Fehler und lässt den Dialog offen', async () => {
    const { fixture, el } = render(1);
    store.addGroupParticipant.mockRejectedValue(new Error('x'));
    click(buttonByText(el, 'Gruppe hinzufügen') ?? el.querySelector('button[title="Gruppe hinzufügen"]'));
    fixture.detectChanges();
    const dialog = fixture.debugElement.query((d) => d.name === 'app-add-group-dialog');
    dialog.componentInstance.added.emit({ id: 12, name: 'Neu', role: 'ATTENDANT' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(toast.error).toHaveBeenCalledWith('Fehler', 'Gruppe konnte nicht hinzugefügt werden');
    expect(el.querySelector('app-add-group-dialog .modal')).not.toBeNull();
  });
});
