export interface Event {
    id: number,
    name: string,
    description: string,
    start: string,
    end: string,
    venue: string,
    status: "PLANNED" | "CANCELLED" | "DELETED" | "NOT_ENOUGH_ATTENDEES",
    minimal_attendees: number,
    attendances: Attendance[],
    own_attendance_status: "PENDING" | "APPROVED" | "REJECTED"
}

export interface Attendance {
    user_name: string,
    user_id: number,
    status: "PENDING" | "APPROVED" | "REJECTED",
    id: number
}

export interface User {
    id: number,
    first_name: string,
    last_name: string,
}