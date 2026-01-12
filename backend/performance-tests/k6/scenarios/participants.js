import { group } from 'k6';
import {
  apiGet,
  apiPost,
  apiDelete,
  checkResponse,
  thinkTime,
  parseJsonBody,
} from '../lib/http-helpers.js';
import { generateAppointment } from '../lib/data-generators.js';
import { ENDPOINTS } from '../config/constants.js';

export function testParticipants() {
  let appointmentId = null;

  group('Participants API', () => {
    // First create an appointment to test participants
    group('Setup - Create Appointment', () => {
      const appointmentData = generateAppointment();
      const response = apiPost(ENDPOINTS.APPOINTMENTS, appointmentData, 'participants_setup_create');

      if (response.status === 201) {
        const body = parseJsonBody(response);
        if (body) {
          appointmentId = body.id;
        }
      }
      thinkTime(0.3, 0.5);
    });

    if (appointmentId) {
      // GET /api/v2/appointments/{id}/participants
      group('List Participants', () => {
        const response = apiGet(ENDPOINTS.PARTICIPANTS(appointmentId), 'participants_list');
        checkResponse(response, {
          'GET participants status is 200': (r) => r.status === 200,
        });
        thinkTime(0.3, 0.6);
      });

      // GET /api/v2/appointments/{id}/participants/status
      group('Get Participation Status', () => {
        const response = apiGet(ENDPOINTS.PARTICIPANTS_STATUS(appointmentId), 'participants_status');
        checkResponse(response, {
          'GET participation status is 200': (r) => r.status === 200,
        });
        thinkTime(0.2, 0.4);
      });

      // POST /api/v2/appointments/{id}/participants/approve
      group('Approve Participation', () => {
        const response = apiPost(
          ENDPOINTS.PARTICIPANTS_APPROVE(appointmentId),
          {},
          'participants_approve'
        );
        checkResponse(response, {
          'POST approve status is 200': (r) => r.status === 200,
        });
        thinkTime(0.3, 0.5);
      });

      // Cleanup
      group('Cleanup - Delete Appointment', () => {
        apiDelete(ENDPOINTS.APPOINTMENT(appointmentId), 'participants_cleanup_delete');
        thinkTime(0.2, 0.4);
      });
    }
  });
}
