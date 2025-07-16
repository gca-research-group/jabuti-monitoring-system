import { TranslateModule } from '@ngx-translate/core';

import { Component, inject, OnInit } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { CustomControlValueAccessorDirective } from '@app/directives/custom-control-value-accessor';
import { BlockchainPlatform } from '@app/models';
import { BlockchainService } from '@app/services/blockchain';

import { SelectComponent } from '../select';

@Component({
  selector: 'app-blockchain-platform-selector',
  templateUrl: './blockchain-platform-selector.component.html',
  styleUrl: './blockchain-platform-selector.component.scss',
  imports: [FormsModule, ReactiveFormsModule, TranslateModule, SelectComponent],
})
export class BlockchainPlatformSelectorComponent
  extends CustomControlValueAccessorDirective
  implements OnInit
{
  items: BlockchainPlatform[] = [];

  service = inject(BlockchainService);

  override ngOnInit(): void {
    super.ngOnInit();
    this.findItems();
  }

  findItems() {
    this.service
      .platforms({
        orderDirection: 'asc',
        pageSize: 1000,
      })
      .subscribe({
        next: items => {
          this.items = items;
          this.formControl.updateValueAndValidity();
        },
      });
  }
}
