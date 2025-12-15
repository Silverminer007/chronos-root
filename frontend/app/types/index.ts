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
    userAttendees: UserAttendee[],
    groupAttendees: GroupAttendee[],
    messages: Message[],
    own_attendance_status: "PENDING" | "APPROVED" | "REJECTED"
}

export interface Attendance {
    user_name: string,
    user_id: number,
    status: "PENDING" | "APPROVED" | "REJECTED",
    id: number
}

export interface Group {
    id: number,
    name: string,
    owner: boolean,
    members: User[],
}

export interface GroupAttendee {
    group: Group,
    role: "ATTENDANT" | "RESPONSIBLE" | "GUEST";
}

export interface UserAttendee {
    user: User,
    role: "ATTENDANT" | "RESPONSIBLE" | "GUEST";
}

export interface User {
    id: number,
    first_name: string,
    last_name: string,
}

export interface Message {
    id: number;
    sender_id: number;
    event_id: number;
    sender_name: string;
    title: string;
    body: string;
    timestamp: string;
}