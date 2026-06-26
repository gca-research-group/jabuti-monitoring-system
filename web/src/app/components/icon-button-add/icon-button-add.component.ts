import { TranslateModule } from '@ngx-translate/core';

import { Component } from '@angular/core';

import { IconButtonComponent } from '../icon-button';

@Component({
  selector: 'app-icon-button-add',
  templateUrl: './icon-button-add.component.html',
  styleUrl: './icon-button-add.component.scss',
  imports: [TranslateModule, IconButtonComponent],
})
export class IconButtonAddComponent {}
