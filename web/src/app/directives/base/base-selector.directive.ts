import { Directive, inject, input, model, OnInit } from '@angular/core';

import { CustomControlValueAccessorDirective } from '@app/directives/custom-control-value-accessor';
import { Selector } from '@app/models';
import { CRUD_SERVICE } from '@app/tokens';

@Directive()
export class BaseSelectorDirective
  extends CustomControlValueAccessorDirective
  implements OnInit
{
  orderBy = input('name');

  items = model<Selector[]>([]);
  protected service = inject(CRUD_SERVICE);

  override ngOnInit(): void {
    super.ngOnInit();
    this.findItens();
  }

  findItens() {
    this.service
      .findAll({
        orderBy: this.orderBy(),
        orderDirection: 'asc',
        pageSize: 20,
      })
      .subscribe({
        next: response => {
          this.items.set(response.data as Selector[]);
        },
      });
  }
}
