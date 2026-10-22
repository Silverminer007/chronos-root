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
    // Ohne Layout (Nuxt: `layout: false`), aber nur für angemeldete Nutzer.
    path: 'onboarding',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/onboarding/onboarding.component').then((m) => m.OnboardingComponent),
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
        path: 'groups',
        loadComponent: () => import('./features/groups/groups.component').then((m) => m.GroupsComponent),
      },
      {
        path: 'groups/:groupId',
        loadComponent: () =>
          import('./features/groups/group-detail.component').then((m) => m.GroupDetailComponent),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings.component').then((m) => m.SettingsComponent),
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/profile/profile.component').then((m) => m.ProfileComponent),
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
