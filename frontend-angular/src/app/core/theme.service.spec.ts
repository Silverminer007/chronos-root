import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let prefersDark: boolean;

  beforeEach(() => {
    prefersDark = false;
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: prefersDark, media: query }));
    document.documentElement.removeAttribute('data-bs-theme');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('übernimmt das helle Farbschema des Systems als data-bs-theme', () => {
    const service = TestBed.inject(ThemeService);
    service.init();
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('light');
    expect(service.theme()).toBe('light');
  });

  it('übernimmt das dunkle Farbschema des Systems als data-bs-theme', () => {
    prefersDark = true;
    const service = TestBed.inject(ThemeService);
    service.init();
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('dark');
  });

  it('setzt das Attribut beim expliziten Wechsel', () => {
    const service = TestBed.inject(ThemeService);
    service.init();
    service.set('dark');
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('dark');
    expect(service.theme()).toBe('dark');
  });
});
