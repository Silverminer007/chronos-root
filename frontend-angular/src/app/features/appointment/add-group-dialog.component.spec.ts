import { TestBed } from '@angular/core/testing';
import { AddGroupDialogComponent } from './add-group-dialog.component';
import { ParticipantLookupService } from './participant-lookup.service';
import { buttonByText, click } from './testing';

describe('AddGroupDialogComponent', () => {
  let lookup: { searchGroups: ReturnType<typeof vi.fn> };

  const render = () => {
    lookup = {
      searchGroups: vi.fn().mockResolvedValue([
        { id: 1, name: 'Jugend', members: [], member_count: 12 },
        { id: 2, name: 'Leiter', members: [] },
      ]),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ParticipantLookupService, useValue: lookup }] });
    const fixture = TestBed.createComponent(AddGroupDialogComponent);
    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const type = async (value: string) => {
      const input = el.querySelector('input[type="text"]') as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      fixture.detectChanges();
    };
    return { fixture, el, type };
  };

  it('sucht Gruppen beim Tippen und zeigt Mitgliederzahl, wenn bekannt', async () => {
    const { el, type } = render();
    await type('J');
    expect(lookup.searchGroups).toHaveBeenCalledWith('J');
    const results = Array.from(el.querySelectorAll('[data-testid="result"]'));
    expect(results).toHaveLength(2);
    expect(results[0].textContent).toContain('12 Mitglieder');
    expect(results[1].textContent).not.toContain('Mitglieder');
  });

  it('zeigt „Keine Gruppen gefunden“ ohne Treffer', async () => {
    const { el, type } = render();
    lookup.searchGroups.mockResolvedValue([]);
    await type('x');
    expect(el.textContent).toContain('Keine Gruppen gefunden');
  });

  it('deaktiviert „Hinzufügen“ ohne Auswahl und emittiert Gruppe und Rolle', async () => {
    const { fixture, el, type } = render();
    const added: unknown[] = [];
    fixture.componentInstance.added.subscribe((e) => added.push(e));
    expect(buttonByText(el, 'Hinzufügen')?.disabled).toBe(true);

    await type('J');
    click(el.querySelector('[data-testid="result"]'));
    click(buttonByText(el, 'Gast'));
    fixture.detectChanges();
    expect(el.textContent).toContain('Als Gast');
    click(buttonByText(el, 'Hinzufügen'));

    expect(added).toEqual([{ id: 1, name: 'Jugend', role: 'GUEST' }]);
  });

  it('sperrt die Buttons, solange `adding` gesetzt ist', () => {
    const { fixture, el } = render();
    fixture.componentRef.setInput('adding', true);
    fixture.detectChanges();
    expect(buttonByText(el, 'Abbrechen')?.disabled).toBe(true);
  });
});
