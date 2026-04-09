import { NgSelectModule } from '@ng-select/ng-select';
import { TranslateModule } from '@ngx-translate/core';

import { Component } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { BaseSelectorDirective } from '@app/directives/base';
import { Selector } from '@app/models';
import { BlockchainService } from '@app/services/blockchain';
import { CRUD_SERVICE } from '@app/tokens';

import { SelectComponent } from '../select';

@Component({
  selector: 'app-blockchain-selector',
  templateUrl: './blockchain-selector.component.html',
  styleUrl: './blockchain-selector.component.scss',
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
      useClass: BlockchainService,
    },
  ],
})
export class BlockchainSelectorComponent extends BaseSelectorDirective {
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
                platform: string;
                id: string;
              }[]
            ).map(item => ({
              name: item.name,
              id: item.id,
              platform: item.platform,
            })) as Selector[],
          );

          this.formControl.updateValueAndValidity();
        },
      });
  }
}
