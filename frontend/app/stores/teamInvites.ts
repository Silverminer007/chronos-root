import {defineStore} from 'pinia'
import type {TeamInvite, TeamInviteType} from '~/types'

export const useTeamInvitesStore = defineStore('teamInvites', {
    state: () => ({
        invites: [] as TeamInvite[],
        loading: false,
        error: null as string | null
    }),

    getters: {
        activeInvites: (state) => state.invites.filter(i => i.status === 'ACTIVE')
    },

    actions: {
        async fetchInvites(teamId: number) {
            this.loading = true
            this.error = null
            try {
                const response = await $fetch<TeamInvite[]>(`/api/v2/teams/${teamId}/invites`)
                this.invites = response || []
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Laden der Einladungen')
                throw err
            } finally {
                this.loading = false
            }
        },

        async createInvite(teamId: number, payload: {
            type: TeamInviteType
            targetEmail?: string
            expiryHours?: number
        }) {
            try {
                const response = await $fetch<TeamInvite>(`/api/v2/teams/${teamId}/invites`, {
                    method: 'POST',
                    body: payload
                })
                if (response) this.invites.push(response)
                return response
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Erstellen der Einladung')
                throw err
            }
        },

        async revokeInvite(teamId: number, inviteId: number) {
            try {
                await $fetch(`/api/v2/teams/${teamId}/invites/${inviteId}`, {
                    method: 'DELETE'
                })
                this.invites = this.invites.filter(i => i.id !== inviteId)
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Widerrufen der Einladung')
                throw err
            }
        },

        clearInvites() {
            this.invites = []
            this.error = null
        }
    }
})
