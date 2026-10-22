import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationSettings } from '../../core/models';
import { NotificationAppointmentSettingsCardComponent } from './notification-appointment-settings-card.component';
import { NotificationGroupSettingsCardComponent } from './notification-group-settings-card.component';
import { NotificationPushCardComponent } from './notification-push-card.component';
import { SettingUpdate } from './setting-update';

const settings: NotificationSettings = {
  appointment_moved: 'ALL',
  appointment_message: 'DISABLED',
  appointment_cancelled: 'ALL',
  appointment_participant_added: 'ALL',
  appointment_participation_status_changed: 'ALL',
  appointment_participation_invalid: 'ALL',
  appointment_participation_status_pending: 'ALL',
  appointment_reminder: 'RESPONSIBLE',
  group_member_added: 'ENABLED',
};

@Component({
  imports: [NotificationPushCardComponent],
  template: `<app-notification-push-card [enabled]="enabled()" [permission]="permission()" (toggled)="toggledCount = toggledCount + 1" />`,
})
class PushHost {
  enabled = signal(false);
  permission = signal<NotificationPermission>('default');
  toggledCount = 0;
}

describe('NotificationPushCardComponent', () => {
  let fixture: ComponentFixture<PushHost>;
  const el = () => fixture.nativeElement as HTMLElement;
  const input = () => el().querySelector('input') as HTMLInputElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(PushHost);
    fixture.detectChanges();
  });

  it('zeigt „Deaktiviert“ und „Aktiviert“ je nach Zustand', () => {
    expect(el().textContent).toContain('Deaktiviert');
    expect(input().checked).toBe(false);
    fixture.componentInstance.enabled.set(true);
    fixture.detectChanges();
    expect(el().textContent).toContain('Aktiviert');
    expect(input().checked).toBe(true);
  });

  it('meldet den Klick auf den Schalter', () => {
    input().click();
    expect(fixture.componentInstance.toggledCount).toBe(1);
  });

  it('sperrt den Schalter und erklärt die Blockade, wenn der Browser Push verweigert', () => {
    fixture.componentInstance.permission.set('denied');
    fixture.detectChanges();
    expect(input().disabled).toBe(true);
    expect(el().textContent).toContain('Im Browser blockiert');
    expect(el().textContent).toContain('Push-Benachrichtigungen wurden im Browser blockiert.');
  });
});

describe('NotificationAppointmentSettingsCardComponent', () => {
  let fixture: ComponentFixture<NotificationAppointmentSettingsCardComponent>;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(NotificationAppointmentSettingsCardComponent);
    fixture.componentRef.setInput('settings', settings);
    fixture.detectChanges();
  });

  it('listet die acht Benachrichtigungen mit je fünf Rollen', () => {
    expect(el().querySelectorAll('li')).toHaveLength(8);
    expect(el().querySelectorAll('li:first-child button')).toHaveLength(5);
    expect(el().textContent).toContain('Termin verschoben');
    expect(el().textContent).toContain('Terminerinnerung');
  });

  it('markiert die gewählte Rolle', () => {
    const pressed = (index: number) =>
      Array.from(el().querySelectorAll(`li:nth-child(${index}) button`)).filter((b) => b.getAttribute('aria-pressed') === 'true').map((b) => b.getAttribute('aria-label'));
    expect(pressed(1)).toEqual(['Alle']);
    expect(pressed(2)).toEqual(['Aus']);
    expect(pressed(8)).toEqual(['Orga.']);
  });

  it('meldet Schlüssel und Wert der Auswahl', () => {
    const updates: SettingUpdate[] = [];
    fixture.componentInstance.update.subscribe((u) => updates.push(u));
    (el().querySelector('li:nth-child(3) button[aria-label="Helfer"]') as HTMLElement).click();
    expect(updates).toEqual([{ key: 'appointment_cancelled', value: 'HELPER' }]);
  });
});

describe('NotificationGroupSettingsCardComponent', () => {
  let fixture: ComponentFixture<NotificationGroupSettingsCardComponent>;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(NotificationGroupSettingsCardComponent);
    fixture.componentRef.setInput('settings', settings);
    fixture.detectChanges();
  });

  it('markiert „An“ bei ENABLED', () => {
    const buttons = Array.from(el().querySelectorAll('button'));
    expect(buttons.map((b) => [b.textContent?.trim(), b.getAttribute('aria-pressed')])).toEqual([
      ['Aus', 'false'],
      ['An', 'true'],
    ]);
  });

  it('meldet Schlüssel und Wert der Auswahl', () => {
    const updates: SettingUpdate[] = [];
    fixture.componentInstance.update.subscribe((u) => updates.push(u));
    (el().querySelector('button') as HTMLElement).click();
    expect(updates).toEqual([{ key: 'group_member_added', value: 'DISABLED' }]);
  });
});
