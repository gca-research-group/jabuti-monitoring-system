import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { Component, effect, inject, input } from '@angular/core';
import { MatIcon, MatIconRegistry } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { DomSanitizer } from '@angular/platform-browser';

@Component({
  selector: 'app-icon',
  templateUrl: './icon.component.html',
  styleUrl: './icon.component.scss',
  imports: [MatIcon, MatTooltip, TranslateModule],
  host: {
    '[style.--icon-color]': 'color()',
    '[style.width]': 'width()',
  },
})
export class IconComponent {
  icon = input<string>();
  svg = input<string>();
  img = input<string>();
  filled = input<boolean>();
  tooltip = input<string>();

  color = input<string>();
  width = input<string>();

  iconRegistry = inject(MatIconRegistry);
  sanitizer = inject(DomSanitizer);

  private onDestroy$ = new Subject();

  constructor() {
    effect(() => {
      if (this.icon() && this.svg()) {
        this.iconRegistry
          .getNamedSvgIcon(this.icon()!)
          .pipe(takeUntil(this.onDestroy$))
          .subscribe({
            error: () => {
              this.iconRegistry.addSvgIcon(
                this.icon()!,
                this.sanitizer.bypassSecurityTrustResourceUrl(this.svg()!),
              );
            },
          });
      }
    });
  }
}
