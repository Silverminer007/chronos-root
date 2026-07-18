import {defineStore} from 'pinia'
import type {Team, TeamRole} from '~/types'

export const useTeamsStore = defineStore('teams', {
    state: () => ({
        teams: [] as Team[],
        currentTeam: null as Team | null,
        loading: false,
        error: null as string | null
    }),

    getters: {
        teamsCount: (state) => state.teams.length,
        getTeamById: (state) => (id: number) => state.teams.find(t => t.id === id)
    },

    actions: {
        async fetchMyTeams() {
            this.loading = true
            this.error = null
            try {
                const response = await $fetch<Team[]>('/api/v2/teams')
                this.teams = response || []
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Laden der Teams')
                throw err
            } finally {
                this.loading = false
            }
        },

        async fetchTeam(teamId: number) {
            this.loading = true
            this.error = null
            try {
                const response = await $fetch<Team>(`/api/v2/teams/${teamId}`)
                this.currentTeam = response
                const index = this.teams.findIndex(t => t.id === teamId)
                if (index !== -1) this.teams[index] = response
                return response
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Laden des Teams')
                throw err
            } finally {
                this.loading = false
            }
        },

        async createTeam(name: string) {
            try {
                const response = await $fetch<Team>('/api/v2/teams', {
                    method: 'POST',
                    body: {name}
                })
                if (response) this.teams.push(response)
                return response
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Erstellen des Teams')
                throw err
            }
        },

        async updateMemberRole(teamId: number, targetOidcId: string, role: TeamRole) {
            try {
                await $fetch(`/api/v2/teams/${teamId}/members/${targetOidcId}/role`, {
                    method: 'PUT',
                    body: {role}
                })
                await this.fetchTeam(teamId)
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Ändern der Rolle')
                throw err
            }
        },

        async removeMember(teamId: number, targetOidcId: string) {
            try {
                await $fetch(`/api/v2/teams/${teamId}/members/${targetOidcId}`, {
                    method: 'DELETE'
                })
                await this.fetchTeam(teamId)
            } catch (err) {
                this.error = getErrorMessage(err, 'Mitglied konnte nicht entfernt werden')
                throw err
            }
        },

        async renameTeam(teamId: number, name: string) {
            try {
                await $fetch(`/api/v2/teams/${teamId}`, {
                    method: 'PATCH',
                    body: {name}
                })
                await this.fetchTeam(teamId)
            } catch (err) {
                this.error = getErrorMessage(err, 'Teamname konnte nicht geändert werden')
                throw err
            }
        },

        async transferOwnership(teamId: number, targetOidcId: string) {
            try {
                await $fetch(`/api/v2/teams/${teamId}/members/${targetOidcId}/transfer-ownership`, {
                    method: 'POST'
                })
                await this.fetchTeam(teamId)
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Übertragen der Eigentümerschaft')
                throw err
            }
        },

        clearCurrentTeam() {
            this.currentTeam = null
        },

        clearError() {
            this.error = null
        }
    }
})
