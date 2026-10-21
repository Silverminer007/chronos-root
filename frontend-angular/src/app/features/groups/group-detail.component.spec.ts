import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ModalService } from '../../core/modal.service';
import { Group, User } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { buttonByText, click } from '../appointment/testing';
import { GroupDetailComponent } from './group-detail.component';
import { GroupStore } from './group.store';

const user = (id: number): User => ({ id, first_name: `Vor${id}`, last_name: `Nach${id}`, email: null });
const group: Group = { id: 4, name: 'Leiterrunde', members: [] };

describe('GroupDetailComponent', () => {
  let fixture: ComponentFixture<GroupDetailComponent>;
  let http: HttpTestingController;
  let toast: ToastService;
  let store: GroupStore;
  const router = { navigateByUrl: vi.fn() };
  const modal = { confirm: vi.fn() };
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    router.navigateByUrl.mockReset();
    modal.confirm.mockReset().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ groupId: '4' })) } },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: { user: signal(user(1)) } },
        { provide: ModalService, useValue: modal },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    store = TestBed.inject(GroupStore);
    fixture = TestBed.createComponent(GroupDetailComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  /** Beantwortet die Ladeanfragen (Mitglieder, danach die unbekannte Gruppe). */
  const load = async (members: User[]) => {
    http.expectOne('/api/v2/groups/4/users').flush(members);
    await settle();
    http.expectOne('/api/v2/groups').flush([group]);
    await settle();
  };

  it('zeigt beim Laden „Gruppe wird geladen...“', () => {
    expect(el().textContent).toContain('Gruppe wird geladen...');
    http.match(() => true);
  });

  it('zeigt Name, Mitgliederzahl und Mitgliederkarten', async () => {
    await load([user(1), user(2)]);
    expect(el().querySelector('h1')?.textContent).toContain('Leiterrunde');
    expect(el().textContent).toContain('2 Mitglieder');
    expect(el().querySelectorAll('app-group-member-card')).toHaveLength(2);
  });

  it('erlaubt das Entfernen nur anderer Mitglieder bei mehr als einem Mitglied', async () => {
    await load([user(1), user(2)]);
    const cards = Array.from(el().querySelectorAll('app-group-member-card'));
    expect(cards[0].querySelector('button')).toBeNull();
    expect(cards[1].querySelector('button')).not.toBeNull();
  });

  it('zeigt „Gruppe verlassen“ bei mehreren und „Gruppe löschen“ beim letzten Mitglied', async () => {
    await load([user(1), user(2)]);
    expect(buttonByText(el(), 'Gruppe verlassen')).toBeDefined();
    expect(buttonByText(el(), 'Gruppe löschen')).toBeUndefined();
  });

  it('zeigt „Gruppe nicht gefunden“, wenn der Store keine Gruppe liefert', async () => {
    http.expectOne('/api/v2/groups/4/users').flush([]);
    await settle();
    http.expectOne('/api/v2/groups').flush([]);
    await settle();
    expect(el().textContent).toContain('Gruppe nicht gefunden');
    click(buttonByText(el(), 'Zurück zur Übersicht'));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/groups');
  });

  it('zeigt bei Ladefehler „Gruppe konnte nicht geladen werden“', async () => {
    http.expectOne('/api/v2/groups/4/users').flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(el().textContent).toContain('Gruppe konnte nicht geladen werden');
  });

  it('leert die aktuelle Gruppe beim Zerstören', async () => {
    await load([user(1)]);
    fixture.destroy();
    expect(store.currentGroup()).toBeNull();
  });

  it('entfernt ein Mitglied und zeigt einen Toast', async () => {
    const success = vi.spyOn(toast, 'success');
    await load([user(1), user(2)]);
    click(el().querySelector('app-group-member-card:nth-child(2) button'));
    await settle();
    http.expectOne({ method: 'DELETE', url: '/api/v2/groups/4/user/2' }).flush(null);
    await settle();
    http.expectOne('/api/v2/groups/4/users').flush([user(1)]);
    await settle();
    expect(success).toHaveBeenCalledWith('Mitglied entfernt', 'Das Mitglied wurde aus der Gruppe entfernt');
  });

  it('zeigt bei Fehler beim Entfernen einen Fehler-Toast', async () => {
    const error = vi.spyOn(toast, 'error');
    await load([user(1), user(2)]);
    click(el().querySelector('app-group-member-card:nth-child(2) button'));
    await settle();
    http.expectOne({ method: 'DELETE', url: '/api/v2/groups/4/user/2' }).flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(error).toHaveBeenCalledWith('Fehler', 'Mitglied konnte nicht entfernt werden');
  });

  it('fügt über den Dialog ein Mitglied hinzu', async () => {
    const success = vi.spyOn(toast, 'success');
    await load([user(1)]);
    const addMember = vi.spyOn(store, 'addMember').mockResolvedValue();
    click(buttonByText(el(), 'Mitglied hinzufügen'));
    fixture.detectChanges();
    const dialog = el().querySelector('app-add-group-member-dialog') as HTMLElement;
    expect(dialog.querySelector('.modal')).not.toBeNull();
    (fixture.debugElement.query((d) => d.name === 'app-add-group-member-dialog').componentInstance as { added: { emit(id: number): void } }).added.emit(9);
    await settle();
    expect(addMember).toHaveBeenCalledWith(4, 9);
    expect(success).toHaveBeenCalledWith('Mitglied hinzugefügt', 'Das Mitglied wurde zur Gruppe hinzugefügt');
  });

  it('zeigt bei Fehler beim Hinzufügen einen Fehler-Toast', async () => {
    const error = vi.spyOn(toast, 'error');
    await load([user(1)]);
    vi.spyOn(store, 'addMember').mockRejectedValue(new Error('x'));
    (fixture.debugElement.query((d) => d.name === 'app-add-group-member-dialog').componentInstance as { added: { emit(id: number): void } }).added.emit(9);
    await settle();
    expect(error).toHaveBeenCalledWith('Fehler', 'Mitglied konnte nicht hinzugefügt werden');
  });

  it('verlässt die Gruppe nach Bestätigung, zeigt einen Toast und navigiert zur Liste', async () => {
    const info = vi.spyOn(toast, 'info');
    await load([user(1), user(2)]);
    click(buttonByText(el(), 'Gruppe verlassen'));
    await settle();
    expect(modal.confirm).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Gruppe verlassen', message: 'Möchtest du diese Gruppe wirklich verlassen?', confirmText: 'Verlassen' }),
    );
    http.expectOne({ method: 'DELETE', url: '/api/v2/groups/4/user/1' }).flush(null);
    await settle();
    expect(info).toHaveBeenCalledWith('Gruppe verlassen', 'Du hast die Gruppe verlassen');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/groups');
  });

  it('verlässt die Gruppe nicht, wenn abgebrochen wird', async () => {
    await load([user(1), user(2)]);
    modal.confirm.mockResolvedValue(false);
    click(buttonByText(el(), 'Gruppe verlassen'));
    await settle();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('zeigt bei Fehler beim Verlassen einen Fehler-Toast', async () => {
    const error = vi.spyOn(toast, 'error');
    await load([user(1), user(2)]);
    click(buttonByText(el(), 'Gruppe verlassen'));
    await settle();
    http.expectOne({ method: 'DELETE', url: '/api/v2/groups/4/user/1' }).flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(error).toHaveBeenCalledWith('Fehler', 'Gruppe konnte nicht verlassen werden');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('löscht die Gruppe als letztes Mitglied nach Bestätigung', async () => {
    const success = vi.spyOn(toast, 'success');
    await load([user(1)]);
    expect(el().textContent).toContain('1 Mitglied');
    click(buttonByText(el(), 'Gruppe löschen'));
    await settle();
    expect(modal.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Gruppe löschen',
        message: 'Du bist das letzte Mitglied. Wenn du gehst, wird die Gruppe gelöscht. Fortfahren?',
        confirmText: 'Löschen',
        confirmColor: 'red',
      }),
    );
    http.expectOne({ method: 'DELETE', url: '/api/v2/groups/4' }).flush(null);
    await settle();
    expect(success).toHaveBeenCalledWith('Gruppe gelöscht', 'Die Gruppe wurde gelöscht');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/groups');
  });

  it('zeigt bei Fehler beim Löschen einen Fehler-Toast', async () => {
    const error = vi.spyOn(toast, 'error');
    await load([user(1)]);
    click(buttonByText(el(), 'Gruppe löschen'));
    await settle();
    http.expectOne({ method: 'DELETE', url: '/api/v2/groups/4' }).flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(error).toHaveBeenCalledWith('Fehler', 'Gruppe konnte nicht gelöscht werden');
  });
});
