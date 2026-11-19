import { TestBed } from '@angular/core/testing';
import { AppointmentCardSkeletonComponent } from './appointment-card-skeleton.component';

describe('AppointmentCardSkeletonComponent', () => {
  it('rendert Platzhalter für Titel, vier Avatare und Schaltflächen und ist für Screenreader verborgen', () => {
    const fixture = TestBed.createComponent(AppointmentCardSkeletonComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.skeleton-avatar')).toHaveLength(4);
    expect(el.querySelectorAll('.skeleton').length).toBeGreaterThan(8);
    expect(el.querySelector('.card')?.getAttribute('aria-hidden')).toBe('true');
  });
});
