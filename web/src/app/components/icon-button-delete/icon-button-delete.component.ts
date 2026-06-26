import { TranslateModule } from '@ngx-translate/core';

import { Component } from '@angular/core';

import { IconButtonComponent } from '../icon-button';

@Component({
  selector: 'app-icon-button-delete',
  templateUrl: './icon-button-delete.component.html',
  styleUrl: './icon-button-delete.component.scss',
  imports: [TranslateModule, IconButtonComponent],
})
export class IconButtonDeleteComponent {}
