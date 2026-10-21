import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { DefaultLayoutComponent } from './layout/default-layout.component';
import { LandingLayoutComponent } from './layout/landing-layout.component';

export const routes: Routes = [
  {
    path: '',
    component: LandingLayoutComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/public/landing/landing.component').then((m) => m.LandingComponent),
      },
      {
        path: 'public/install',
        loadComponent: () =>
          import('./features/public/install/install.component').then((m) => m.InstallComponent),
      },
      {
        path: 'public/impressum',
        loadComponent: () =>
          import('./features/public/impressum/impressum.component').then((m) => m.ImpressumComponent),
      },
      {
        path: 'public/datenschutz',
        loadComponent: () =>
          import('./features/public/datenschutz/datenschutz.component').then(
            (m) => m.DatenschutzComponent,
          ),
      },
    ],
  },
  {
    path: '',
    component: DefaultLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'agenda',
        loadComponent: () =>
          import('./features/agenda/agenda.component').then((m) => m.AgendaComponent),
      },
      {
        path: 'friends',
        loadComponent: () =>
          import('./features/friends/friends.component').then((m) => m.FriendsComponent),
      },
      {
        path: 'appointment/:id',
        loadComponent: () =>
          import('./features/appointment/appointment-page.component').then((m) => m.AppointmentPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
