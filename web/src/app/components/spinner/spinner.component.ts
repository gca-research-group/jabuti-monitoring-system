import { Component, input } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-spinner',
  template: `<mat-spinner [diameter]="size()" />`,
  imports: [MatProgressSpinnerModule],
})
export class SpinnerComponent {
  size = input<number>();
}
