import { TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/toast.service';
import { buttonByText, click } from '../appointment/testing';
import { CreateGroupDialogComponent } from './create-group-dialog.component';
import { GroupStore } from './group.store';

describe('CreateGroupDialogComponent', () => {
  const store = { createGroup: vi.fn() };
  const toast = { success: vi.fn() };

  const render = (visible = true) => {
    store.createGroup.mockReset().mockResolvedValue({ id: 1, name: 'x', members: [] });
    toast.success.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: GroupStore, useValue: store },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(CreateGroupDialogComponent);
    fixture.componentRef.setInput('visible', visible);
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const input = () => el.querySelector('input') as HTMLInputElement;
    const type = (value: string) => {
      input().value = value;
      input().dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const settle = async () => {
      await new Promise((resolve) => setTimeout(resolve));
      fixture.detectChanges();
    };
    return { fixture, el, input, type, settle, closed };
  };

  it('rendert nichts, solange er unsichtbar ist', () => {
    expect(render(false).el.querySelector('.modal')).toBeNull();
  });

  it('sperrt „Erstellen“ bei weniger als zwei Zeichen (nach Trimmen)', () => {
    const { el, type } = render();
    expect(buttonByText(el, 'Erstellen')?.disabled).toBe(true);
    type(' a ');
    expect(buttonByText(el, 'Erstellen')?.disabled).toBe(true);
    type('ab');
    expect(buttonByText(el, 'Erstellen')?.disabled).toBe(false);
  });

  it('legt die Gruppe mit getrimmtem Namen an, zeigt einen Toast und schließt', async () => {
    const { el, type, settle, closed } = render();
    type('  Leiterrunde ');
    click(buttonByText(el, 'Erstellen'));
    await settle();
    expect(store.createGroup).toHaveBeenCalledWith('Leiterrunde');
    expect(toast.success).toHaveBeenCalledWith('Gruppe erstellt', 'Die Gruppe "Leiterrunde" wurde erstellt');
    expect(closed).toHaveBeenCalled();
  });

  it('erstellt mit Enter', async () => {
    const { input, type, settle } = render();
    type('Helfer');
    input().dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter' }));
    await settle();
    expect(store.createGroup).toHaveBeenCalledWith('Helfer');
  });

  it('zeigt bei Fehler die Meldung und bleibt offen', async () => {
    const { el, type, settle, closed } = render();
    store.createGroup.mockRejectedValue({});
    type('Helfer');
    click(buttonByText(el, 'Erstellen'));
    await settle();
    expect(el.textContent).toContain('Fehler beim Erstellen der Gruppe');
    expect(closed).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('leert das Feld beim Abbrechen und schließt', () => {
    const { el, type, closed, fixture } = render();
    type('Helfer');
    click(buttonByText(el, 'Abbrechen'));
    fixture.detectChanges();
    expect(closed).toHaveBeenCalled();
    expect((el.querySelector('input') as HTMLInputElement).value).toBe('');
  });

  it('schließt mit Escape', () => {
    const { closed } = render();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(closed).toHaveBeenCalled();
  });
});
