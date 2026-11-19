import type { LucideIconData } from '@lucide/angular';
import type { IconNode } from 'lucide';

/** Verpackt die Icon-Daten aus `lucide` (tree-shakable) für `provideLucideIcons`. */
export const icon = (name: string, node: IconNode): LucideIconData => ({ name, node });

// Lucide enthält keine Markenlogos; als Umriss-Icons im Lucide-Raster (24×24, Strich 2) nachgebaut.
export const GOOGLE_ICON: LucideIconData = {
  name: 'google',
  node: [
    ['path', { d: 'M21 12h-9', key: 'g-bar' }],
    ['path', { d: 'M20.5 12a8.5 8.5 0 1 1-2.5-6', key: 'g-arc' }],
  ],
};

export const MICROSOFT_ICON: LucideIconData = {
  name: 'microsoft',
  node: [
    ['rect', { x: '3', y: '3', width: '8', height: '8', key: 'm-tl' }],
    ['rect', { x: '13', y: '3', width: '8', height: '8', key: 'm-tr' }],
    ['rect', { x: '3', y: '13', width: '8', height: '8', key: 'm-bl' }],
    ['rect', { x: '13', y: '13', width: '8', height: '8', key: 'm-br' }],
  ],
};

export const CHROME_ICON: LucideIconData = {
  name: 'chrome',
  node: [
    ['circle', { cx: '12', cy: '12', r: '10', key: 'c-outer' }],
    ['circle', { cx: '12', cy: '12', r: '4', key: 'c-inner' }],
    ['line', { x1: '21.17', x2: '12', y1: '8', y2: '8', key: 'c-l1' }],
    ['line', { x1: '3.95', x2: '8.54', y1: '6.06', y2: '14', key: 'c-l2' }],
    ['line', { x1: '10.88', x2: '15.46', y1: '21.94', y2: '14', key: 'c-l3' }],
  ],
};
