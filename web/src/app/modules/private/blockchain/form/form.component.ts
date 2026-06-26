import { TranslateModule } from '@ngx-translate/core';
import { debounceTime } from 'rxjs';

import { Component, inject } from '@angular/core';
import {
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { BlockchainPlatformSelectorComponent } from '@app/components/blockchain-platform-selector';
import { ButtonComponent } from '@app/components/button';
import { InputComponent } from '@app/components/input';
import { TextAreaComponent } from '@app/components/textarea';
import { BaseFormDirective } from '@app/directives/base';
import { Blockchain } from '@app/models';
import { BlockchainService } from '@app/services/blockchain';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';

@Component({
  selector: 'app-blockchain-form',
  templateUrl: './form.component.html',
  styleUrls: ['./../../form.base.scss', './form.component.scss'],
  host: { class: 'd-md-flex d-sm-block justify-content-center' },
  imports: [
    ReactiveFormsModule,
    FormsModule,
    TranslateModule,

    InputComponent,
    ButtonComponent,
    BlockchainPlatformSelectorComponent,
    TextAreaComponent,
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
          url: '/blockchain',
        },
      ],
    },
    {
      provide: CRUD_SERVICE,
      useClass: BlockchainService,
    },
  ],
})
export class FormComponent extends BaseFormDirective<
  Blockchain,
  {
    id: FormControl<string | null>;
    name: FormControl<string | null>;
    platform: FormControl<string | null>;
    parameters: FormGroup;
  }
> {
  protected override service = inject<BlockchainService>(CRUD_SERVICE);
  parameters: { field: string; type: string; description: string }[] = [];

  parametersValues: Record<string, any> = {};

  constructor() {
    super();
    this.updateParameters();
  }

  protected override patchValue(item: Blockchain) {
    this.parametersValues = item.parameters;
    this.form.patchValue({
      ...item,
    });
  }

  protected override buildForm(): void {
    this.form = this.formBuilder.group({
      id: new FormControl(),
      name: new FormControl('', [Validators.required]),
      platform: new FormControl('HYPERLEDGER_FABRIC', [Validators.required]),
      parameters: this.formBuilder.group({}),
    });
  }

  private updateParameters() {
    this.form
      .get('platform')
      ?.valueChanges.pipe(debounceTime(300))
      .subscribe(value => {
        if (!value) {
          return;
        }

        this.service.config(value).subscribe({
          next: items => {
            const group = this.form.get('parameters') as FormGroup;
            Object.keys(group.value as object).map(key => {
              group.removeControl(key);
            });

            items?.map(item => {
              group.addControl(
                item.field,
                new FormControl('', [Validators.required]),
              );
            });

            this.parameters = items ?? [];
            this.form.get('parameters')?.patchValue(this.parametersValues);
          },
          error: (error: { message: string }) => {
            console.error('[error]', error);
          },
        });
      });
  }
}
