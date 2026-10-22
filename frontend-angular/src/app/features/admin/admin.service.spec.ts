import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminService } from './admin.service';

/** Pfade laut Backend: `AdminStatisticsResource` (`/admin/stats`) und `AdminUserResource` (`/admin/users`). */
describe('AdminService (Backend-Pfade)', () => {
  let service: AdminService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AdminService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.match(() => true));

  it('lädt die Statistik von /api/v2/admin/stats', () => {
    void service.statistics();
    const req = http.expectOne((r) => r.url === '/api/v2/admin/stats');
    expect(req.request.method).toBe('GET');
  });

  it('lädt die Nutzerliste von /api/v2/admin/users mit size=20', () => {
    void service.users(20);
    const req = http.expectOne((r) => r.url === '/api/v2/admin/users');
    expect(req.request.params.get('size')).toBe('20');
  });
});
