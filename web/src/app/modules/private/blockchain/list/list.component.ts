import { TranslateModule } from '@ngx-translate/core';

import { Component, TemplateRef, viewChild } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { IconButtonComponent } from '@app/components/icon-button';
import { InputComponent } from '@app/components/input';
import { TableComponent } from '@app/components/table';
import { BaseListDirective } from '@app/directives/base';
import { Column, ColumnType, Blockchain } from '@app/models';
import { BlockchainService } from '@app/services/blockchain';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';

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
  selector: 'app-blockchain-list',
  templateUrl: './list.component.html',
  styleUrl: './list.component.scss',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    RouterLink,

    TranslateModule,

    TableComponent,
    InputComponent,
    IconButtonComponent,
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
          label: 'blockchain',
        },
      ],
    },
    {
      provide: CRUD_SERVICE,
      useClass: BlockchainService,
    },
  ],
})
export class ListComponent extends BaseListDirective<
  Blockchain,
  BlockchainService
> {
  columns = COLUMNS;

  displayedColumns = COLUMNS.map(column => column.id);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private actionsColumn = viewChild<TemplateRef<any>>('actionsColumn');
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private actionsRow = viewChild<TemplateRef<any>>('actionsRow');

  protected updateForm() {
    this.form.addControl('id', new FormControl());
    this.form.addControl('name', new FormControl());
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

  testConnection(id: string) {
    this.service.testConnection(id).subscribe({
      next: () => {
        this.toastr.success('THE_CONNECTION_IS_UP');
      },
    });
  }

  override getCurrentItemId(
    item: Blockchain,
  ): string | number | null | undefined {
    return item.id;
  }

  updateRouteQueryParameters() {
    void this.router.navigate([], {
      queryParams: this.form.value as Record<string, string>,
      queryParamsHandling: 'merge',
    });
  }
}
