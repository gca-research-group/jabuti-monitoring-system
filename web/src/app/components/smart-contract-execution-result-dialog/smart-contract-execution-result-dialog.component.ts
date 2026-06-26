import { NgxJsonViewerModule } from 'ngx-json-viewer';
import { TranslateModule } from '@ngx-translate/core';

import { Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';

import { SmartContractExecution } from '@app/models';

@Component({
  selector: 'app-smart-contract-execution-result-dialog',
  templateUrl: './smart-contract-execution-result-dialog.component.html',
  styleUrl: './smart-contract-execution-result-dialog.component.scss',
  imports: [
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatButton,

    NgxJsonViewerModule,

    TranslateModule,
  ],
})
export class SmartContractExecutionResultDialogComponent {
  readonly dialogRef = inject(
    MatDialogRef<SmartContractExecutionResultDialogComponent>,
  );

  readonly data = inject<SmartContractExecution>(MAT_DIALOG_DATA);

  ok() {
    this.dialogRef.close();
  }
}
