import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { buttonByText, click } from '../appointment/testing';
import { SurveyComponent, countWords } from './survey.component';

describe('countWords', () => {
  it.each([
    ['', 0],
    ['   ', 0],
    ['eins', 1],
    ['  eins   zwei\ndrei ', 3],
  ])('%j → %i', (text, count) => expect(countWords(text)).toBe(count));
});

describe('SurveyComponent', () => {
  let fixture: ComponentFixture<SurveyComponent>;
  let http: HttpTestingController;
  let toast: ToastService;
  const router = { navigateByUrl: vi.fn() };
  const el = () => fixture.nativeElement as HTMLElement;

  const create = (query: Record<string, string> = {}) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 's-1' }), queryParamMap: convertToParamMap(query) } },
        },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: { user: signal(null) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    fixture = TestBed.createComponent(SurveyComponent);
    fixture.detectChanges();
  };

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const eligible = async (value: boolean) => {
    http.expectOne('/api/v2/survey/s-1/eligible').flush(value);
    await settle();
  };

  const radio = (value: string) => el().querySelector<HTMLInputElement>(`input[type=radio][value="${value}"]`)!;
  const checkbox = (label: string) =>
    el().querySelector<HTMLInputElement>(
      `#${Array.from(el().querySelectorAll('label')).find((l) => l.textContent?.includes(label))!.htmlFor}`,
    )!;
  const type = (input: HTMLTextAreaElement | HTMLInputElement, text: string) => {
    input.value = text;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  beforeEach(() => {
    router.navigateByUrl.mockReset();
    vi.useFakeTimers({ toFake: ['setTimeout'], shouldAdvanceTime: true });
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('zeigt das Formular mit fünf Fragen, wenn der Nutzer teilnehmen darf', async () => {
    create();
    await eligible(true);
    expect(el().textContent).toContain('Kurze Umfrage');
    expect(el().querySelectorAll('.question')).toHaveLength(5);
  });

  it('zeigt „bereits geantwortet“, wenn der Nutzer nicht teilnahmeberechtigt ist', async () => {
    create();
    await eligible(false);
    expect(el().textContent).toContain('Danke, du hast bereits geantwortet!');
    const link = el().querySelector('main a');
    expect(link?.getAttribute('routerLink')).toBe('/agenda');
    expect(link?.textContent).toContain('Zur Übersicht');
  });

  it('zeigt das Formular, wenn die Prüfung fehlschlägt', async () => {
    create();
    http.expectOne('/api/v2/survey/s-1/eligible').flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(el().textContent).toContain('Kurze Umfrage');
  });

  it('überspringt die Prüfung bei Aufruf aus einer Push-Benachrichtigung', async () => {
    create({ from: 'push' });
    await settle();
    http.expectNone('/api/v2/survey/s-1/eligible');
    expect(el().textContent).toContain('Kurze Umfrage');
  });

  it('zeigt das Freitextfeld nur bei „Etwas anderes“', async () => {
    create();
    await eligible(true);
    expect(el().querySelector('input[type=text]')).toBeNull();
    radio('other').click();
    fixture.detectChanges();
    expect(el().querySelector('input[type=text]')).not.toBeNull();
  });

  it('zählt die Wörter und markiert Überschreitungen', async () => {
    create();
    await eligible(true);
    const q2 = el().querySelectorAll('textarea')[0];
    type(q2, 'a b c');
    expect(el().textContent).toContain('3 / 30 Wörter');
    type(q2, Array(31).fill('w').join(' '));
    expect(el().querySelector('.text-danger')?.textContent).toContain('31 / 30 Wörter');
  });

  it('„Keines davon“ schließt die übrigen Antworten aus (Q3)', async () => {
    create();
    await eligible(true);
    for (const label of ['Jemanden als Freund hinzufügen', 'Eine Gruppe erstellen oder nutzen', 'Keines davon']) {
      checkbox(label).click();
      fixture.detectChanges();
    }
    expect(checkbox('Jemanden als Freund hinzufügen').checked).toBe(false);
    expect(checkbox('Keines davon').checked).toBe(true);
    checkbox('Eine Gruppe erstellen oder nutzen').click();
    fixture.detectChanges();
    expect(checkbox('Keines davon').checked).toBe(false);
  });

  it('sendet nur beantwortete Fragen und leitet nach zwei Sekunden zur Agenda', async () => {
    create();
    await eligible(true);
    radio('other').click();
    fixture.detectChanges();
    type(el().querySelector<HTMLInputElement>('input[type=text]')!, '  Fotos  ');
    type(el().querySelectorAll('textarea')[1], ' Mehr Farben ');
    checkbox('Jemanden als Freund hinzufügen').click();
    checkbox("Eine 'Vielleicht / unverbindlich'-Option").click();
    click(buttonByText(el(), 'Absenden'));
    const req = http.expectOne('/api/v2/survey/s-1/responses');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      answers: { q1: 'other', q1_other: 'Fotos', q3: ['friends'], q4: ['maybe_option'], q5: 'Mehr Farben' },
    });
    req.flush(null);
    await settle();
    expect(el().textContent).toContain('Danke!');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    vi.advanceTimersByTime(2000);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/agenda');
  });

  it('sendet ein leeres Antwortobjekt, wenn nichts ausgefüllt ist', async () => {
    create();
    await eligible(true);
    click(buttonByText(el(), 'Absenden'));
    const req = http.expectOne('/api/v2/survey/s-1/responses');
    expect(req.request.body).toEqual({ answers: {} });
    req.flush(null);
    await settle();
  });

  it('zeigt bei Status 400 „bereits geantwortet“', async () => {
    create();
    await eligible(true);
    click(buttonByText(el(), 'Absenden'));
    http.expectOne('/api/v2/survey/s-1/responses').flush(null, { status: 400, statusText: 'Bad Request' });
    await settle();
    expect(el().textContent).toContain('Danke, du hast bereits geantwortet!');
  });

  it('meldet andere Fehler als Toast und lässt das Formular stehen', async () => {
    create();
    await eligible(true);
    click(buttonByText(el(), 'Absenden'));
    http.expectOne('/api/v2/survey/s-1/responses').flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(toast.toasts()[0]).toMatchObject({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Antworten konnten nicht gesendet werden. Bitte versuche es erneut.',
    });
    expect(el().textContent).toContain('Kurze Umfrage');
    expect(buttonByText(el(), 'Absenden')?.disabled).toBe(false);
  });
});
