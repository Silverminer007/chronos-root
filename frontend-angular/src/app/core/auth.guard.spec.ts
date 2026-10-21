import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';
import { OnboardingService } from './onboarding.service';

describe('authGuard', () => {
  const user = signal<object | null>(null);
  let checkSession: ReturnType<typeof vi.fn>;
  let fetchUser: ReturnType<typeof vi.fn>;
  let isProfileComplete: ReturnType<typeof vi.fn>;
  let shouldShow: ReturnType<typeof vi.fn>;

  const run = (url: string) =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as Promise<boolean | UrlTree>;

  const urlOf = (result: boolean | UrlTree) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  beforeEach(() => {
    user.set(null);
    checkSession = vi.fn().mockResolvedValue(true);
    fetchUser = vi.fn().mockResolvedValue(undefined);
    isProfileComplete = vi.fn().mockReturnValue(true);
    shouldShow = vi.fn().mockReturnValue(false);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { checkSession, fetchUser, isProfileComplete, user } },
        { provide: OnboardingService, useValue: { shouldShow } },
      ],
    });
  });

  it('leitet nicht angemeldete Benutzer auf / um', async () => {
    checkSession.mockResolvedValue(false);
    expect(urlOf(await run('/agenda'))).toBe('/');
  });

  it('lässt nicht angemeldete Benutzer auf / und /public/* durch', async () => {
    checkSession.mockResolvedValue(false);
    expect(await run('/')).toBe(true);
    expect(await run('/public/impressum')).toBe(true);
  });

  it('lädt den Benutzer, falls noch nicht vorhanden', async () => {
    await run('/agenda');
    expect(fetchUser).toHaveBeenCalledOnce();
  });

  it('lädt den Benutzer nicht erneut, wenn er vorhanden ist', async () => {
    user.set({ id: 1 });
    await run('/agenda');
    expect(fetchUser).not.toHaveBeenCalled();
  });

  it('leitet auf /onboarding, wenn das Onboarding noch nicht gezeigt wurde', async () => {
    shouldShow.mockReturnValue(true);
    expect(urlOf(await run('/agenda'))).toBe('/onboarding');
  });

  it('leitet auf /onboarding, wenn das Profil unvollständig ist', async () => {
    user.set({ id: 1 });
    isProfileComplete.mockReturnValue(false);
    expect(urlOf(await run('/agenda'))).toBe('/onboarding');
  });

  it('leitet nicht auf /onboarding, wenn der Benutzer nicht geladen werden konnte', async () => {
    isProfileComplete.mockReturnValue(false);
    expect(await run('/agenda')).toBe(true);
    expect(fetchUser).toHaveBeenCalledOnce();
  });

  it('lässt /onboarding selbst durch', async () => {
    shouldShow.mockReturnValue(true);
    expect(await run('/onboarding')).toBe(true);
  });

  it('leitet angemeldete Benutzer von / auf /agenda', async () => {
    expect(urlOf(await run('/'))).toBe('/agenda');
  });

  it('lässt angemeldete Benutzer mit vollständigem Profil durch', async () => {
    expect(await run('/agenda')).toBe(true);
  });
});
