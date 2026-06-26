import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { CustomControlValueAccessorDirective } from '@app/directives/custom-control-value-accessor';

import { SelectComponent } from '../select';

@Component({
  selector: 'app-user-role-selector',
  templateUrl: './user-role-selector.component.html',
  styleUrl: './user-role-selector.component.scss',
  imports: [SelectComponent, ReactiveFormsModule],
})
export class UserRoleSelectorComponent extends CustomControlValueAccessorDirective {
  items = [
    { id: 'ADMIN', name: 'admin' },
    { id: 'USER', name: 'user' },
  ];
}
