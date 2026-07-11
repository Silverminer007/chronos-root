import {defineStore} from 'pinia'
import type {NotificationSettings} from '~/types'

const defaultSettings: NotificationSettings = {
    appointment_moved: 'ALL',
    appointment_message: 'ALL',
    appointment_cancelled: 'ALL',
    appointment_participant_added: 'ALL',
    appointment_participation_status_changed: 'ALL',
    appointment_participation_invalid: 'ALL',
    appointment_participation_status_pending: 'ALL',
    appointment_reminder: 'ALL',
    group_member_added: 'ENABLED'
}

export const useSettingsStore = defineStore('settings', {
    state: () => ({
        settings: null as NotificationSettings | null,
        loading: false,
        saving: false,
        error: null as string | null
    }),

    getters: {
        hasSettings: (state) => state.settings !== null,

        getSetting: (state) => {
            return (key: keyof NotificationSettings) =>
                state.settings?.[key] ?? defaultSettings[key]
        }
    },

    actions: {
        async fetchSettings() {
            this.loading = true
            this.error = null

            try {
                const response = await $fetch<NotificationSettings>('/api/v2/settings')
                this.settings = response
            } catch (err) {
                if (getErrorStatus(err) === 404) {
                    this.settings = {...defaultSettings}
                } else {
                    this.error = getErrorMessage(err, 'Fehler beim Laden der Einstellungen')
                    throw err
                }
            } finally {
                this.loading = false
            }
        },

        async saveSettings() {
            if (!this.settings) return

            this.saving = true
            this.error = null

            try {
                const response = await $fetch<NotificationSettings>('/api/v2/settings', {
                    method: 'PUT',
                    body: this.settings
                })
                this.settings = response
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Speichern der Einstellungen')
                throw err
            } finally {
                this.saving = false
            }
        },

        updateSetting(key: keyof NotificationSettings, value: string) {
            if (this.settings) {
                (this.settings as Record<keyof NotificationSettings, string>)[key] = value
            }
        },

        clearError() {
            this.error = null
        }
    }
})
