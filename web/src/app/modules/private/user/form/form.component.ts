import { TranslateModule } from '@ngx-translate/core';
import { finalize } from 'rxjs';

import { Component } from '@angular/core';
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
import { User } from '@app/models';
import { UserService } from '@app/services/user';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';
import { removeEmptyKeys } from '@app/utils';
import { StatusSelectorComponent } from '@app/components/status-selector';
import { ImageUploaderComponent } from '@app/components/image-uploader';

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
    ImageUploaderComponent,
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
  User,
  {
    id: FormControl;
    name: FormControl;
    email: FormControl;
    photo: FormControl;
    password: FormControl;
    status: FormControl;
  }
> {
  preview: string | null = null;

  constructor() {
    super();
    this.buildForm();
    this.form.get('photo')?.valueChanges.subscribe((value: string) => {
      this.preview = value;
    });
  }

  protected buildForm() {
    this.form = this.formBuilder.group({
      id: null,
      name: new FormControl(null, Validators.required),
      email: new FormControl(null, Validators.required),
      photo: null,
      password: new FormControl(null),
      status: null,
    });
  }

  override save() {
    if (this.form.invalid) {
      this.toastr.warning('INVALID_FORM');
      return;
    }

    this.loading = true;
    this.service
      .save(removeEmptyKeys(this.form.value))
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
