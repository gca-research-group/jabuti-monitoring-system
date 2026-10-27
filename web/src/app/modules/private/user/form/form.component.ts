import { TranslateModule } from '@ngx-translate/core';
import { finalize } from 'rxjs';

import { Component, effect, inject } from '@angular/core';
import {
  FormControl,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTabsModule } from '@angular/material/tabs';

import { ButtonComponent } from '@app/components/button';
import { InputComponent } from '@app/components/input';
import { BaseFormDirective } from '@app/directives/base';
import {
  CreateUserRequest,
  UpdateUserRequest,
  UserResponse,
} from '@app/models';
import { UserService } from '@app/services/user';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';

import { StatusSelectorComponent } from '@app/components/status-selector';

@Component({
  selector: 'app-user-form',
  templateUrl: './form.component.html',
  styleUrls: ['./../../form.base.scss', './form.component.scss'],
  host: { class: 'd-md-flex d-sm-block justify-content-center' },
  imports: [
    ReactiveFormsModule,
    FormsModule,

    TranslateModule,
    MatSlideToggleModule,
    MatTabsModule,
    MatExpansionModule,

    ButtonComponent,

    InputComponent,
    StatusSelectorComponent,
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
          url: '/admin/user',
        },
      ],
    },
    {
      provide: CRUD_SERVICE,
      useClass: UserService,
    },
  ],
})
export class UserFormComponent extends BaseFormDirective<
  UserResponse,
  {
    id: FormControl;
    name: FormControl;
    email: FormControl;
    photo: FormControl;
    password: FormControl;
    status: FormControl;
    remarks: FormControl;
  }
> {
  private readonly userService = inject<UserService>(CRUD_SERVICE);

  constructor() {
    super();
    effect(() => {
      this.form.controls.password.setValidators(
        this.id() ? [] : [Validators.required],
      );
      this.form.controls.password.updateValueAndValidity({ emitEvent: false });
    });
    this.form.controls.photo.valueChanges.subscribe(() => {
      const photo = this.form.controls.photo;
      photo.setValidators(
        this.id() && !photo.dirty ? [] : [Validators.maxLength(255)],
      );
      photo.updateValueAndValidity({ emitEvent: false });
    });
  }

  protected buildForm() {
    this.form = this.formBuilder.group({
      id: null,
      name: new FormControl(null, Validators.required),
      email: new FormControl(null, [Validators.required, Validators.email]),
      photo: new FormControl(null, Validators.maxLength(255)),
      password: new FormControl(null),
      status: true,
      remarks: null,
    });
  }

  protected override patchValue(item: UserResponse) {
    super.patchValue(item);
    this.form.controls.password.reset(null);
    this.form.markAsPristine();
    this.form.controls.photo.clearValidators();
    this.form.controls.photo.updateValueAndValidity({ emitEvent: false });
  }

  override save() {
    if (this.form.invalid) {
      this.toastr.warning('INVALID_FORM');
      return;
    }

    const id = this.id();
    const values = this.form.getRawValue();
    const request = Object.fromEntries(
      Object.entries(values).filter(
        ([key]) =>
          key !== 'id' &&
          (!id ||
            this.form.controls[key as keyof typeof this.form.controls].dirty),
      ),
    );
    if (request['photo'] === '') request['photo'] = null;
    // An untouched edit password is omitted. A supplied empty password stays invalid.
    if (id && !this.form.controls.password.dirty) delete request['password'];
    if (id && this.form.controls.password.dirty && !values.password) {
      this.toastr.warning('INVALID_FORM');
      return;
    }
    const save = id
      ? this.userService.update(id, request as UpdateUserRequest)
      : this.userService.create(request as unknown as CreateUserRequest);
    this.loading = true;
    save
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
