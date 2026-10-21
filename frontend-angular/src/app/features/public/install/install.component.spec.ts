import { TestBed } from '@angular/core/testing';
import { InstallComponent } from './install.component';

const CHROME = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36';
const FIREFOX = 'Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0';

describe('InstallComponent', () => {
  async function render(userAgent: string) {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(userAgent);
    TestBed.configureTestingModule({ imports: [InstallComponent] });
    const fixture = TestBed.createComponent(InstallComponent);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => vi.restoreAllMocks());

  const browsers = (el: HTMLElement) => Array.from(el.querySelectorAll('section h2.browser-name')).map((h) => h.textContent?.trim());

  it('zeigt Überschrift und Erklärung', async () => {
    const el = await render(CHROME);
    expect(el.querySelector('h1')?.textContent).toContain('Chronos installieren');
    expect(el.textContent).toContain('App installieren');
  });

  it('listet Chrome, Safari und Firefox, der erkannte Browser zuerst und markiert', async () => {
    const el = await render(FIREFOX);
    expect(browsers(el)).toEqual(['Firefox', 'Chrome / Edge', 'Safari']);
    expect(el.querySelectorAll('.badge')).toHaveLength(1);
    expect(el.querySelector('.badge')?.textContent).toContain('Dein Browser');
  });

  it('markiert bei unbekanntem Browser keinen', async () => {
    const el = await render('curl/8');
    expect(browsers(el)).toEqual(['Chrome / Edge', 'Safari', 'Firefox']);
    expect(el.querySelector('.badge')).toBeNull();
  });

  it('verweist in den Schritten auf die eigene Adresse', async () => {
    const el = await render(CHROME);
    const link = el.querySelector('ol a') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe(location.host);
    expect(link.getAttribute('href')).toBe(location.origin);
  });

  it('erklärt, was eine PWA ist', async () => {
    const el = await render(CHROME);
    expect(el.textContent).toContain('Was ist eine PWA?');
    expect(el.textContent).toContain('Kein App Store nötig');
  });
});
