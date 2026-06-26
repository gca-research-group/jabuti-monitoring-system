import { TranslateModule } from '@ngx-translate/core';

import { Component, inject, input } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { IS_MOBILE } from '@app/tokens';

@Component({
  selector: 'app-expansion-panel',
  templateUrl: './expansion-panel.component.html',
  styleUrl: './expansion-panel.component.scss',
  imports: [
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule,
  ],
})
export class ExpansionPanelComponent {
  readonly title = input<string>();
  isMobile = inject(IS_MOBILE);
}
