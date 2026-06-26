import { TranslateService } from '@ngx-translate/core';
import { ToastrService } from 'ngx-toastr';
import { finalize } from 'rxjs';

import { Location } from '@angular/common';
import {
  computed,
  Directive,
  effect,
  inject,
  input,
  OnDestroy,
} from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import { CrudService } from '@app/models';
import { BreadcrumbService } from '@app/services/breadcrumb';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';
import { removeEmptyKeys } from '@app/utils';

@Directive()
export abstract class BaseFormDirective<
  T extends object,
  R extends Record<string, FormControl | FormGroup | FormArray>,
> implements OnDestroy {
  protected formBuilder = inject(FormBuilder);
  protected breadcrumbService = inject(BreadcrumbService);
  protected service = inject<CrudService<T>>(CRUD_SERVICE);
  protected location = inject(Location);
  protected activatedRoute = inject(ActivatedRoute);
  protected toastr = inject(ToastrService);

  protected breadCrumbs = inject(BREADCRUMB);

  protected translateService = inject(TranslateService);

  id = input<number>();
  protected readonly resource = this.service.findByIdResource(this.id);
  item = computed(() => this.resource.value());

  form!: FormGroup<R>;
  loading = false;

  constructor() {
    effect(() => {
      if (this.id()) {
        this.breadcrumbService.update([...this.breadCrumbs, { label: 'edit' }]);
      } else {
        this.breadcrumbService.update([...this.breadCrumbs, { label: 'add' }]);
      }
    });

    this.buildForm();

    effect(() => {
      const item = this.item();

      if (item) {
        this.patchValue(item);
      }
    });
  }

  ngOnDestroy(): void {
    this.breadcrumbService.reset();
  }

  protected abstract buildForm(): void;

  protected patchValue(item: T) {
    this.form.patchValue({
      ...item,
    });
}

  save() {
    if (this.form.invalid) {
      this.toastr.warning('INVALID_FORM');
      return;
    }

    this.loading = true;
    this.service
      .save(removeEmptyKeys(this.form.getRawValue()))
      .pipe(
        finalize(() => {
          this.loading = false;
        }),
      )
      .subscribe({
        next: () => {
          const message = this.form.value['id']
            ? 'RECORD_UPDATED_SUCCESSFULLY'
            : 'RECORD_CREATED_SUCCESSFULLY';

          this.toastr.success(this.translateService.instant(message) as string);
          this.location.back();
        },
      });
  }
}
