import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { CustomControlValueAccessorDirective } from '@app/directives/custom-control-value-accessor';

import { SelectComponent } from '../select';

@Component({
  selector: 'app-status-selector',
  templateUrl: './status-selector.component.html',
  styleUrl: './status-selector.component.scss',
  imports: [SelectComponent, ReactiveFormsModule],
})
export class StatusSelectorComponent extends CustomControlValueAccessorDirective {
  items = [
    { id: '1', name: 'active' },
    { id: '0', name: 'inactive' },
  ];
}
