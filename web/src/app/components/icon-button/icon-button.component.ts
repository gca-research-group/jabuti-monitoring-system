import { TranslateModule } from '@ngx-translate/core';

import { Component, input } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip, TooltipPosition } from '@angular/material/tooltip';

import { IconComponent } from '../icon/icon.component';
import { SpinnerComponent } from '../spinner';

@Component({
  selector: 'app-icon-button',
  templateUrl: './icon-button.component.html',
  styleUrl: './icon-button.component.scss',
  imports: [
    MatIconButton,
    MatTooltip,
    TranslateModule,
    IconComponent,
    SpinnerComponent,
  ],
})
export class IconButtonComponent {
  ariaLabel = input<string>();
  icon = input<string>();
  svg = input<string>();
  img = input<string>();
  tooltip = input<string>('');
  disabled = input<boolean>();
  color = input<string>();
  tooltipPosition = input<TooltipPosition>('below');
  isLoading = input<boolean>(false);
}
