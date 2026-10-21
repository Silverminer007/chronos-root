import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ModalService } from '../../core/modal.service';
import { Friend, FriendshipRequest } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { buttonByText, click } from '../appointment/testing';
import { FriendsComponent } from './friends.component';

const API = '/api/v2/friendships';
const friend = (id: number): Friend => ({ user_id: id, name: `Freund ${id}`, email: '', profile_picture_url: '', friends_since: '' });
const request = (id: number, isIncoming: boolean): FriendshipRequest => ({
  requestId: id,
  userId: id,
  userName: `Anfrager ${id}`,
  userEmail: '',
  profilePictureUrl: '',
  status: 'PENDING',
  createdAt: '',
  respondedAt: '',
  isIncoming,
});

describe('FriendsComponent', () => {
  let fixture: ComponentFixture<FriendsComponent>;
  let http: HttpTestingController;
  let toast: ToastService;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    fixture = TestBed.createComponent(FriendsComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const load = async (friends: Friend[], incoming: FriendshipRequest[] = [], outgoing: FriendshipRequest[] = []) => {
    http.expectOne(`${API}/friends`).flush(friends);
    http.expectOne(`${API}/requests/incoming`).flush(incoming);
    http.expectOne(`${API}/requests/outgoing`).flush(outgoing);
    await settle();
  };

  const tab = (label: string) => click(Array.from(el().querySelectorAll('[role="tab"]')).find((t) => t.textContent?.includes(label)));

  it('zeigt beim Laden „Wird geladen...“', () => {
    expect(el().textContent).toContain('Wird geladen...');
    http.match(() => true);
  });

  it('listet Freunde und zeigt die Zähler an den Tabs', async () => {
    await load([friend(1), friend(2)], [request(5, true)], [request(6, false)]);
    expect(el().querySelectorAll('app-friend-card')).toHaveLength(2);
    expect(el().querySelectorAll('[role="tab"]')[0].textContent).toContain('2');
    expect(el().querySelectorAll('[role="tab"]')[1].textContent).toContain('2');
  });

  it('zeigt den Leerzustand ohne Freunde und öffnet den Dialog', async () => {
    await load([]);
    expect(el().textContent).toContain('Noch keine Freunde');
    click(buttonByText(el(), 'Freund hinzufügen'));
    fixture.detectChanges();
    expect(el().querySelector('app-send-friend-request-dialog .modal')).not.toBeNull();
  });

  it('öffnet den Dialog über Hinzufügen und den mobilen Button', async () => {
    await load([friend(1)]);
    click(buttonByText(el(), 'Hinzufügen'));
    fixture.detectChanges();
    expect(el().querySelector('.modal')).not.toBeNull();
    click(buttonByText(el(), 'Abbrechen'));
    fixture.detectChanges();
    expect(el().querySelector('.modal')).toBeNull();
    click(el().querySelector('[aria-label="Freund hinzufügen"]'));
    fixture.detectChanges();
    expect(el().querySelector('.modal')).not.toBeNull();
  });

  it('zeigt Ladefehler', async () => {
    http.expectOne(`${API}/friends`).flush(null, { status: 500, statusText: 'x' });
    http.expectOne(`${API}/requests/incoming`).flush([]);
    http.expectOne(`${API}/requests/outgoing`).flush([]);
    await settle();
    expect(el().textContent).toContain('Fehler beim Laden');
    expect(el().textContent).toContain('Fehler beim Laden der Daten');
  });

  it('zeigt im Anfragen-Tab ein- und ausgehende Anfragen samt Leerzuständen', async () => {
    await load([], [request(5, true)], []);
    tab('Anfragen');
    fixture.detectChanges();
    expect(el().textContent).toContain('Eingehende Anfragen');
    expect(el().textContent).toContain('(1)');
    expect(el().textContent).toContain('Anfrager 5');
    expect(el().textContent).toContain('Keine ausgehenden Anfragen');
  });

  it('beendet eine Freundschaft nach Bestätigung und zeigt einen Toast', async () => {
    await load([friend(1)]);
    vi.spyOn(TestBed.inject(ModalService), 'confirm').mockResolvedValue(true);
    const success = vi.spyOn(toast, 'add');
    click(el().querySelector('app-friend-card button'));
    await vi.waitFor(() => http.expectOne(`${API}/friends/1`).flush(null));
    await settle();
    expect(success).toHaveBeenCalledWith({ severity: 'success', summary: 'Freundschaft beendet', detail: 'Die Freundschaft wurde beendet' });
    expect(el().querySelectorAll('app-friend-card')).toHaveLength(0);
  });

  it('zeigt einen Fehler-Toast, wenn das Beenden scheitert', async () => {
    await load([friend(1)]);
    vi.spyOn(TestBed.inject(ModalService), 'confirm').mockResolvedValue(true);
    const error = vi.spyOn(toast, 'error');
    click(el().querySelector('app-friend-card button'));
    await vi.waitFor(() => http.expectOne(`${API}/friends/1`).flush(null, { status: 500, statusText: 'x' }));
    await settle();
    expect(error).toHaveBeenCalledWith('Fehler', 'Freundschaft konnte nicht beendet werden');
  });

  it('nimmt eine Anfrage an und lädt die Freunde neu', async () => {
    await load([], [request(5, true)]);
    tab('Anfragen');
    fixture.detectChanges();
    const add = vi.spyOn(toast, 'add');
    click(buttonByText(el(), 'Annehmen'));
    http.expectOne(`${API}/requests/5/accept`).flush(null);
    await vi.waitFor(() => http.expectOne(`${API}/friends`).flush([friend(5)]));
    await settle();
    expect(add).toHaveBeenCalledWith({ severity: 'success', summary: 'Anfrage angenommen', detail: 'Ihr seid jetzt Freunde!' });
  });

  it('lehnt eine Anfrage ab und zieht eine eigene zurück', async () => {
    await load([], [request(5, true)], [request(6, false)]);
    tab('Anfragen');
    fixture.detectChanges();
    const add = vi.spyOn(toast, 'add');
    click(buttonByText(el(), 'Ablehnen'));
    http.expectOne(`${API}/requests/5/decline`).flush(null);
    await settle();
    expect(add).toHaveBeenCalledWith({ severity: 'info', summary: 'Anfrage abgelehnt', detail: 'Die Anfrage wurde abgelehnt' });
    click(buttonByText(el(), 'Zurückziehen'));
    http.expectOne(`${API}/requests/6`).flush(null);
    await settle();
    expect(add).toHaveBeenCalledWith({ severity: 'info', summary: 'Anfrage zurückgezogen', detail: 'Die Anfrage wurde zurückgezogen' });
    expect(el().textContent).toContain('Keine eingehenden Anfragen');
    expect(el().textContent).toContain('Keine ausgehenden Anfragen');
  });

  it('meldet Fehler beim Ablehnen per Toast', async () => {
    await load([], [request(5, true)]);
    tab('Anfragen');
    fixture.detectChanges();
    const error = vi.spyOn(toast, 'error');
    click(buttonByText(el(), 'Ablehnen'));
    http.expectOne(`${API}/requests/5/decline`).flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(error).toHaveBeenCalledWith('Fehler', 'Anfrage konnte nicht abgelehnt werden');
  });
});
