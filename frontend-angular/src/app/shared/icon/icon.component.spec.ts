import { TestBed } from '@angular/core/testing';
import { IconComponent } from './icon.component';

describe('IconComponent', () => {
  async function render(name: string, size?: number) {
    const fixture = TestBed.createComponent(IconComponent);
    fixture.componentRef.setInput('name', name);
    if (size) fixture.componentRef.setInput('size', size);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('rendert ein Lucide-Icon als dekoratives SVG in Schriftgröße', async () => {
    const svg = (await render('map-pin')).querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg?.getAttribute('aria-hidden')).toBe('true');
    expect(svg?.getAttribute('width')).toBe('1em');
    expect(svg?.querySelectorAll('path, circle').length).toBeGreaterThan(0);
  });

  it('übernimmt eine feste Größe', async () => {
    expect((await render('check', 20)).querySelector('svg')?.getAttribute('width')).toBe('20');
  });

  it.each(['google', 'microsoft', 'chrome', 'apple', 'check-circle', 'trash-2', 'more-vertical', 'triangle-alert'])(
    'kennt %s',
    async (name) => {
      expect((await render(name)).querySelector('svg')?.children.length).toBeGreaterThan(0);
    },
  );

  it('wirft bei unbekanntem Namen', async () => {
    await expect(render('gibt-es-nicht')).rejects.toThrow();
  });
});
