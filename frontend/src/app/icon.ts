import { Component, input } from '@angular/core';

// Un solo componente mantiene el mismo trazo en toda la interfaz.
@Component({
  selector: 'app-icon',
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="paths[name()] || paths['box']" /></svg>`,
  styles: [':host{display:inline-flex;width:20px;height:20px;flex-shrink:0}svg{width:100%;height:100%}'],
})
export class Icon {
  readonly name = input('box');
  readonly paths: Record<string, string> = {
    grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
    box: 'm12 3 9 5v9l-9 5-9-5V8l9-5z M3 8l9 5 9-5 M12 13v9 M7.5 5.5l9 5v4',
    orders: 'M6 3h12v19l-3-2-3 2-3-2-3 2V3z M9 8h6 M9 12h6 M9 16h3',
    inventory: 'm3 7 9-4 9 4v14H3V7z M7 11h10v10 M7 15h10 M7 18h10',
    users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M20 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75',
    chart: 'M3 3v18h18 M7 14v3 M12 10v7 M17 6v11',
    activity: 'M3 12h4l3-8 4 16 3-8h4',
    layers: 'm12 3 10 5-10 5L2 8l10-5z M2 12l10 5 10-5 M2 16l10 5 10-5',
    arrow: 'M5 12h14 M13 6l6 6-6 6',
    up: 'M7 17 17 7 M7 7h10v10',
    down: 'm7 10 5 5 5-5',
    chevron: 'm9 5 7 7-7 7',
    plus: 'M12 5v14 M5 12h14',
    search: 'M21 21l-5-5 M10 17a7 7 0 1 0 0-14 7 7 0 0 0 0 14',
    bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
    check: 'm5 12 4 4L19 6',
    clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 6v6l4 2',
    close: 'm6 6 12 12 M6 18 18 6',
    refresh: 'M20 7A9 9 0 0 0 4 7 M20 3v4h-4 M4 17a9 9 0 0 0 16 0 M4 21v-4h4',
    download: 'M12 3v12 m-5-5 5 5 5-5 M5 16v5h14v-5',
    filter: 'M3 6h18 M6 12h12 M9 18h6 M7 3v6 M16 9v6 M11 15v6',
    calendar: 'M4 5h16v16H4V5z M8 3v4 M16 3v4 M4 10h16 M8 14h2 M14 14h2 M8 18h2',
    shield: 'm12 3 9 4v5c0 5-9 10-9 10S3 17 3 12V7l9-4z m-4 9 3 3 5-6',
    spark: 'm12 3 2.4 6.6L21 12l-6.6 2.4L12 21l-2.4-6.6L3 12l6.6-2.4L12 3z',
    logout: 'M9 4H4v16h5 M9 12h12 m-5-5 5 5-5 5',
    truck: 'M2 5h12v12H2V5z M14 9h4l4 5v3h-8 M7 21a2 2 0 1 0 0-4 2 2 0 0 0 0 4 M18 21a2 2 0 1 0 0-4 2 2 0 0 0 0 4',
    code: 'm8 7-5 5 5 5 m8-10 5 5-5 5 M14 3l-4 18',
    help: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M9 8a3 3 0 1 1 4 3c-1 .5-1 1-1 3 M12 17h.01',
    menu: 'M4 6h16 M4 12h16 M4 18h16',
    leaf: 'M20 3C8 2 2 8 5 16s16 5 15-13z M4 21 16 9',
    info: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 11v6 M12 7h.01',
    edit: 'm15 4 5 5 M4 20l5-1L21 7l-5-5L4 14v6z',
    lock: 'M5 10h14v11H5V10z M8 10V6a4 4 0 0 1 8 0v4 M12 14v3',
  };
}
