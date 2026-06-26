import { TranslateModule } from '@ngx-translate/core';

import { Component, TemplateRef, viewChild } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { BlockchainPlatformSelectorComponent } from '@app/components/blockchain-platform-selector';
import { IconButtonComponent } from '@app/components/icon-button';
import { InputComponent } from '@app/components/input';
import { TableComponent } from '@app/components/table';
import { BaseListDirective } from '@app/directives/base';
import { Column, ColumnType, SmartContract } from '@app/models';
import { SmartContractService } from '@app/services/smart-contract';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';
import { ExpansionPanelComponent } from '@app/components/expansion-panel';

const COLUMNS: Column[] = [
  {
    id: 'id',
    label: 'id',
  },
  {
    id: 'name',
    label: 'name',
  },
  {
    id: 'createdAt',
    label: 'createdAt',
    rowType: ColumnType.DATETIME,
  },
  {
    id: 'updatedAt',
    label: 'updatedAt',
    rowType: ColumnType.DATETIME,
  },
  {
    id: 'actions',
    label: '',
    columnType: ColumnType.TEMPLATE,
    rowType: ColumnType.TEMPLATE,
    width: 150,
    sortable: false,
  },
];

@Component({
  selector: 'app-smart-contract-list',
  templateUrl: './list.component.html',
  styleUrl: './list.component.scss',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    RouterLink,

    TranslateModule,

    BlockchainPlatformSelectorComponent,
    ExpansionPanelComponent,
    IconButtonComponent,
    InputComponent,
    TableComponent,
  ],
  providers: [
    {
      provide: BREADCRUMB,
      useValue: [
        {
          label: 'home',
          url: '/',
        },
        {
          label: 'smart-contracts',
        },
      ],
    },
    {
      provide: CRUD_SERVICE,
      useClass: SmartContractService,
    },
  ],
})
export class ListComponent extends BaseListDirective<SmartContract> {
  columns = COLUMNS;

  displayedColumns = COLUMNS.map(column => column.id);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private actionsColumn = viewChild<TemplateRef<any>>('actionsColumn');
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private actionsRow = viewChild<TemplateRef<any>>('actionsRow');

  protected updateForm() {
    this.form.addControl('id', new FormControl());
    this.form.addControl('name', new FormControl());
    this.form.addControl('blockchainPlatform', new FormControl());
  }

  protected updateColumns() {
    this.columns = this.columns.map(column => {
      if (column.id !== 'actions') {
        return column;
      }

      return {
        ...column,
        templateRow: this.actionsRow(),
        templateColumn: this.actionsColumn(),
      };
    });
  }
}
