import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { buttonByText, click } from '../features/appointment/testing';
import { LEAVE_DELAY_MS, NewFeaturesDialogComponent } from './new-features-dialog.component';

describe('NewFeaturesDialogComponent', () => {
  let fixture: ComponentFixture<NewFeaturesDialogComponent>;
  let navigate: ReturnType<typeof vi.fn>;
  const el = () => fixture.nativeElement as HTMLElement;
  const title = () => el().querySelector('h2')?.textContent;
  const shown = () => JSON.parse(localStorage.getItem('shownFeatureIds') ?? '[]') as string[];

  const open = () => {
    fixture = TestBed.createComponent(NewFeaturesDialogComponent);
    fixture.detectChanges();
  };

  const press = (text: string) => {
    click(buttonByText(el(), text));
    fixture.detectChanges();
  };

  const finishLeaving = () => {
    vi.advanceTimersByTime(LEAVE_DELAY_MS);
    fixture.detectChanges();
  };

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({ providers: [{ provide: Router, useValue: { navigateByUrl: navigate } }] });
  });

  afterEach(() => vi.useRealTimers());

  it('bleibt verborgen, wenn alles gesehen wurde', () => {
    localStorage.setItem(
      'shownFeatureIds',
      JSON.stringify(['profile-page-v1', 'linked-accounts-v1', 'passkeys-v1', 'change-password-v1']),
    );
    open();
    expect(el().textContent).toBe('');
  });

  it('zeigt die erste ungesehene Neuigkeit mit Zähler', () => {
    open();
    expect(title()).toBe('Dein Profil');
    expect(el().textContent).toContain('1/4');
    expect(buttonByText(el(), 'Zum Profil')).toBeDefined();
    expect(el().querySelector('[aria-label="Zurück"]')).toBeNull();
  });

  it('zeigt nur noch ungesehene Neuigkeiten', () => {
    localStorage.setItem('shownFeatureIds', JSON.stringify(['profile-page-v1']));
    open();
    expect(title()).toBe('Verknüpfte Konten');
    expect(el().textContent).toContain('1/3');
  });

  it('„Weiter“ merkt die Neuigkeit und blättert; „Zurück“ geht zurück', () => {
    open();
    press('Weiter');
    expect(shown()).toEqual(['profile-page-v1']);
    expect(title()).toBe('Verknüpfte Konten');
    click(el().querySelector('[aria-label="Zurück"]'));
    fixture.detectChanges();
    expect(title()).toBe('Dein Profil');
  });

  it('die Punkte springen zu einer Neuigkeit', () => {
    open();
    click(el().querySelector('[aria-label="Neuigkeit 3"]'));
    fixture.detectChanges();
    expect(title()).toBe('Passkeys');
  });

  it('„Fertig“ in der letzten Neuigkeit schließt nach der Ausblendzeit', () => {
    open();
    click(el().querySelector('[aria-label="Neuigkeit 4"]'));
    fixture.detectChanges();
    press('Fertig');
    expect(el().querySelector('.modal.leaving')).not.toBeNull();
    finishLeaving();
    expect(el().textContent).toBe('');
    expect(shown()).toEqual(['change-password-v1']);
  });

  it('„Alle überspringen“ markiert alles als gesehen', () => {
    open();
    press('Alle überspringen');
    finishLeaving();
    expect(el().textContent).toBe('');
    expect(shown()).toHaveLength(4);
  });

  it('das Schließen-Kreuz und ein Klick neben den Dialog überspringen alles', () => {
    open();
    click(el().querySelector('.btn-close'));
    finishLeaving();
    expect(shown()).toHaveLength(4);

    localStorage.clear();
    open();
    click(el().querySelector('.modal'));
    finishLeaving();
    expect(shown()).toHaveLength(4);
  });

  it('Escape überspringt alles', () => {
    open();
    el().querySelector('.modal')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    finishLeaving();
    expect(shown()).toHaveLength(4);
  });

  it('der Link-Button merkt die Neuigkeit, schließt und öffnet die Seite', () => {
    open();
    press('Zum Profil');
    finishLeaving();
    expect(shown()).toEqual(['profile-page-v1']);
    expect(navigate).toHaveBeenCalledWith('/profile');
    expect(el().textContent).toBe('');
  });

  it('bietet „Alle überspringen“ nur bei mehreren Neuigkeiten an', () => {
    localStorage.setItem('shownFeatureIds', JSON.stringify(['profile-page-v1', 'linked-accounts-v1', 'passkeys-v1']));
    open();
    expect(buttonByText(el(), 'Alle überspringen')).toBeUndefined();
    expect(buttonByText(el(), 'Fertig')).toBeDefined();
  });
});
