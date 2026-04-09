import { NgSelectModule } from '@ng-select/ng-select';
import { TranslateModule } from '@ngx-translate/core';

import { Component } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { BaseSelectorDirective } from '@app/directives/base';
import { Selector } from '@app/models';
import { SmartContractService } from '@app/services/smart-contract';
import { CRUD_SERVICE } from '@app/tokens';

import { SelectComponent } from '../select';

@Component({
  selector: 'app-smart-contract-selector',
  templateUrl: './smart-contract-selector.component.html',
  styleUrl: './smart-contract-selector.component.scss',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    NgSelectModule,
    TranslateModule,
    SelectComponent,
  ],
  providers: [
    {
      provide: CRUD_SERVICE,
      useClass: SmartContractService,
    },
  ],
})
export class SmartContractSelectorComponent extends BaseSelectorDirective {
  override findItens() {
    this.service
      .findAll({
        orderBy: this.orderBy(),
        orderDirection: 'asc',
        pageSize: 1000,
      })
      .subscribe({
        next: response => {
          this.items.set(
            (
              response.data as {
                name: string;
                blockchainPlatform: string;
                id: string;
              }[]
            ).map(item => ({
              name: item.name,
              id: item.id,
              blockchainPlatform: item.blockchainPlatform,
            })) as Selector[],
          );

          this.formControl.updateValueAndValidity();
        },
      });
  }
}
