import { ComponentFixture, TestBed } from '@angular/core/testing';
import { buttonByText, click } from '../features/appointment/testing';
import { PwaInstallDialogComponent } from './pwa-install-dialog.component';

describe('PwaInstallDialogComponent', () => {
  let fixture: ComponentFixture<PwaInstallDialogComponent>;
  const el = () => fixture.nativeElement as HTMLElement;

  const promptEvent = (outcome: 'accepted' | 'dismissed' = 'accepted') => {
    const event = new Event('beforeinstallprompt', { cancelable: true });
    Object.assign(event, { prompt: vi.fn().mockResolvedValue(undefined), userChoice: Promise.resolve({ outcome }) });
    return event as Event & { prompt: ReturnType<typeof vi.fn> };
  };

  const fire = (event: Event) => {
    window.dispatchEvent(event);
    fixture.detectChanges();
  };

  beforeEach(() => {
    fixture = TestBed.createComponent(PwaInstallDialogComponent);
    fixture.detectChanges();
  });

  it('ist zunächst verborgen', () => {
    expect(el().textContent).toBe('');
  });

  it('erscheint nach beforeinstallprompt und unterdrückt den Browser-Banner', () => {
    const event = promptEvent();
    fire(event);
    expect(event.defaultPrevented).toBe(true);
    expect(el().textContent).toContain('App installieren');
  });

  it('„Später“ schließt den Dialog ohne Installation', () => {
    const event = promptEvent();
    fire(event);
    click(buttonByText(el(), 'Später'));
    fixture.detectChanges();
    expect(el().textContent).toBe('');
    expect(event.prompt).not.toHaveBeenCalled();
  });

  it('„Jetzt installieren“ zeigt den Browser-Dialog und schließt danach', async () => {
    const event = promptEvent();
    fire(event);
    click(buttonByText(el(), 'Jetzt installieren'));
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    expect(event.prompt).toHaveBeenCalled();
    expect(el().textContent).toBe('');
  });
});
