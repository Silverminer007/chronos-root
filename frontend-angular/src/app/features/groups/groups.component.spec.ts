import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ModalService } from '../../core/modal.service';
import { Group } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { buttonByText, click } from '../appointment/testing';
import { GroupsComponent } from './groups.component';

const group = (id: number): Group => ({ id, name: `Gruppe ${id}`, members: [] });

describe('GroupsComponent', () => {
  let fixture: ComponentFixture<GroupsComponent>;
  let http: HttpTestingController;
  let toast: ToastService;
  const modal = { confirm: vi.fn() };
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    modal.confirm.mockReset().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: ModalService, useValue: modal }],
    });
    http = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    fixture = TestBed.createComponent(GroupsComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const load = async (groups: Group[]) => {
    http.expectOne('/api/v2/groups').flush(groups);
    await settle();
  };

  it('zeigt beim Laden „Wird geladen...“', () => {
    expect(el().textContent).toContain('Wird geladen...');
    http.match(() => true);
  });

  it('listet die Gruppen', async () => {
    await load([group(1), group(2)]);
    expect(el().querySelectorAll('app-group-card')).toHaveLength(2);
    expect(el().textContent).toContain('Verwalte deine Gruppen für Termine');
  });

  it('zeigt den Leerzustand und öffnet den Dialog „Neue Gruppe“', async () => {
    await load([]);
    expect(el().textContent).toContain('Noch keine Gruppen');
    click(buttonByText(el(), 'Erste Gruppe erstellen'));
    fixture.detectChanges();
    expect(el().querySelector('app-create-group-dialog .modal')).not.toBeNull();
  });

  it('öffnet den Dialog über „Neue Gruppe“ und den FAB', async () => {
    await load([group(1)]);
    click(buttonByText(el(), 'Neue Gruppe'));
    fixture.detectChanges();
    expect(el().querySelector('app-create-group-dialog .modal')).not.toBeNull();
  });

  it('zeigt bei Ladefehler die Meldung', async () => {
    http.expectOne('/api/v2/groups').flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(el().textContent).toContain('Fehler beim Laden');
    expect(el().textContent).toContain('Fehler beim Laden der Gruppen');
    expect(el().querySelector('app-group-card')).toBeNull();
  });

  it('löscht eine Gruppe nach Bestätigung und zeigt einen Toast', async () => {
    const success = vi.spyOn(toast, 'success');
    await load([group(1), group(2)]);
    click(el().querySelector('app-group-card button[title="Gruppe löschen"]'));
    await settle();
    http.expectOne({ method: 'DELETE', url: '/api/v2/groups/1' }).flush(null);
    await settle();
    expect(success).toHaveBeenCalledWith('Gruppe gelöscht', 'Die Gruppe wurde gelöscht');
    expect(el().querySelectorAll('app-group-card')).toHaveLength(1);
  });

  it('zeigt bei Fehler beim Löschen einen Fehler-Toast', async () => {
    const error = vi.spyOn(toast, 'error');
    await load([group(1)]);
    click(el().querySelector('app-group-card button[title="Gruppe löschen"]'));
    await settle();
    http.expectOne({ method: 'DELETE', url: '/api/v2/groups/1' }).flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(error).toHaveBeenCalledWith('Fehler', 'Gruppe konnte nicht gelöscht werden');
  });
});
