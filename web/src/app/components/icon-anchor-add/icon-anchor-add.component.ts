import { TranslateModule } from '@ngx-translate/core';

import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

import { IconButtonAddComponent } from '../icon-button-add';

@Component({
  selector: 'app-icon-anchor-add',
  templateUrl: './icon-anchor-add.component.html',
  styleUrl: './icon-anchor-add.component.scss',
  imports: [TranslateModule, RouterModule, IconButtonAddComponent],
})
export class IconAnchorAddComponent {}
