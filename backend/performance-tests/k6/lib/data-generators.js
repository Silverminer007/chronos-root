// Test data generators for K6 performance tests

function randomString(length) {
  const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function generateAppointment() {
  const now = new Date();
  const startTime = new Date(now.getTime() + randomInt(1, 30) * 24 * 60 * 60 * 1000);
  const endTime = new Date(startTime.getTime() + randomInt(1, 4) * 60 * 60 * 1000);

  return {
    name: `Perf Test Appointment ${randomString(8)}`,
    description: `Performance test appointment created by K6. ID: ${randomString(16)}`,
    start: startTime.toISOString(),
    end: endTime.toISOString(),
    venue: `Test Venue ${randomString(6)}`,
    minimal_attendees: randomInt(1, 10),
  };
}

export function generateUpdateAppointment() {
  const now = new Date();
  const startTime = new Date(now.getTime() + randomInt(31, 60) * 24 * 60 * 60 * 1000);
  const endTime = new Date(startTime.getTime() + randomInt(1, 4) * 60 * 60 * 1000);

  return {
    name: `Updated Appointment ${randomString(8)}`,
    description: `Updated by performance test. ID: ${randomString(16)}`,
    start: startTime.toISOString(),
    end: endTime.toISOString(),
    venue: `Updated Venue ${randomString(6)}`,
    minimal_attendees: randomInt(2, 15),
  };
}

export function generateGroup() {
  return {
    name: `Perf Test Group ${randomString(8)}`,
  };
}

export function generateMessage() {
  return {
    body: `Performance test message: ${randomString(50)}`,
  };
}

export function generateSettings() {
  const options = ['ALL', 'RESPONSIBLE', 'ATTENDANT', 'DISABLED'];
  const randomOption = () => options[randomInt(0, options.length - 1)];

  return {
    appointment_moved: randomOption(),
    appointment_message: randomOption(),
    appointment_cancelled: randomOption(),
    appointment_participant_added: randomOption(),
    appointment_participation_status_changed: randomOption(),
    appointment_participation_invalid: randomOption(),
    appointment_participation_status_pending: randomOption(),
    appointment_reminder: randomOption(),
    group_member_added: 'DISABLED',
  };
}

export function generatePushSubscription() {
  return {
    endpoint: `https://fcm.googleapis.com/fcm/send/${randomString(32)}`,
    keys: {
      p256dh: randomString(87),
      auth: randomString(22),
    },
  };
}

export function generateAddParticipant(userId) {
  return {
    user_id: userId,
    user_role: 'ATTENDANT',
  };
}

export function generateAddGroupParticipant(groupId) {
  return {
    group_id: groupId,
    user_role: 'ATTENDANT',
  };
}

export function generateParticipantRole() {
  return {
    role: 'HELPER',
  };
}

export function generateUserUpdate(userId, email, firstName, lastName) {
  return {
    id: userId,
    email: email,
    first_name: firstName,
    last_name: `${lastName}_updated`,
  };
}
