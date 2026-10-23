import { TestBed } from '@angular/core/testing';
import { NewFeaturesService } from './new-features.service';

describe('NewFeaturesService', () => {
  let service: NewFeaturesService;

  beforeEach(() => {
    localStorage.clear();
    service = TestBed.inject(NewFeaturesService);
  });

  it('liefert alle Neuigkeiten, solange keine gezeigt wurde', () => {
    expect(service.getUnshownFeatures().map((f) => f.id)).toEqual([
      'profile-page-v1',
      'linked-accounts-v1',
      'passkeys-v1',
      'change-password-v1',
    ]);
  });

  it('merkt sich gezeigte Neuigkeiten einmalig in shownFeatureIds', () => {
    service.markShown('profile-page-v1');
    service.markShown('profile-page-v1');
    expect(JSON.parse(localStorage.getItem('shownFeatureIds')!)).toEqual(['profile-page-v1']);
    expect(service.getUnshownFeatures().map((f) => f.id)).not.toContain('profile-page-v1');
  });

  it('markAllShown blendet alle aus', () => {
    service.markAllShown();
    expect(service.getUnshownFeatures()).toEqual([]);
  });

  it('ignoriert ungültige Einträge in localStorage', () => {
    localStorage.setItem('shownFeatureIds', '{kaputt');
    expect(service.getUnshownFeatures()).toHaveLength(4);
  });
});
