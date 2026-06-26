import { TranslateModule } from '@ngx-translate/core';

import { Component, computed, input } from '@angular/core';

import { IconComponent } from '../icon';

@Component({
  selector: 'app-icon-status',
  template: `<app-icon
    icon="filled_circle"
    svg="/icons/filled_circle.svg"
    [tooltip]="selectedItem()?.label ?? '' | translate"
    [color]="selectedItem()?.color"
    style="width: 40px"
  />`,
  styles: [
    `
      :host {
        display: flex;
        justify-content: center;
        align-items: center;
      }
    `,
  ],
  imports: [IconComponent, TranslateModule],
})
export class IconStatusComponent {
  status = input.required<boolean | string | number>();

  items = input<
    { id: boolean | string | number; label: string; color: string }[]
  >([
    { id: true, label: 'active', color: 'green' },
    { id: false, label: 'inactive', color: 'red' },
  ]);

  selectedItem = computed(() => {
    return this.items().find(item => item.id === this.status());
  });
}
