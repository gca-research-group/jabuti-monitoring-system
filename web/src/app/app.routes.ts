import { Routes } from '@angular/router';

import { loginRoutes } from './modules/login';
import { privateRoutes } from './modules/private';

export const routes: Routes = [
  ...loginRoutes,
  ...privateRoutes,
];
