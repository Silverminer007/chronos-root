import {defineStore} from 'pinia'
import type {Friend, FriendshipRequest} from '~/types'

export const useFriendshipsStore = defineStore('friendships', {
    state: () => ({
        friends: [] as Friend[],
        incomingRequests: [] as FriendshipRequest[],
        outgoingRequests: [] as FriendshipRequest[],
        loading: false,
        error: null as string | null
    }),

    getters: {
        totalRequestsCount: (state) => {
            return state.incomingRequests.length + state.outgoingRequests.length
        },

        incomingRequestsCount: (state) => state.incomingRequests.length,

        outgoingRequestsCount: (state) => state.outgoingRequests.length,

        friendsCount: (state) => state.friends.length
    },

    actions: {
        async fetchFriends() {
            this.loading = true
            this.error = null

            try {
                const response = await $fetch<Friend[]>('/api/v2/friendships/friends')
                this.friends = response || []
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Laden der Freunde'
                throw err
            } finally {
                this.loading = false
            }
        },

        async fetchIncomingRequests() {
            this.loading = true
            this.error = null

            try {
                const response = await $fetch<FriendshipRequest[]>('/api/v2/friendships/requests/incoming')
                this.incomingRequests = response || []
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Laden der eingehenden Anfragen'
                throw err
            } finally {
                this.loading = false
            }
        },

        async fetchOutgoingRequests() {
            this.loading = true
            this.error = null

            try {
                const response = await $fetch<FriendshipRequest[]>('/api/v2/friendships/requests/outgoing')
                this.outgoingRequests = response || []
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Laden der ausgehenden Anfragen'
                throw err
            } finally {
                this.loading = false
            }
        },

        async fetchAll() {
            this.loading = true
            this.error = null

            try {
                await Promise.all([
                    this.fetchFriends(),
                    this.fetchIncomingRequests(),
                    this.fetchOutgoingRequests()
                ])
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Laden der Daten'
                throw err
            } finally {
                this.loading = false
            }
        },

        async endFriendship(friendId: number) {
            try {
                await $fetch(`/api/v2/friendships/friends/${friendId}`, {
                    method: 'DELETE'
                })

                this.friends = this.friends.filter(f => f.user_id !== friendId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Beenden der Freundschaft'
                throw err
            }
        },

        async sendRequest(email: string) {
            try {
                await $fetch('/api/v2/friendships/requests', {
                    method: 'POST',
                    query: {email}
                })

                await this.fetchOutgoingRequests()
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Senden der Anfrage'
                throw err
            }
        },

        async acceptRequest(requestId: number) {
            try {
                await $fetch(`/api/v2/friendships/requests/${requestId}/accept`, {
                    method: 'POST'
                })

                this.incomingRequests = this.incomingRequests.filter(r => r.requestId !== requestId)
                await this.fetchFriends()
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Annehmen der Anfrage'
                throw err
            }
        },

        async declineRequest(requestId: number) {
            try {
                await $fetch(`/api/v2/friendships/requests/${requestId}/decline`, {
                    method: 'POST'
                })

                this.incomingRequests = this.incomingRequests.filter(r => r.requestId !== requestId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Ablehnen der Anfrage'
                throw err
            }
        },

        async cancelRequest(requestId: number) {
            try {
                await $fetch(`/api/v2/friendships/requests/${requestId}`, {
                    method: 'DELETE'
                })

                this.outgoingRequests = this.outgoingRequests.filter(r => r.requestId !== requestId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Zurückziehen der Anfrage'
                throw err
            }
        },

        clearError() {
            this.error = null
        }
    }
})
