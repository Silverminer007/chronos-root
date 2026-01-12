// API Configuration
export const API_BASE_URL = 'http://localhost:8080';
export const KEYCLOAK_URL = 'http://localhost:8180';
export const KEYCLOAK_REALM = __ENV.KEYCLOAK_REALM || 'chronos-perf';
export const KEYCLOAK_CLIENT_ID = __ENV.KEYCLOAK_CLIENT_ID || 'chronos-api';
export const KEYCLOAK_CLIENT_SECRET = __ENV.KEYCLOAK_CLIENT_SECRET || 'chronos-api-secret';

// Test user configuration (50 users)
export const TEST_USERS = [];
for (let i = 1; i <= 50; i++) {
  TEST_USERS.push({
    username: `testuser${i}`,
    password: `testpassword${i}`,
    email: `testuser${i}@chronos-perf.local`,
    firstName: 'Test',
    lastName: `User${i}`,
  });
}

// API Endpoints
export const ENDPOINTS = {
  // Appointments
  APPOINTMENTS: '/api/v2/appointments/',
  APPOINTMENT: (id) => `/api/v2/appointments/${id}`,
  APPOINTMENT_CANCEL: (id) => `/api/v2/appointments/${id}/cancel`,

  // Participants
  PARTICIPANTS: (appointmentId) => `/api/v2/appointments/${appointmentId}/participants/`,
  PARTICIPANTS_STATUS: (appointmentId) => `/api/v2/appointments/${appointmentId}/participants/status`,
  PARTICIPANTS_APPROVE: (appointmentId) => `/api/v2/appointments/${appointmentId}/participants/approve`,
  PARTICIPANTS_REJECT: (appointmentId) => `/api/v2/appointments/${appointmentId}/participants/reject`,
  PARTICIPANTS_USERS: (appointmentId) => `/api/v2/appointments/${appointmentId}/participants/users`,
  PARTICIPANTS_USER: (appointmentId, userId) => `/api/v2/appointments/${appointmentId}/participants/users/${userId}`,
  PARTICIPANTS_GROUPS: (appointmentId) => `/api/v2/appointments/${appointmentId}/participants/groups`,
  PARTICIPANTS_GROUP: (appointmentId, groupId) => `/api/v2/appointments/${appointmentId}/participants/groups/${groupId}`,

  // Messages
  MESSAGES: (appointmentId) => `/api/v2/appointments/${appointmentId}/messages/`,

  // Groups
  GROUPS: '/api/v2/groups/',
  GROUP: (id) => `/api/v2/groups/${id}`,
  GROUP_USERS: (id) => `/api/v2/groups/${id}/users`,
  GROUP_USER: (groupId, userId) => `/api/v2/groups/${groupId}/user/${userId}`,

  // User
  USER: '/api/v2/user',

  // Friendships
  FRIENDSHIP_SUGGESTIONS: '/api/v2/friendships/suggestions',
  FRIENDSHIP_REQUESTS: '/api/v2/friendships/requests',
  FRIENDSHIP_REQUEST_ACCEPT: (id) => `/api/v2/friendships/requests/${id}/accept`,
  FRIENDSHIP_REQUEST_DECLINE: (id) => `/api/v2/friendships/requests/${id}/decline`,
  FRIENDSHIP_REQUEST_CANCEL: (id) => `/api/v2/friendships/requests/${id}`,
  FRIENDSHIP_REQUESTS_INCOMING: '/api/v2/friendships/requests/incoming',
  FRIENDSHIP_REQUESTS_OUTGOING: '/api/v2/friendships/requests/outgoing',
  FRIENDS: '/api/v2/friendships/friends',
  FRIEND: (id) => `/api/v2/friendships/friends/${id}`,

  // Settings
  SETTINGS: '/api/v2/settings',

  // Push
  PUSH_PUBLIC_KEY: '/api/v2/push/public-key',
  PUSH_SUBSCRIBE: '/api/v2/push/subscribe',
  PUSH_STATUS: '/api/v2/push/status',
  PUSH_UNSUBSCRIBE: '/api/v2/push/unsubscribe',
};
