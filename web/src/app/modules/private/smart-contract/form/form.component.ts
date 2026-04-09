import { TranslateModule } from '@ngx-translate/core';

import { Component, inject } from '@angular/core';
import {
  FormArray,
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';

import { BlockchainPlatformSelectorComponent } from '@app/components/blockchain-platform-selector';
import { ButtonComponent } from '@app/components/button';
import { DeleteDialogComponent } from '@app/components/delete-dialog';
import { IconButtonComponent } from '@app/components/icon-button';
import { InputComponent } from '@app/components/input';
import { BaseFormDirective } from '@app/directives/base';
import { SmartContract } from '@app/models';
import { SmartContractService } from '@app/services/smart-contract';
import { BREADCRUMB, CRUD_SERVICE } from '@app/tokens';

@Component({
  selector: 'app-smart-contract-form',
  templateUrl: './form.component.html',
  styleUrls: ['./../../form.base.scss', './form.component.scss'],
  host: { class: 'd-md-flex d-sm-block justify-content-center' },
  imports: [
    ReactiveFormsModule,
    FormsModule,

    InputComponent,
    ButtonComponent,
    IconButtonComponent,
    BlockchainPlatformSelectorComponent,

    MatExpansionModule,
    TranslateModule,
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
          url: '/smart-contract',
        },
      ],
    },
    {
      provide: CRUD_SERVICE,
      useClass: SmartContractService,
    },
  ],
})
export class FormComponent extends BaseFormDirective<
  SmartContract,
  {
    id: FormControl<string | null>;
    name: FormControl<string | null>;
    blockchainPlatform: FormControl<string | null>;
    clauses: FormArray;
    status: FormControl;
  }
> {
  readonly dialog = inject(MatDialog);

  get clauses() {
    return this.form.get('clauses') as FormArray;
  }

  protected override buildForm(): void {
    this.form = this.formBuilder.group({
      id: new FormControl(),
      name: new FormControl('', [Validators.required]),
      blockchainPlatform: new FormControl('HYPERLEDGER_FABRIC', [
        Validators.required,
      ]),
      clauses: this.formBuilder.array([]),
      status: new FormControl(true)
    });
  }

  protected override updateFormOnUpdateInitialization(): void {}

  override find(id: string) {
    this.service.findById(id).subscribe({
      next: item => {
        for (const clause of item.clauses ?? []) {
          this.addClause(false);
          const index = this.clauses.length - 1;

          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          for (const argument of clause.clauseArguments ?? []) {
            this.addClauseArgument(index);
          }
        }

        this.form.patchValue({
          ...item,
        });
      },
    });
  }

  addClause(addClauseArgument = true) {
    this.clauses.push(
      this.formBuilder.group({
        name: [null, [Validators.required]],
        clauseArguments: this.formBuilder.array([]),
      }),
    );

    if (addClauseArgument) {
      const index = this.clauses.length - 1;
      this.addClauseArgument(index);
    }
  }

  addClauseArgument(index: number) {
    const _clauseArguments = this.clauses
      .at(index)
      .get('clauseArguments') as FormArray;

    _clauseArguments.push(
      this.formBuilder.group({
        name: [null, [Validators.required]],
      }),
    );
  }

  removeClause(index: number) {
    const clause = this.clauses.at(index);
    const _clauseArguments = clause.get('clauseArguments') as FormArray<
      FormGroup<{
        name: FormControl<string | null>;
      }>
    >;

    if (!!clause.get('name')?.value || _clauseArguments.value.length) {
      const dialogRef = this.dialog.open(DeleteDialogComponent, {
        data: true,
      });

      dialogRef.afterClosed().subscribe((value: boolean) => {
        if (value) {
          this.clauses.removeAt(index);
        }
      });

      return;
    }

    this.clauses.removeAt(index);
  }

  removeClauseArgument(clauseIndex: number, argumentIndex: number) {
    const _clauseArguments = this.clauses
      .at(clauseIndex)
      .get('clauseArguments') as FormArray<
      FormGroup<{
        name: FormControl<string | null>;
      }>
    >;

    const _clauseArgument = _clauseArguments.at(argumentIndex);

    if (
      !!_clauseArgument.get('name')?.value
    ) {
      const dialogRef = this.dialog.open(DeleteDialogComponent, {
        data: true,
      });

      dialogRef.afterClosed().subscribe((value: boolean) => {
        if (value) {
          _clauseArguments.removeAt(argumentIndex);
        }
      });

      return;
    }

    _clauseArguments.removeAt(argumentIndex);
  }
}
