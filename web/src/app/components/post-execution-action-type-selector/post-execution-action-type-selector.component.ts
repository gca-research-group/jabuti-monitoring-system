import { TranslateModule } from '@ngx-translate/core';

import { Component } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { CustomControlValueAccessorDirective } from '@app/directives/custom-control-value-accessor';

import { SelectComponent } from '../select';

@Component({
  selector: 'app-post-execution-action-type-selector',
  templateUrl: './post-execution-action-type-selector.component.html',
  styleUrl: './post-execution-action-type-selector.component.scss',
  imports: [FormsModule, ReactiveFormsModule, TranslateModule, SelectComponent],
})
export class PostExecutionActionSelectorComponent extends CustomControlValueAccessorDirective {
  items = [
    {
      id: 'WEBHOOK',
      name: 'webhook',
    },
    {
      id: 'EVENT',
      name: 'event',
    }
  ];
}
