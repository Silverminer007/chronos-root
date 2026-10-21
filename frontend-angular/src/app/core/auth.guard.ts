import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { OnboardingService } from './onboarding.service';

const isPublic = (path: string) => path === '/' || path.startsWith('/public');

/** Port von `middleware/auth.global.ts`: Sitzung prüfen, dann Onboarding bzw. Profil, `/` → `/agenda`. */
export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const onboarding = inject(OnboardingService);
  const router = inject(Router);
  const path = state.url.split(/[?#]/)[0];

  if (!(await auth.checkSession())) {
    return isPublic(path) ? true : router.parseUrl('/');
  }
  if (!auth.user()) {
    await auth.fetchUser();
  }
  if ((onboarding.shouldShow() || !auth.isProfileComplete()) && path !== '/onboarding') {
    return router.parseUrl('/onboarding');
  }
  if (path === '/') {
    return router.parseUrl('/agenda');
  }
  return true;
};
