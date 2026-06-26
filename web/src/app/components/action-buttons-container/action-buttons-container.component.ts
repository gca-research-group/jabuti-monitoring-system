import { TranslateModule } from '@ngx-translate/core';

import { Component, HostBinding, Input } from '@angular/core';

@Component({
  selector: 'app-action-buttons-container',
  templateUrl: './action-buttons-container.component.html',
  styleUrl: './action-buttons-container.component.scss',
  imports: [TranslateModule],
})
export class ActionButtonsContainerComponent {
  @Input()
  @HostBinding('style.justify-content')
  justifyContent = 'center';
}
