/** Antwort von `GET /api/v2/admin/stats`. */
export interface AdminStatistics {
  users: { total: number };
  appointments: { total: number; planned: number; cancelled: number; deleted: number; notEnoughAttendees: number };
  participations: { total: number; pending: number; approved: number; rejected: number };
  groups: { total: number; totalMembers: number };
  friendships: { total: number; pending: number; accepted: number; declined: number };
  messages: { total: number };
  pushSubscriptions: { total: number };
}

export interface AdminUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  createdAt: string;
  lastUpdate: string;
  lastSeen: string;
}

export interface AdminUserResponse {
  items: AdminUser[];
  page: number;
  size: number;
  total: number;
}

export interface PushLogEntry {
  id: number;
  user_id: number;
  notification_type: string | null;
  payload: string;
  status_code: number;
  success: boolean;
  error_message: string | null;
  created_at: string;
}
