import { TranslateModule } from '@ngx-translate/core';

import { Component, TemplateRef, viewChild } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ActionButtonsContainerComponent } from '@app/components/action-buttons-container';
import { IconAnchorAddComponent } from '@app/components/icon-anchor-add';
import { IconAnchorEditComponent } from '@app/components/icon-anchor-edit';
import { IconButtonComponent } from '@app/components/icon-button';
import { IconButtonDeleteComponent } from '@app/components/icon-button-delete';
import { IconStatusComponent } from '@app/components/icon-status';
import { InputComponent } from '@app/components/input';
import { TableComponent } from '@app/components/table';
import { BaseListDirective } from '@app/directives/base';
import { Column, ColumnType, User } from '@app/models';
import { UserService } from '@app/services/user';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';
import { ExpansionPanelComponent } from '@app/components/expansion-panel';
import { StatusSelectorComponent } from '@app/components/status-selector';

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
    id: 'email',
    label: 'email',
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
  selector: 'app-user-list',
  templateUrl: './list.component.html',
  styleUrl: './list.component.scss',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    TableComponent,
    TranslateModule,
    ExpansionPanelComponent,
    IconButtonComponent,
    InputComponent,
    StatusSelectorComponent,
    IconStatusComponent,
    ActionButtonsContainerComponent,
    IconAnchorAddComponent,
    IconButtonDeleteComponent,
    IconAnchorEditComponent,
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
          label: 'user',
        },
      ],
    },
    {
      provide: CRUD_SERVICE,
      useClass: UserService,
    },
  ],
})
export class UserListComponent extends BaseListDirective<User> {
  columns = COLUMNS;

  displayedColumns = COLUMNS.map(column => column.id);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private actionsColumn = viewChild<TemplateRef<any>>('actionsColumn');
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private actionsRow = viewChild<TemplateRef<any>>('actionsRow');

  protected updateForm() {
    this.form.addControl('id', new FormControl());
    this.form.addControl('name', new FormControl());
    this.form.addControl('email', new FormControl());
    this.form.addControl('status', new FormControl());
  }

  protected updateColumns() {
    this.columns = this.columns.map(column => {
      if (column.id === 'actions') {
        return {
          ...column,
          templateRow: this.actionsRow(),
          templateColumn: this.actionsColumn(),
        };
      }

      return column;
    });
  }
}
