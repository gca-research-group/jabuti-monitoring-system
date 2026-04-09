import { TranslateModule } from '@ngx-translate/core';
import { finalize, Subject, takeUntil } from 'rxjs';

import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import {
  FormArray,
  FormControl,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatExpansionModule } from '@angular/material/expansion';

import { BlockchainSelectorComponent } from '@app/components/blockchain-selector';
import { ButtonComponent } from '@app/components/button';
import { InputComponent } from '@app/components/input';
import { SmartContractClauseSelectorComponent } from '@app/components/smart-contract-clause-selector';
import { SmartContractSelectorComponent } from '@app/components/smart-contract-selector';
import { BaseFormDirective } from '@app/directives/base';
import {
  SmartContractClauseArgument,
  SmartContractExecution,
} from '@app/models';
import { SmartContractService } from '@app/services/smart-contract';
import { SmartContractExecutionService } from '@app/services/smart-contract-execution';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';
import { removeEmptyKeys } from '@app/utils';

@Component({
  selector: 'app-smart-contract-execution-form',
  templateUrl: './form.component.html',
  styleUrls: ['./../../form.base.scss', './form.component.scss'],
  host: { class: 'd-md-flex d-sm-block justify-content-center' },
  imports: [
    ReactiveFormsModule,
    FormsModule,

    MatExpansionModule,
    TranslateModule,

    InputComponent,
    ButtonComponent,
    BlockchainSelectorComponent,
    SmartContractSelectorComponent,
    SmartContractClauseSelectorComponent,
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
          label: 'smart-contract-execution',
          url: '/smart-contract-execution',
        },
      ],
    },
    {
      provide: CRUD_SERVICE,
      useClass: SmartContractExecutionService,
    },
  ],
})
export class FormComponent
  extends BaseFormDirective<
    SmartContractExecution,
    {
      id: FormControl<string | null>;
      blockchainId: FormControl<string | null>;
      smartContractId: FormControl<string | null>;
      clauseName: FormControl<string | null>;
      clauseArguments: FormArray;
    }
  >
  implements OnInit, OnDestroy
{
  protected override service =
    inject<SmartContractExecutionService>(CRUD_SERVICE);
  private smartContractService = inject(SmartContractService);
  smartContractId: string | null = null;

  private onDestroy$ = new Subject();

  get clauseArguments() {
    return this.form.get('clauseArguments') as FormArray;
  }

  override ngOnInit(): void {
    super.ngOnInit();

    this.form
      .get('blockchainId')
      ?.valueChanges.pipe(takeUntil(this.onDestroy$))
      .subscribe(value => {
        if (value) {
          this.form.get('smartContractId')?.enable();
        } else {
          this.form.get('smartContractId')?.disable();
        }
      });

    this.form
      .get('smartContractId')
      ?.valueChanges.pipe(takeUntil(this.onDestroy$))
      .subscribe(value => {
        this.smartContractId = value;

        if (value) {
          this.form.get('clauseName')?.enable();
        } else {
          this.form.get('clauseName')?.disable();
        }
      });
  }

  override ngOnDestroy(): void {
    super.ngOnDestroy();
    this.onDestroy$.complete();
    this.onDestroy$.unsubscribe();
  }

  protected override patchValue(item: SmartContractExecution) {
    this.form.patchValue({
      blockchainId: item.payload?.blockchain?.id,
      smartContractId: item.payload?.smartContract?.id,
      clauseName: item.payload?.clauseName,
    });

    for (const clauseArgument of item.payload?.clauseArguments ?? []) {
      this.clauseArguments?.push(
        this.formBuilder.group({
          name: clauseArgument.name,
          value: [clauseArgument.value, [Validators.required]],
        }),
      );
    }
  }

  protected override buildForm(): void {
    this.form = this.formBuilder.group({
      id: new FormControl(),
      blockchainId: new FormControl('', [Validators.required]),
      smartContractId: new FormControl('', [Validators.required]),
      clauseName: new FormControl('', [Validators.required]),
      clauseArguments: this.formBuilder.array([]),
    });

    this.form.get('smartContractId')?.disable();
    this.form.get('clauseName')?.disable();
  }

  protected override updateFormOnUpdateInitialization(): void {}

  execute() {
    if (this.form.invalid) {
      this.toastr.warning('INVALID_FORM');
      return;
    }

    this.loading = true;
    this.smartContractService
      .execute(removeEmptyKeys(this.form.value))
      .pipe(
        finalize(() => {
          this.loading = false;
        }),
      )
      .subscribe({
        next: () => {
          const message = 'RECORD_CREATED_SUCCESSFULLY';

          this.toastr.success(message);
          this.location.back();
        },
      });
  }

  setArguments(items: SmartContractClauseArgument[]) {
    this.clauseArguments.clear();

    for (const item of items) {
      this.clauseArguments?.push(
        this.formBuilder.group({
          name: item.name,
          value: [null, [Validators.required]],
        }),
      );
    }
  }
}
