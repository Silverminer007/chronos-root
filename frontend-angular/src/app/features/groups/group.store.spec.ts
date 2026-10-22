import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Group, User } from '../../core/models';
import { GroupStore } from './group.store';

const user = (id: number): User => ({ id, first_name: `Vor${id}`, last_name: `Nach${id}`, email: null });
const group = (id: number, members: User[] = []): Group => ({ id, name: `Gruppe ${id}`, members });

describe('GroupStore', () => {
  let store: GroupStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(GroupStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const loadGroups = async (groups: Group[]) => {
    const result = store.fetchGroups();
    http.expectOne('/api/v2/groups').flush(groups);
    await result;
  };

  describe('fetchGroups', () => {
    it('lädt die Gruppen und setzt loading', async () => {
      const result = store.fetchGroups();
      expect(store.loading()).toBe(true);
      http.expectOne('/api/v2/groups').flush([group(1), group(2)]);
      await result;
      expect(store.groups()).toHaveLength(2);
      expect(store.groupsCount()).toBe(2);
      expect(store.loading()).toBe(false);
      expect(store.error()).toBeNull();
    });

    it('behandelt eine leere Antwort als leere Liste', async () => {
      await loadGroups(null as unknown as Group[]);
      expect(store.groups()).toEqual([]);
    });

    it('setzt bei Fehler die deutsche Meldung und wirft', async () => {
      const result = store.fetchGroups();
      http.expectOne('/api/v2/groups').flush(null, { status: 500, statusText: 'x' });
      await expect(result).rejects.toBeDefined();
      expect(store.error()).toBe('Fehler beim Laden der Gruppen');
      expect(store.loading()).toBe(false);
    });
  });

  describe('fetchGroup', () => {
    it('lädt die Mitglieder einer bekannten Gruppe und aktualisiert die Liste', async () => {
      await loadGroups([group(1), group(2)]);
      const result = store.fetchGroup(2);
      http.expectOne('/api/v2/groups/2/users').flush([user(7), user(8)]);
      const current = await result;
      expect(current?.members).toHaveLength(2);
      expect(store.currentGroup()?.id).toBe(2);
      expect(store.currentGroupMemberCount()).toBe(2);
      expect(store.groups()[1].members).toHaveLength(2);
      expect(store.loading()).toBe(false);
    });

    it('lädt zuerst alle Gruppen, wenn die Gruppe unbekannt ist', async () => {
      const result = store.fetchGroup(3);
      http.expectOne('/api/v2/groups/3/users').flush([user(1)]);
      await new Promise((resolve) => setTimeout(resolve));
      http.expectOne('/api/v2/groups').flush([group(3)]);
      expect((await result)?.members).toHaveLength(1);
      expect(store.groups()[0].members).toHaveLength(1);
    });

    it('liefert null und lässt currentGroup leer, wenn die Gruppe nicht existiert', async () => {
      const result = store.fetchGroup(9);
      http.expectOne('/api/v2/groups/9/users').flush([]);
      await new Promise((resolve) => setTimeout(resolve));
      http.expectOne('/api/v2/groups').flush([group(1)]);
      expect(await result).toBeNull();
      expect(store.currentGroup()).toBeNull();
    });

    it('verwirft die bisherige currentGroup, wenn eine unbekannte Gruppe angefragt wird', async () => {
      await loadGroups([group(1)]);
      const first = store.fetchGroup(1);
      http.expectOne('/api/v2/groups/1/users').flush([user(1)]);
      await first;
      expect(store.currentGroup()?.id).toBe(1);
      const result = store.fetchGroup(999);
      expect(store.currentGroup()).toBeNull();
      http.expectOne('/api/v2/groups/999/users').flush([]);
      await new Promise((resolve) => setTimeout(resolve));
      http.expectOne('/api/v2/groups').flush([group(1)]);
      expect(await result).toBeNull();
      expect(store.currentGroup()).toBeNull();
    });

    it('setzt bei Fehler die deutsche Meldung und wirft', async () => {
      const result = store.fetchGroup(1);
      http.expectOne('/api/v2/groups/1/users').flush(null, { status: 404, statusText: 'x' });
      await expect(result).rejects.toBeDefined();
      expect(store.error()).toBe('Fehler beim Laden der Gruppe');
      expect(store.loading()).toBe(false);
    });
  });

  describe('createGroup', () => {
    it('legt die Gruppe an und hängt sie an die Liste', async () => {
      const result = store.createGroup('Leiterrunde');
      const req = http.expectOne('/api/v2/groups');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ name: 'Leiterrunde' });
      req.flush(group(4));
      expect((await result).id).toBe(4);
      expect(store.groups().map((g) => g.id)).toEqual([4]);
    });

    it('setzt bei Fehler die Meldung und wirft', async () => {
      const result = store.createGroup('x');
      http.expectOne('/api/v2/groups').flush(null, { status: 500, statusText: 'x' });
      await expect(result).rejects.toBeDefined();
      expect(store.error()).toBe('Fehler beim Erstellen der Gruppe');
    });
  });

  describe('deleteGroup', () => {
    it('löscht die Gruppe und entfernt sie aus der Liste', async () => {
      await loadGroups([group(1), group(2)]);
      const result = store.deleteGroup(1);
      const req = http.expectOne('/api/v2/groups/1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
      await result;
      expect(store.groups().map((g) => g.id)).toEqual([2]);
    });

    it('setzt bei Fehler die Meldung, wirft und behält die Liste', async () => {
      await loadGroups([group(1)]);
      const result = store.deleteGroup(1);
      http.expectOne('/api/v2/groups/1').flush(null, { status: 500, statusText: 'x' });
      await expect(result).rejects.toBeDefined();
      expect(store.error()).toBe('Fehler beim Löschen der Gruppe');
      expect(store.groups()).toHaveLength(1);
    });
  });

  describe('Mitglieder', () => {
    it('addMember lädt bei geöffneter Gruppe deren Mitglieder neu', async () => {
      await loadGroups([group(1)]);
      const open = store.fetchGroup(1);
      http.expectOne('/api/v2/groups/1/users').flush([user(1)]);
      await open;

      const result = store.addMember(1, 5);
      const post = http.expectOne('/api/v2/groups/1/user/5');
      expect(post.request.method).toBe('POST');
      post.flush(null);
      await new Promise((resolve) => setTimeout(resolve));
      http.expectOne('/api/v2/groups/1/users').flush([user(1), user(5)]);
      await result;
      expect(store.currentGroup()?.members).toHaveLength(2);
    });

    it('addMember lädt ohne geöffnete Gruppe die Gruppenliste neu', async () => {
      const result = store.addMember(1, 5);
      http.expectOne('/api/v2/groups/1/user/5').flush(null);
      await new Promise((resolve) => setTimeout(resolve));
      http.expectOne('/api/v2/groups').flush([group(1)]);
      await result;
      expect(store.groups()).toHaveLength(1);
    });

    it('addMember setzt bei Fehler die Meldung und wirft', async () => {
      const result = store.addMember(1, 5);
      http.expectOne('/api/v2/groups/1/user/5').flush(null, { status: 500, statusText: 'x' });
      await expect(result).rejects.toBeDefined();
      expect(store.error()).toBe('Fehler beim Hinzufügen des Mitglieds');
    });

    it('removeMember entfernt das Mitglied und lädt die Liste neu', async () => {
      const result = store.removeMember(1, 5);
      const del = http.expectOne('/api/v2/groups/1/user/5');
      expect(del.request.method).toBe('DELETE');
      del.flush(null);
      await new Promise((resolve) => setTimeout(resolve));
      http.expectOne('/api/v2/groups').flush([group(1)]);
      await result;
    });

    it('removeMember setzt bei Fehler die Meldung und wirft', async () => {
      const result = store.removeMember(1, 5);
      http.expectOne('/api/v2/groups/1/user/5').flush(null, { status: 500, statusText: 'x' });
      await expect(result).rejects.toBeDefined();
      expect(store.error()).toBe('Fehler beim Entfernen des Mitglieds');
    });

    it('leaveGroup entfernt die Gruppe und leert currentGroup', async () => {
      await loadGroups([group(1), group(2)]);
      const open = store.fetchGroup(1);
      http.expectOne('/api/v2/groups/1/users').flush([user(1)]);
      await open;

      const result = store.leaveGroup(1, 1);
      const del = http.expectOne('/api/v2/groups/1/user/1');
      expect(del.request.method).toBe('DELETE');
      del.flush(null);
      await result;
      expect(store.groups().map((g) => g.id)).toEqual([2]);
      expect(store.currentGroup()).toBeNull();
    });

    it('leaveGroup setzt bei Fehler die Meldung und wirft', async () => {
      const result = store.leaveGroup(1, 1);
      http.expectOne('/api/v2/groups/1/user/1').flush(null, { status: 500, statusText: 'x' });
      await expect(result).rejects.toBeDefined();
      expect(store.error()).toBe('Fehler beim Verlassen der Gruppe');
    });
  });

  it('clearCurrentGroup und clearError setzen zurück', async () => {
    await loadGroups([group(1)]);
    const open = store.fetchGroup(1);
    http.expectOne('/api/v2/groups/1/users').flush([]);
    await open;
    store.clearCurrentGroup();
    expect(store.currentGroup()).toBeNull();

    const failing = store.createGroup('x');
    http.expectOne('/api/v2/groups').flush(null, { status: 500, statusText: 'x' });
    await failing.catch(() => undefined);
    store.clearError();
    expect(store.error()).toBeNull();
  });

  describe('searchGroups', () => {
    it('sucht Gruppen mit Suchbegriff', async () => {
      const result = store.searchGroups('Jugend');
      http.expectOne((r) => r.url === '/api/v2/groups/' && r.params.get('search') === 'Jugend').flush([{ id: 1, name: 'Jugend' }]);
      expect(await result).toEqual([{ id: 1, name: 'Jugend' }]);
    });

    it('liefert bei leerer Antwort eine leere Liste', async () => {
      const result = store.searchGroups('x');
      http.expectOne((r) => r.url === '/api/v2/groups/').flush(null);
      expect(await result).toEqual([]);
    });
  });
});
