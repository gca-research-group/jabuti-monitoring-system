import { Routes } from '@angular/router';


export const userRoutes: Routes = [
  {
    path: 'user',
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./list/list.component').then(m => m.UserListComponent),
      },
      {
        path: 'add',
        loadComponent: () =>
          import('./form/form.component').then(m => m.UserFormComponent),
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./form/form.component').then(m => m.UserFormComponent),
      },
    ],
  },
];
