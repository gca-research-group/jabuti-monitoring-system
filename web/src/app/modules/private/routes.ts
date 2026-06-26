import { Routes } from '@angular/router';

import { isAuthenticatedGuard } from '@app/guards';

import { blockchainRoutes } from './blockchain';
import { smartContractRoutes } from './smart-contract';
import { SmartContractExecutionRoutes } from './smart-contract-execution';
import { WrapperComponent } from './wrapper';
import { userRoutes } from './user';

export const privateRoutes: Routes = [
  {
    path: '',
    component: WrapperComponent,
    children: [
      ...blockchainRoutes,
      ...SmartContractExecutionRoutes,
      ...smartContractRoutes,
      ...userRoutes,
    ],
    canActivateChild: [isAuthenticatedGuard],
  },
];
