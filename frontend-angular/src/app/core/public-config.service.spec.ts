import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PublicConfigService } from './public-config.service';

const impressum = { name: 'Max Muster', street: 'Weg 1', city: '12345 Stadt', email: 'a@b.de', phone: '0123' };

describe('PublicConfigService', () => {
  let service: PublicConfigService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PublicConfigService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lädt die Impressum-Werte von /api/config', async () => {
    const result = service.loadImpressum();
    http.expectOne('/api/config').flush({ impressum });
    await result;
    expect(service.impressum()).toEqual(impressum);
  });

  it('lädt nur einmal, auch bei mehreren Aufrufen', async () => {
    const first = service.loadImpressum();
    const second = service.loadImpressum();
    http.expectOne('/api/config').flush({ impressum });
    await Promise.all([first, second]);
    await service.loadImpressum();
    http.expectNone('/api/config');
  });

  it('bleibt bei einem Fehler leer (Platzhalter werden angezeigt)', async () => {
    const result = service.loadImpressum();
    http.expectOne('/api/config').flush(null, { status: 502, statusText: 'Bad Gateway' });
    await result;
    expect(service.impressum()).toEqual({ name: '', street: '', city: '', email: '', phone: '' });
  });
});
