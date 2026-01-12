import { group } from 'k6';
import {
  apiGet,
  apiPost,
  apiPatch,
  apiDelete,
  checkResponse,
  thinkTime,
  parseJsonBody,
} from '../lib/http-helpers.js';
import { generateAppointment, generateUpdateAppointment } from '../lib/data-generators.js';
import { ENDPOINTS } from '../config/constants.js';

export function testAppointments() {
  let createdAppointmentId = null;

  // GET /api/v2/appointments - List appointments
  group('List Appointments', () => {
    const response = apiGet(ENDPOINTS.APPOINTMENTS, 'appointments_list');
    checkResponse(response, {
      'GET appointments status is 200': (r) => r.status === 200,
      'GET appointments returns array': (r) => {
        const body = parseJsonBody(r);
        return Array.isArray(body);
      },
    });
    thinkTime(0.5, 1);
  });

  // GET /api/v2/appointments with query params
  group('List Appointments with Filters', () => {
    const queryParams = '?page=0&size=20&participants=true&messages=true';
    const response = apiGet(`${ENDPOINTS.APPOINTMENTS}${queryParams}`, 'appointments_list_filtered');
    checkResponse(response, {
      'GET appointments with filters status is 200': (r) => r.status === 200,
    });
    thinkTime(0.3, 0.8);
  });

  // POST /api/v2/appointments - Create appointment
  group('Create Appointment', () => {
    const appointmentData = generateAppointment();
    const response = apiPost(ENDPOINTS.APPOINTMENTS, appointmentData, 'appointments_create');

    // Extract ID from response
    const body = parseJsonBody(response);
    if (response.status === 201 && body && body.id) {
      createdAppointmentId = body.id;
    }

    checkResponse(response, {
      'POST appointment status is 201': (r) => r.status === 201,
      'POST appointment returns id': () => createdAppointmentId !== null,
    });
    thinkTime(0.5, 1);
  });

  // GET /api/v2/appointments/{id} - Get single appointment
  group('Get Single Appointment', () => {
    if (!createdAppointmentId) {
      console.log('GET - Skipped: no appointment ID');
      return;
    }
    const url = ENDPOINTS.APPOINTMENT(createdAppointmentId);
    const response = apiGet(url, 'appointments_get');
    checkResponse(response, {
      'GET appointment by id status is 200': (r) => r.status === 200,
    });
    thinkTime(0.3, 0.6);
  });

  // PATCH /api/v2/appointments/{id} - Update appointment
  group('Update Appointment', () => {
    if (!createdAppointmentId) return;
    const updateData = generateUpdateAppointment();
    const response = apiPatch(
      ENDPOINTS.APPOINTMENT(createdAppointmentId),
      updateData,
      'appointments_update'
    );
    checkResponse(response, {
      'PATCH appointment status is 200': (r) => r.status === 200,
    });
    thinkTime(0.5, 1);
  });

  // POST /api/v2/appointments/{id}/cancel - Cancel appointment
  group('Cancel Appointment', () => {
    if (!createdAppointmentId) return;
    const response = apiPost(
      ENDPOINTS.APPOINTMENT_CANCEL(createdAppointmentId),
      {},
      'appointments_cancel'
    );
    checkResponse(response, {
      'POST cancel appointment status is 200': (r) => r.status === 200,
    });
    thinkTime(0.3, 0.6);
  });

  // DELETE /api/v2/appointments/{id} - Delete appointment
  group('Delete Appointment', () => {
    if (!createdAppointmentId) return;
    const response = apiDelete(
      ENDPOINTS.APPOINTMENT(createdAppointmentId),
      'appointments_delete'
    );
    checkResponse(response, {
      'DELETE appointment status is 200 or 204': (r) => r.status === 200 || r.status === 204,
    });
    thinkTime(0.3, 0.6);
  });

  return createdAppointmentId;
}
