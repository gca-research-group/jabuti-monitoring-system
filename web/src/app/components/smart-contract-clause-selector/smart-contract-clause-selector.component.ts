import { NgSelectModule } from '@ng-select/ng-select';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { Component, effect, input, OnDestroy, output } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { BaseSelectorDirective } from '@app/directives/base';
import {
  SmartContract,
  SmartContractClause,
  SmartContractClauseArgument,
} from '@app/models';
import { SmartContractService } from '@app/services/smart-contract';
import { CRUD_SERVICE } from '@app/tokens';

import { SelectComponent } from '../select';

@Component({
  selector: 'app-smart-contract-clause-selector',
  templateUrl: './smart-contract-clause-selector.component.html',
  styleUrl: './smart-contract-clause-selector.component.scss',
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
export class SmartContractClauseSelectorComponent
  extends BaseSelectorDirective
  implements OnDestroy
{
  smartContractId = input<string | null>();

  arguments = output<SmartContractClauseArgument[]>();

  private _items: SmartContractClause[] = [];
  private onDestroy$ = new Subject();

  constructor() {
    super();

    effect(() => {
      this.findItens();
    });
  }

  ngOnDestroy(): void {
    this.onDestroy$.complete();
    this.onDestroy$.unsubscribe();
  }

  override findItens() {
    if (!this.smartContractId()) {
      return;
    }

    this.formControl.valueChanges
      .pipe(takeUntil(this.onDestroy$))
      .subscribe(clauseName => {
        console.log('[clauseName]', clauseName);
        const clause = this._items.find(item => item.name === clauseName);
        if (clause?.clauseArguments?.length) {
          this.arguments.emit(clause?.clauseArguments);
        }
      });

    this.service.findById(this.smartContractId()!).subscribe({
      next: response => {
        this._items = (response as SmartContract).clauses ?? [];
        this.items.set(
          ((response as SmartContract).clauses ?? []).map(item => ({
            id: item.name,
            name: item.name,
          })),
        );
      },
    });
  }
}
