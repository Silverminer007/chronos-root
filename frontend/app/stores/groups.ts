import {defineStore} from 'pinia'
import type {Group, User} from '~/types'

export const useGroupsStore = defineStore('groups', {
    state: () => ({
        groups: [] as Group[],
        currentGroup: null as Group | null,
        loading: false,
        error: null as string | null
    }),

    getters: {
        groupsCount: (state) => state.groups.length,

        getGroupById: (state) => {
            return (id: number) => state.groups.find(g => g.id === id)
        },

        currentGroupMemberCount: (state) => state.currentGroup?.members?.length || 0
    },

    actions: {
        async fetchGroups() {
            this.loading = true
            this.error = null

            try {
                const response = await $fetch<Group[]>('/api/v2/groups')
                this.groups = response || []
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Laden der Gruppen')
                throw err
            } finally {
                this.loading = false
            }
        },

        async fetchGroup(groupId: number) {
            this.loading = true
            this.error = null

            try {
                const members = await $fetch<User[]>(`/api/v2/groups/${groupId}/users`)
                const group = this.groups.find(g => g.id === groupId)

                if (group) {
                    this.currentGroup = {...group, members: members || []}
                } else {
                    // If not in list, fetch all groups first
                    await this.fetchGroups()
                    const freshGroup = this.groups.find(g => g.id === groupId)
                    if (freshGroup) {
                        this.currentGroup = {...freshGroup, members: members || []}
                    }
                }

                // Update the group in the list as well
                const index = this.groups.findIndex(g => g.id === groupId)
                if (index !== -1 && this.currentGroup) {
                    this.groups[index] = this.currentGroup
                }

                return this.currentGroup
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Laden der Gruppe')
                throw err
            } finally {
                this.loading = false
            }
        },

        async createGroup(name: string) {
            try {
                const response = await $fetch<Group>('/api/v2/groups', {
                    method: 'POST',
                    body: {name}
                })

                if (response) {
                    this.groups.push(response)
                }

                return response
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Erstellen der Gruppe')
                throw err
            }
        },

        async deleteGroup(groupId: number) {
            try {
                await $fetch(`/api/v2/groups/${groupId}`, {
                    method: 'DELETE'
                })

                this.groups = this.groups.filter(g => g.id !== groupId)
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Löschen der Gruppe')
                throw err
            }
        },

        async addMember(groupId: number, userId: number) {
            try {
                await $fetch(`/api/v2/groups/${groupId}/user/${userId}`, {
                    method: 'POST'
                })

                // Refresh the current group if we're viewing it
                if (this.currentGroup?.id === groupId) {
                    await this.fetchGroup(groupId)
                } else {
                    await this.fetchGroups()
                }
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Hinzufügen des Mitglieds')
                throw err
            }
        },

        async removeMember(groupId: number, userId: number) {
            try {
                await $fetch(`/api/v2/groups/${groupId}/user/${userId}`, {
                    method: 'DELETE'
                })

                // Refresh the current group if we're viewing it
                if (this.currentGroup?.id === groupId) {
                    await this.fetchGroup(groupId)
                } else {
                    await this.fetchGroups()
                }
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Entfernen des Mitglieds')
                throw err
            }
        },

        async leaveGroup(groupId: number, userId: number) {
            // This is essentially removeMember but for the current user
            // The calling code should handle navigation after this
            try {
                await $fetch(`/api/v2/groups/${groupId}/user/${userId}`, {
                    method: 'DELETE'
                })

                this.groups = this.groups.filter(g => g.id !== groupId)
                this.currentGroup = null
            } catch (err) {
                this.error = getErrorMessage(err, 'Fehler beim Verlassen der Gruppe')
                throw err
            }
        },

        clearCurrentGroup() {
            this.currentGroup = null
        },

        clearError() {
            this.error = null
        }
    }
})
