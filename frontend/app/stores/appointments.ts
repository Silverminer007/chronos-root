// stores/appointments.ts
import {defineStore} from 'pinia'
import type {Appointment, ParticipantGroup} from "~/types";

export interface AppointmentFilters {
    start?: string
    end?: string
    search?: string
    page?: number
    size?: number
    participants?: boolean
    messages?: boolean
    groups?: boolean
}

export const useAppointmentsStore = defineStore('appointments', {
    state: () => ({
        appointments: [] as Appointment[],
        currentAppointment: null as Appointment | null,
        loading: false,
        error: null as string | null,
        pagination: {
            page: 0,
            size: 20
        }
    }),

    getters: {
        // Sortierte Appointments nach Startdatum
        sortedAppointments: (state) => {
            return [...state.appointments].sort((a, b) =>
                new Date(a.start).getTime() - new Date(b.start).getTime()
            )
        },

        // Kommende Appointments
        upcomingAppointments: (state) => {
            const now = new Date()
            return state.appointments.filter(apt =>
                new Date(apt.start) > now
            ).sort((a, b) =>
                new Date(a.start).getTime() - new Date(b.start).getTime()
            )
        },

        // Vergangene Appointments
        pastAppointments: (state) => {
            const now = new Date()
            return state.appointments.filter(apt =>
                new Date(apt.end) < now
            ).sort((a, b) =>
                new Date(b.start).getTime() - new Date(a.start).getTime()
            )
        },

        // Appointment nach ID finden
        getAppointmentById: (state) => {
            return (id: number) => state.appointments.find(apt => apt.id === id)
        },

        // Genehmigte Teilnehmer abrufen
        getApprovedParticipants: () => {
            return (appointment: Appointment) =>
                appointment.participants?.filter(p => p.status === 'APPROVED') || []
        },

        // Teilnahme des aktuellen Benutzers
        getCurrentUserParticipation: () => {
            return (appointment: Appointment, userId: number) =>
                appointment.participants?.find(p => p.user_id === userId)
        },

        // Prüfen ob Benutzer zugesagt hat
        hasApproved: () => {
            return (appointment: Appointment, userId: number) =>
                appointment.participants?.some(p => p.user_id === userId && p.status === 'APPROVED') || false
        },

        // Prüfen ob Benutzer abgesagt hat
        hasRejected: () => {
            return (appointment: Appointment, userId: number) =>
                appointment.participants?.some(p => p.user_id === userId && p.status === 'REJECTED') || false
        },

        // Teilnehmer nach Rolle gruppieren
        getParticipantsByRole: () => {
            return (appointment: Appointment) => ({
                responsible: appointment.participants?.filter(p => p.role === 'RESPONSIBLE') || [],
                attendant: appointment.participants?.filter(p => p.role === 'ATTENDANT') || [],
                guest: appointment.participants?.filter(p => p.role === 'GUEST') || []
            })
        },

        // Gruppen aus Teilnehmern extrahieren
        getGroups: () => {
            return (appointment: Appointment): ParticipantGroup[] => {
                return appointment.group_participants;
            }
        },

        // Teilnehmer einer bestimmten Gruppe abrufen
        getParticipantsByGroup: () => {
            return (appointment: Appointment, groupId: number) =>
                appointment.participants?.filter(p => p.via_group_id === groupId) || []
        },

        // Direkt hinzugefügte Teilnehmer (nicht über Gruppe)
        getDirectParticipants: () => {
            return (appointment: Appointment) =>
                appointment.participants?.filter(p => p.via_group_id === null) || []
        }
    },

    actions: {
        // Agenda abrufen
        async fetchAgenda(filters: AppointmentFilters = {}) {
            this.loading = true
            this.error = null

            try {
                const params = new URLSearchParams()

                if (filters.start) params.append('start', filters.start)
                if (filters.end) params.append('end', filters.end)
                if (filters.search) params.append('search', filters.search)
                if (filters.page !== undefined) {
                    params.append('page', filters.page.toString())
                    this.pagination.page = filters.page
                }
                if (filters.size !== undefined) {
                    params.append('size', filters.size.toString())
                    this.pagination.size = filters.size
                }
                if (filters.participants) params.append('participants', 'true')
                if (filters.messages) params.append('messages', 'true')
                if (filters.groups) params.append('groups', 'true')

                const response = await $fetch(`/api/v2/appointments?${params.toString()}`)
                console.log(response)

                if (!this.appointments || this.appointments.length === 0) {
                    this.appointments = [];
                }
                this.appointments = this.appointments.concat(response.content || response);
                console.log(this.appointments);
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Laden der Appointments'
                throw err
            } finally {
                this.loading = false
            }
        },

        // Einzelnes Appointment abrufen
        async fetchAppointment(id: number, options = {participants: true, messages: true, group_participants: true}) {
            this.loading = true
            this.error = null

            try {
                const params = new URLSearchParams()
                if (options.participants) params.append('participants', 'true')
                if (options.messages) params.append('messages', 'true')
                if (options.group_participants) params.append('group_participants', 'true')

                const response = await $fetch(`/api/v2/appointments/${id}?${params.toString()}`)

                this.currentAppointment = response

                // Auch in der Liste aktualisieren
                const index = this.appointments.findIndex(apt => apt.id === id)
                if (index !== -1) {
                    this.appointments[index] = response
                }

                return response
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Laden des Appointments'
                throw err
            } finally {
                this.loading = false
            }
        },

        // Neues Appointment erstellen
        async createAppointment(data: Omit<Appointment, 'id'>) {
            this.loading = true
            this.error = null

            try {
                const response = await $fetch('/api/v2/appointments', {
                    method: 'POST',
                    body: data
                })

                this.appointments.push(response)
                return response
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Erstellen des Appointments'
                throw err
            } finally {
                this.loading = false
            }
        },

        // Appointment aktualisieren
        async updateAppointment(id: number, data: Partial<Appointment>) {
            this.loading = true
            this.error = null

            try {
                const response = await $fetch(`/api/v2/appointments/${id}`, {
                    method: 'PATCH',
                    body: data
                })

                const index = this.appointments.findIndex(apt => apt.id === id)
                if (index !== -1) {
                    this.appointments[index] = response
                }

                if (this.currentAppointment?.id === id) {
                    this.currentAppointment = response
                }

                return response
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Aktualisieren des Appointments'
                throw err
            } finally {
                this.loading = false
            }
        },

        // Appointment löschen
        async deleteAppointment(id: number) {
            this.loading = true
            this.error = null

            try {
                await $fetch(`/api/v2/appointments/${id}`, {
                    method: 'DELETE'
                })

                this.appointments = this.appointments.filter(apt => apt.id !== id)

                if (this.currentAppointment?.id === id) {
                    this.currentAppointment = null
                }
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Löschen des Appointments'
                throw err
            } finally {
                this.loading = false
            }
        },

        // Appointment absagen
        async cancelAppointment(id: number) {
            this.loading = true
            this.error = null

            try {
                await $fetch(`/api/v2/appointments/${id}/cancel`, {
                    method: 'POST'
                })

                // Appointment neu laden
                await this.fetchAppointment(id)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Absagen des Appointments'
                throw err
            } finally {
                this.loading = false
            }
        },

        // Teilnehmer hinzufügen
        async addParticipant(appointmentId: number, userId: number, role: string) {
            try {
                await $fetch(`/api/v2/appointments/${appointmentId}/participants/users`, {
                    method: 'POST',
                    body: {user_id: userId, user_role: role}
                })

                await this.fetchAppointment(appointmentId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Hinzufügen des Teilnehmers'
                throw err
            }
        },

        // Teilnehmer entfernen
        async removeParticipant(appointmentId: number, userId: number) {
            try {
                await $fetch(`/api/v2/appointments/${appointmentId}/participants/users/${userId}`, {
                    method: 'DELETE'
                })

                await this.fetchAppointment(appointmentId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Entfernen des Teilnehmers'
                throw err
            }
        },

        // Teilnehmerrolle ändern
        async changeParticipantRole(appointmentId: number, userId: number, role: string) {
            try {
                await $fetch(`/api/v2/appointments/${appointmentId}/participants/users/${userId}`, {
                    method: 'PATCH',
                    body: {role}
                })

                await this.fetchAppointment(appointmentId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Ändern der Rolle'
                throw err
            }
        },

        // Gruppe hinzufügen
        async addGroupParticipant(appointmentId: number, groupId: number, role: string) {
            try {
                await $fetch(`/api/v2/appointments/${appointmentId}/participants/groups`, {
                    method: 'POST',
                    body: {group_id: groupId, user_role: role}
                })

                await this.fetchAppointment(appointmentId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Hinzufügen der Gruppe'
                throw err
            }
        },

        // Gruppe entfernen
        async removeGroupParticipant(appointmentId: number, groupId: number) {
            try {
                await $fetch(`/api/v2/appointments/${appointmentId}/participants/groups/${groupId}`, {
                    method: 'DELETE'
                })

                await this.fetchAppointment(appointmentId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Entfernen der Gruppe'
                throw err
            }
        },

        // Appointment zusagen
        async approveAppointment(appointmentId: number) {
            try {
                await $fetch(`/api/v2/appointments/${appointmentId}/participants/approve`, {
                    method: 'POST'
                })

                await this.fetchAppointment(appointmentId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Zusagen'
                throw err
            }
        },

        // Appointment absagen (als Teilnehmer)
        async rejectAppointment(appointmentId: number) {
            try {
                await $fetch(`/api/v2/appointments/${appointmentId}/participants/reject`, {
                    method: 'POST'
                })

                await this.fetchAppointment(appointmentId)
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Absagen'
                throw err
            }
        },

        // Nachricht senden
        async sendMessage(appointmentId: number, body: string) {
            try {
                const response = await $fetch(`/api/v2/appointments/${appointmentId}/messages`, {
                    method: 'POST',
                    body: {body}
                })

                // Nachrichten neu laden
                await this.fetchAppointment(appointmentId)

                return response
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Senden der Nachricht'
                throw err
            }
        },

        // Nachrichten abrufen
        async fetchMessages(appointmentId: number) {
            try {
                const response = await $fetch(`/api/v2/appointments/${appointmentId}/messages`)

                if (this.currentAppointment?.id === appointmentId) {
                    this.currentAppointment.messages = response
                }

                return response
            } catch (err: any) {
                this.error = err.message || 'Fehler beim Laden der Nachrichten'
                throw err
            }
        },

        // Initiale Appointments laden (für Agenda-Seite)
        async loadInitialAppointments() {
            return this.fetchAgenda({
                participants: true,
                page: 0,
                size: 20
            })
        },

        // Weitere Appointments laden (Paginierung)
        async fetchAppointments() {
            return this.fetchAgenda({
                participants: true,
                page: this.pagination.page + 1,
                size: this.pagination.size
            })
        },

        // Suche
        search(query: string) {
            return this.fetchAgenda({
                search: query,
                participants: true,
                page: 0,
                size: 20
            })
        },

        // State zurücksetzen
        resetCurrentAppointment() {
            this.currentAppointment = null
        },

        clearError() {
            this.error = null
        }
    }
})