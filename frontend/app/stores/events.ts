import {defineStore} from 'pinia'
import type {Event} from '~/types'

export const useEventsStore = defineStore('events', () => {
    const events = ref<Event[]>([])
    const currentEvent = ref<Event | null>(null)
    const error = ref<string | undefined>(undefined)
    const loading = ref(false)
    const searchString = ref('')

    async function fetchEvents() {
        loading.value = true

        const {data} = await useFetch("/api/events", {
            params: {
                search: searchString.value,
                page: Math.floor(events.value.length / 20),
                size: 20,
                attendances: true
            }
        })

        if (!data.value) {
            error.value = "Failed to fetch events."
        } else {
            events.value = events.value.concat(data.value)
            error.value = undefined
        }

        loading.value = false
    }

    async function search(search: string | undefined) {
        if (!search) return

        events.value = []
        searchString.value = search
        await fetchEvents()
    }

    async function updateAttendanceStatus(
        eventId: number,
        attendanceStatus: "APPROVED" | "REJECTED",
        userId: number | undefined
    ) {
        if (!userId) return

        // Backup für Rollback
        let oldAttendanceStatus: "PENDING" | "APPROVED" | "REJECTED" = "PENDING"

        for (const event of events.value) {
            if (event.id === eventId) {
                oldAttendanceStatus = event.own_attendance_status
                event.own_attendance_status = attendanceStatus

                for (const attendance of event.attendances) {
                    if (attendance.user_id === userId) {
                        attendance.status = attendanceStatus
                        break
                    }
                }
                break
            }
        }
        error.value = undefined
        await $fetch(`/api/event/${eventId}/attendance`, {
            method: "POST",
            body: {status: attendanceStatus},
            onResponseError() {
                error.value = "Failed to update attendance status."

                for (const event of events.value) {
                    if (event.id === eventId) {
                        event.own_attendance_status = oldAttendanceStatus
                        for (const attendance of event.attendances) {
                            if (attendance.user_id === userId) {
                                attendance.status = oldAttendanceStatus
                                break
                            }
                        }
                        break
                    }
                }
            }
        })
    }

    function getApprovedAttendances(event: Event) {
        return event.attendances.filter(a => a.status === "APPROVED")
    }

    function hasRejected(event: Event) {
        return event.own_attendance_status === "REJECTED"
    }

    function hasApproved(event: Event) {
        return event.own_attendance_status === "APPROVED"
    }

    async function sendMessage(eventId: number, messageTitle: string, messageBody: string) {
        error.value = undefined
        await $fetch(`/api/event/${eventId}/messages`, {
            method: "POST",
            body: {
                event_id: eventId,
                title: messageTitle,
                message: messageBody
            },
            onResponseError() {
                error.value = "Message sending failed."
            }
        })
    }

    async function setCurrentEvent(event: Event) {
        currentEvent.value = event;
    }

    async function getEventById(eventId: number): Promise<Event | null> {
        if (currentEvent.value && currentEvent.value.id === eventId) {
            return currentEvent.value
        }
        loading.value = true

        const {data} = await useFetch(`/api/event/${eventId}`, {
            params: {
                attendances: true,
                messages: true,
                attendees: true
            }
        })

        loading.value = false
        if (!data.value) {
            error.value = "You either have no permission to see this event or it does not exist.";
            return null;
        } else {
            currentEvent.value = data.value
            error.value = undefined
            return currentEvent.value
        }
    }

    return {
        events,
        error,
        loading,
        searchString,
        fetchEvents,
        search,
        updateAttendanceStatus,
        getApprovedAttendances,
        hasApproved,
        hasRejected,
        getEventById,
        setCurrentEvent,
        sendMessage
    }
})