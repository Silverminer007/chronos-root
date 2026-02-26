export interface Appointment {
    id: number,
    name: string,
    description: string,
    start: string,
    end: string,
    venue: string,
    status: AppointmentStatus,
    minimal_attendees: number,
    participants: UserParticipant[],
    group_participants: ParticipantGroup[],
    messages: Message[]
}

export type Role = "NONE" | "GUEST" | "ATTENDANT" | "HELPER" | "RESPONSIBLE";
export type ParticipationStatus = "PENDING" | "APPROVED" | "REJECTED";
export type AppointmentStatus =  "PLANNED" | "CANCELLED" | "DELETED" | "NOT_ENOUGH_ATTENDEES";

export interface UserParticipant {
    user_id: number,
    name: string,
    profile_picture_url: string,
    role: Role,
    status: ParticipationStatus,
    via_group_id: number | null,
    via_group_name: string | null,
}

// For displaying groups in the UI (derived from participants)
export interface ParticipantGroup {
    id: number,
    name: string,
    members: User[]
}

export interface Group {
    id: number,
    name: string,
    members: User[];
}

export interface User {
    id: number,
    first_name: string | null,
    last_name: string | null,
}

export interface Friend {
    user_id: number,
    name: string,
    email: string,
    profile_picture_url: string,
    friends_since: string;
}

export interface FriendshipRequest {
    requestId: number;
    userId: number;
    userName: string;
    userEmail: string;
    profilePictureUrl: string;
    status: "PENDING" | "ACCEPTED" | "DECLINED";
    createdAt: string;
    respondedAt: string;
    isIncoming: boolean;
}

export interface Message {
    id: number;
    sender_id: number;
    sender_name: string;
    appointment_id: number;
    body: string;
    timestamp: string;
}

// Notification settings types
export type AppointmentNotificationSetting = "DISABLED" | "ALL" | "ATTENDANT" | "HELPER" | "RESPONSIBLE";
export type GroupNotificationSetting = "DISABLED" | "ENABLED";

export interface NotificationSettings {
    appointment_moved: AppointmentNotificationSetting;
    appointment_message: AppointmentNotificationSetting;
    appointment_cancelled: AppointmentNotificationSetting;
    appointment_participant_added: AppointmentNotificationSetting;
    appointment_participation_status_changed: AppointmentNotificationSetting;
    appointment_participation_invalid: AppointmentNotificationSetting;
    appointment_participation_status_pending: AppointmentNotificationSetting;
    appointment_reminder: AppointmentNotificationSetting;
    group_member_added: GroupNotificationSetting;
}