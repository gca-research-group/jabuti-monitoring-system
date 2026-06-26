import { TranslateModule } from '@ngx-translate/core';

import { Component, input } from '@angular/core';
import { RouterModule } from '@angular/router';

import { IconButtonComponent } from '../icon-button';

@Component({
  selector: 'app-icon-anchor-edit',
  templateUrl: './icon-anchor-edit.component.html',
  styleUrl: './icon-anchor-edit.component.scss',
  imports: [TranslateModule, RouterModule, IconButtonComponent, RouterModule],
})
export class IconAnchorEditComponent {
  id = input('');
}
