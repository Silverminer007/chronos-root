import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ParticipantLookupService } from './participant-lookup.service';

describe('ParticipantLookupService', () => {
  let service: ParticipantLookupService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ParticipantLookupService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sucht Gruppen mit Suchbegriff', async () => {
    const result = service.searchGroups('Jugend');
    http.expectOne((r) => r.url === '/api/v2/groups/' && r.params.get('search') === 'Jugend').flush([{ id: 1, name: 'Jugend' }]);
    expect(await result).toEqual([{ id: 1, name: 'Jugend' }]);
  });

  it('liefert bei leerer Antwort eine leere Liste', async () => {
    const result = service.searchGroups('x');
    http.expectOne((r) => r.url === '/api/v2/groups/').flush(null);
    expect(await result).toEqual([]);
  });
});
