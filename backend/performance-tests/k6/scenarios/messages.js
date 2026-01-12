import { group } from 'k6';
import {
  apiGet,
  apiPost,
  apiDelete,
  checkResponse,
  thinkTime,
  parseJsonBody,
} from '../lib/http-helpers.js';
import { generateAppointment, generateMessage } from '../lib/data-generators.js';
import { ENDPOINTS } from '../config/constants.js';

export function testMessages() {
  let appointmentId = null;

  group('Messages API', () => {
    // Setup: Create appointment
    group('Setup - Create Appointment', () => {
      const appointmentData = generateAppointment();
      const response = apiPost(ENDPOINTS.APPOINTMENTS, appointmentData, 'messages_setup_create');

      if (response.status === 201) {
        const body = parseJsonBody(response);
        if (body) {
          appointmentId = body.id;
        }
      }
      thinkTime(0.3, 0.5);
    });

    if (appointmentId) {
      // GET /api/v2/appointments/{id}/messages
      group('List Messages', () => {
        const response = apiGet(ENDPOINTS.MESSAGES(appointmentId), 'messages_list');
        checkResponse(response, {
          'GET messages status is 200': (r) => r.status === 200,
          'GET messages returns array': (r) => {
            const body = parseJsonBody(r);
            return Array.isArray(body);
          },
        });
        thinkTime(0.3, 0.6);
      });

      // POST /api/v2/appointments/{id}/messages
      group('Send Message', () => {
        const messageData = generateMessage();
        const response = apiPost(ENDPOINTS.MESSAGES(appointmentId), messageData, 'messages_send');
        checkResponse(response, {
          'POST message status is 200': (r) => r.status === 200,
        });
        thinkTime(0.3, 0.6);
      });

      // Send multiple messages to test load
      group('Send Multiple Messages', () => {
        for (let i = 0; i < 3; i++) {
          const messageData = generateMessage();
          apiPost(ENDPOINTS.MESSAGES(appointmentId), messageData, 'messages_send_batch');
          thinkTime(0.2, 0.4);
        }
      });

      // Verify messages were created
      group('Verify Messages', () => {
        const response = apiGet(ENDPOINTS.MESSAGES(appointmentId), 'messages_verify');
        checkResponse(response, {
          'Messages were created': (r) => {
            const messages = parseJsonBody(r);
            return Array.isArray(messages) && messages.length >= 4;
          },
        });
        thinkTime(0.2, 0.4);
      });

      // Cleanup
      group('Cleanup - Delete Appointment', () => {
        apiDelete(ENDPOINTS.APPOINTMENT(appointmentId), 'messages_cleanup_delete');
        thinkTime(0.2, 0.4);
      });
    }
  });
}
