import { NotificationSettings } from '../../core/models';

/** Auswahl einer Einstellung in den Benachrichtigungs-Karten. */
export interface SettingUpdate {
  key: keyof NotificationSettings;
  value: string;
}
