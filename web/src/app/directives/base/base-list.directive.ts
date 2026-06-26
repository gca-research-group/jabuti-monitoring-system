import { ToastrService } from 'ngx-toastr';
import { debounceTime } from 'rxjs';

import {
  Directive,
  AfterViewInit,
  OnDestroy,
  inject,
  ChangeDetectorRef,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Sort } from '@angular/material/sort';
import { ActivatedRoute, Router } from '@angular/router';

import { DeleteDialogComponent } from '@app/components/delete-dialog';
import { CrudService } from '@app/models';
import { BreadcrumbService } from '@app/services/breadcrumb';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';
import { removeEmptyKeys } from '@app/utils';

@Directive()
export abstract class BaseListDirective<T extends { id?: number|string }>
  implements AfterViewInit, OnDestroy
{
  protected breadcrumbService = inject(BreadcrumbService);
  protected service = inject<CrudService<T>>(CRUD_SERVICE);
  protected formBuilder = inject(FormBuilder);
  protected cdk = inject(ChangeDetectorRef);
  protected activatedRoute = inject(ActivatedRoute);
  protected router = inject(Router);
  protected readonly dialog = inject(MatDialog);
  protected toastr = inject(ToastrService);

  form!: FormGroup;
  @ViewChild('filters', {
    read: ElementRef,
  })
  formElement?: ElementRef<HTMLFormElement>;

  tableHeight!: string;

  protected breadCrumb = inject(BREADCRUMB);

  data: T[] = [];
  total = 0;

  loading = false;
  hasMore = true;

  constructor() {
    this.updateBreadcrumb();
    this.buildForm();
    this.updateForm();
    this.bindQueryParamToForm();
    this.listenFormChanges();
  }

  ngAfterViewInit(): void {
    this.updateColumns();
    this.updateTableSize();
    this.cdk.detectChanges();
    this.search();
  }

  ngOnDestroy(): void {
    this.breadcrumbService.reset();
  }

  private buildForm() {
    this.form = this.formBuilder.group({
      page: new FormControl(1),
      pageSize: new FormControl(20),
      orderBy: new FormControl(null),
      orderDirection: new FormControl(null),
    });
  }

  protected abstract updateForm(): void;
  protected abstract updateColumns(): void;

  private updateBreadcrumb() {
    this.breadcrumbService.update(this.breadCrumb);
  }

  protected bindQueryParamToForm() {
    const queryParams = this.activatedRoute.snapshot.queryParams;
    this.form.patchValue(queryParams as object, { emitEvent: false });
  }

  private listenFormChanges() {
    this.form.valueChanges.pipe(debounceTime(300)).subscribe(() => {
      this.form.patchValue({ page: 1 }, { emitEvent: false });
      this.search();
      void this.router.navigate([], {
        queryParams: this.form.value as Record<string, string>,
        queryParamsHandling: 'merge',
      });
    });
  }

  private updateTableSize() {
    const form = this.formElement?.nativeElement;

    this.tableHeight = `calc(100vh - var(--jms-toolbar-height) - (2 * var(--jms-content-vertical-padding)) - ${form?.offsetHeight}px - 16px)`;
  }

  openDialog(item: T): void {
    const dialogRef = this.dialog.open(DeleteDialogComponent, {
      data: item.id,
    });

    dialogRef.afterClosed().subscribe((id: number | undefined) => {
      if (id) {
        this.service.delete(id).subscribe({
          next: () => {
            this.data = this.data.filter(item => item.id !== id);
            this.toastr.success('DELETED_SUCCESSFULLY');
          },
          error: error => {
            console.log('[error]', error);
          },
        });
      }
    });
  }

  scroll() {
    if (this.hasMore) {
      this.form.patchValue(
        {
          page: +(this.form.get('page')?.value ?? 0) + 1,
        },
        { emitEvent: false },
      );
      this.findAll();
    }
  }

  findAll() {
    this.service
      .findAll(removeEmptyKeys(this.form.value as Record<string, FormControl>))
      .subscribe({
        next: response => {
          this.data = [...this.data, ...response.data];
          this.hasMore = response.hasMore;
          this.total = response.total;
        },
        error: error => {
          console.log('[error]', error);
        },
      });
  }

  search() {
    const _params = removeEmptyKeys<Record<string, string | number>>(
      this.form.value as Record<string, FormControl>,
    );
    _params['page'] = 1;

    this.service.findAll(_params).subscribe({
      next: response => {
        this.data = response.data;
        this.hasMore = response.hasMore;
        this.total = response.total;
      },
      error: error => {
        console.log('[error]', error);
      },
    });
  }

  sort(event: Sort) {
    this.form.patchValue({
      orderBy: event.active,
      orderDirection: event.direction,
      page: 1,
    });
  }
}
