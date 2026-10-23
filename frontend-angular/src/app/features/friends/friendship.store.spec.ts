import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Friend, FriendshipRequest } from '../../core/models';
import { FriendshipStore } from './friendship.store';

const friend = (id: number): Friend => ({
  user_id: id,
  name: `Freund ${id}`,
  email: `f${id}@x.de`,
  profile_picture_url: '',
  friends_since: '2026-01-01T00:00:00',
});

const request = (id: number, isIncoming: boolean): FriendshipRequest => ({
  requestId: id,
  userId: id + 100,
  userName: `Person ${id}`,
  userEmail: `p${id}@x.de`,
  profilePictureUrl: '',
  status: 'PENDING',
  createdAt: '2026-10-21T10:00:00',
  respondedAt: '',
  isIncoming,
});

describe('FriendshipStore', () => {
  let store: FriendshipStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(FriendshipStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lädt Freunde, setzt den Zustand und liefert die Liste', async () => {
    const result = store.fetchFriends();
    expect(store.loading()).toBe(true);
    http.expectOne('/api/v2/friendships/friends').flush([friend(1), friend(2)]);
    expect(await result).toHaveLength(2);
    expect(store.friends()).toHaveLength(2);
    expect(store.friendsCount()).toBe(2);
    expect(store.loading()).toBe(false);
  });

  it('behandelt eine leere Antwort als leere Liste', async () => {
    const result = store.fetchFriends();
    http.expectOne('/api/v2/friendships/friends').flush(null);
    expect(await result).toEqual([]);
    expect(store.friends()).toEqual([]);
  });

  it('setzt bei Ladefehler die deutsche Meldung und wirft', async () => {
    const result = store.fetchFriends();
    http.expectOne('/api/v2/friendships/friends').flush(null, { status: 500, statusText: 'x' });
    await expect(result).rejects.toBeDefined();
    expect(store.error()).toBe('Fehler beim Laden der Freunde');
    expect(store.loading()).toBe(false);
  });

  it('lädt ein- und ausgehende Anfragen und zählt sie', async () => {
    const incoming = store.fetchIncomingRequests();
    http.expectOne('/api/v2/friendships/requests/incoming').flush([request(1, true)]);
    await incoming;
    const outgoing = store.fetchOutgoingRequests();
    http.expectOne('/api/v2/friendships/requests/outgoing').flush([request(2, false), request(3, false)]);
    await outgoing;
    expect(store.incomingRequestsCount()).toBe(1);
    expect(store.outgoingRequestsCount()).toBe(2);
    expect(store.totalRequestsCount()).toBe(3);
  });

  it('meldet Fehler beim Laden der Anfragen', async () => {
    const incoming = store.fetchIncomingRequests();
    http.expectOne('/api/v2/friendships/requests/incoming').flush(null, { status: 500, statusText: 'x' });
    await expect(incoming).rejects.toBeDefined();
    expect(store.error()).toBe('Fehler beim Laden der eingehenden Anfragen');
    const outgoing = store.fetchOutgoingRequests();
    http.expectOne('/api/v2/friendships/requests/outgoing').flush(null, { status: 500, statusText: 'x' });
    await expect(outgoing).rejects.toBeDefined();
    expect(store.error()).toBe('Fehler beim Laden der ausgehenden Anfragen');
  });

  it('fetchAll lädt alles parallel', async () => {
    const result = store.fetchAll();
    http.expectOne('/api/v2/friendships/friends').flush([friend(1)]);
    http.expectOne('/api/v2/friendships/requests/incoming').flush([request(1, true)]);
    http.expectOne('/api/v2/friendships/requests/outgoing').flush([]);
    await result;
    expect(store.friends()).toHaveLength(1);
    expect(store.incomingRequests()).toHaveLength(1);
    expect(store.error()).toBeNull();
    expect(store.loading()).toBe(false);
  });

  it('fetchAll meldet den Fehler und wirft', async () => {
    const result = store.fetchAll();
    http.expectOne('/api/v2/friendships/friends').flush(null, { status: 500, statusText: 'x' });
    http.expectOne('/api/v2/friendships/requests/incoming').flush([]);
    http.expectOne('/api/v2/friendships/requests/outgoing').flush([]);
    await expect(result).rejects.toBeDefined();
    expect(store.error()).toBe('Fehler beim Laden der Daten');
    expect(store.loading()).toBe(false);
  });

  it('beendet eine Freundschaft und entfernt den Freund', async () => {
    const load = store.fetchFriends();
    http.expectOne('/api/v2/friendships/friends').flush([friend(1), friend(2)]);
    await load;
    const result = store.endFriendship(1);
    const req = http.expectOne('/api/v2/friendships/friends/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await result;
    expect(store.friends().map((f) => f.user_id)).toEqual([2]);
  });

  it('behält den Freund und meldet, wenn das Beenden scheitert', async () => {
    const load = store.fetchFriends();
    http.expectOne('/api/v2/friendships/friends').flush([friend(1)]);
    await load;
    const result = store.endFriendship(1);
    http.expectOne('/api/v2/friendships/friends/1').flush(null, { status: 500, statusText: 'x' });
    await expect(result).rejects.toBeDefined();
    expect(store.friends()).toHaveLength(1);
    expect(store.error()).toBe('Fehler beim Beenden der Freundschaft');
  });

  it('sendet eine Anfrage per E-Mail und lädt die ausgehenden neu', async () => {
    const result = store.sendRequest('a@b.de');
    const post = http.expectOne((r) => r.url === '/api/v2/friendships/requests' && r.method === 'POST');
    expect(post.request.params.get('email')).toBe('a@b.de');
    post.flush(null);
    await vi.waitFor(() => http.expectOne('/api/v2/friendships/requests/outgoing').flush([request(9, false)]));
    await result;
    expect(store.outgoingRequests()).toHaveLength(1);
  });

  it('meldet Fehler beim Senden', async () => {
    const result = store.sendRequest('a@b.de');
    http.expectOne((r) => r.url === '/api/v2/friendships/requests').flush(null, { status: 404, statusText: 'x' });
    await expect(result).rejects.toBeDefined();
    expect(store.error()).toBe('Fehler beim Senden der Anfrage');
  });

  it('nimmt eine Anfrage an, entfernt sie und lädt die Freunde neu', async () => {
    const load = store.fetchIncomingRequests();
    http.expectOne('/api/v2/friendships/requests/incoming').flush([request(1, true), request(2, true)]);
    await load;
    const result = store.acceptRequest(1);
    const post = http.expectOne('/api/v2/friendships/requests/1/accept');
    expect(post.request.method).toBe('POST');
    post.flush(null);
    await vi.waitFor(() => http.expectOne('/api/v2/friendships/friends').flush([friend(101)]));
    await result;
    expect(store.incomingRequests().map((r) => r.requestId)).toEqual([2]);
    expect(store.friends()).toHaveLength(1);
  });

  it('lehnt eine Anfrage ab und entfernt sie', async () => {
    const load = store.fetchIncomingRequests();
    http.expectOne('/api/v2/friendships/requests/incoming').flush([request(1, true)]);
    await load;
    const result = store.declineRequest(1);
    http.expectOne('/api/v2/friendships/requests/1/decline').flush(null);
    await result;
    expect(store.incomingRequests()).toEqual([]);
  });

  it('zieht eine ausgehende Anfrage zurück', async () => {
    const load = store.fetchOutgoingRequests();
    http.expectOne('/api/v2/friendships/requests/outgoing').flush([request(1, false)]);
    await load;
    const result = store.cancelRequest(1);
    const del = http.expectOne('/api/v2/friendships/requests/1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
    await result;
    expect(store.outgoingRequests()).toEqual([]);
  });

  it('meldet Fehler bei Annehmen, Ablehnen und Zurückziehen', async () => {
    const fail = (url: string) => http.expectOne(url).flush(null, { status: 500, statusText: 'x' });
    const accept = store.acceptRequest(1);
    fail('/api/v2/friendships/requests/1/accept');
    await expect(accept).rejects.toBeDefined();
    expect(store.error()).toBe('Fehler beim Annehmen der Anfrage');
    const decline = store.declineRequest(1);
    fail('/api/v2/friendships/requests/1/decline');
    await expect(decline).rejects.toBeDefined();
    expect(store.error()).toBe('Fehler beim Ablehnen der Anfrage');
    const cancel = store.cancelRequest(1);
    fail('/api/v2/friendships/requests/1');
    await expect(cancel).rejects.toBeDefined();
    expect(store.error()).toBe('Fehler beim Zurückziehen der Anfrage');
    store.clearError();
    expect(store.error()).toBeNull();
  });
});
