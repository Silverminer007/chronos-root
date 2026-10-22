import { ComponentFixture, TestBed } from '@angular/core/testing';
import { buttonByText, click } from '../features/appointment/testing';
import { PushService } from '../core/push.service';
import { PushPermissionModalComponent } from './push-permission-modal.component';

describe('PushPermissionModalComponent', () => {
  let fixture: ComponentFixture<PushPermissionModalComponent>;
  const push = { shouldAsk: vi.fn(), subscribe: vi.fn(), markAsked: vi.fn() };
  const el = () => fixture.nativeElement as HTMLElement;

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const open = async (shouldAsk = true) => {
    push.shouldAsk.mockResolvedValue(shouldAsk);
    fixture = TestBed.createComponent(PushPermissionModalComponent);
    fixture.detectChanges();
    await settle();
  };

  beforeEach(() => {
    Object.values(push).forEach((fn) => fn.mockReset());
    TestBed.configureTestingModule({ providers: [{ provide: PushService, useValue: push }] });
  });

  it('bleibt verborgen, wenn nicht gefragt werden soll', async () => {
    await open(false);
    expect(el().textContent).toBe('');
  });

  it('bleibt verborgen, wenn die Prüfung fehlschlägt', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    push.shouldAsk.mockRejectedValue(new Error('x'));
    fixture = TestBed.createComponent(PushPermissionModalComponent);
    fixture.detectChanges();
    await settle();
    expect(el().textContent).toBe('');
  });

  it('fragt nach der Berechtigung', async () => {
    await open();
    expect(el().textContent).toContain('Push-Benachrichtigungen aktivieren?');
  });

  it('„Aktivieren“ abonniert, merkt die Zusage und schließt', async () => {
    await open();
    push.subscribe.mockResolvedValue({});
    click(buttonByText(el(), 'Aktivieren'));
    await settle();
    expect(push.subscribe).toHaveBeenCalled();
    expect(push.markAsked).toHaveBeenCalledWith('granted');
    expect(el().textContent).toBe('');
  });

  it('merkt eine Ablehnung des Browsers als „denied“', async () => {
    await open();
    push.subscribe.mockRejectedValue(new Error('NotAllowedError'));
    click(buttonByText(el(), 'Aktivieren'));
    await settle();
    expect(push.markAsked).toHaveBeenCalledWith('denied');
    expect(el().textContent).toBe('');
  });

  it('„Nicht jetzt“ merkt „denied“ und schließt', async () => {
    await open();
    click(buttonByText(el(), 'Nicht jetzt'));
    fixture.detectChanges();
    expect(push.markAsked).toHaveBeenCalledWith('denied');
    expect(el().textContent).toBe('');
  });

  it('Schließen merkt „dismissed“', async () => {
    await open();
    click(el().querySelector('.btn-close'));
    fixture.detectChanges();
    expect(push.markAsked).toHaveBeenCalledWith('dismissed');
    expect(el().textContent).toBe('');
  });
});
