import { group } from 'k6';
import {
  apiGet,
  apiPut,
  checkResponse,
  thinkTime,
  parseJsonBody,
} from '../lib/http-helpers.js';
import { generateSettings } from '../lib/data-generators.js';
import { ENDPOINTS } from '../config/constants.js';

export function testSettings() {
  group('Settings API', () => {
    // GET /api/v2/settings
    group('Get Settings', () => {
      const response = apiGet(ENDPOINTS.SETTINGS, 'settings_get');
      checkResponse(response, {
        'GET settings status is 200': (r) => r.status === 200,
        'GET settings returns valid data': (r) => {
          const body = parseJsonBody(r);
          return body && body.appointment_moved !== undefined;
        },
      });
      thinkTime(0.2, 0.4);
    });

    // PUT /api/v2/settings
    group('Update Settings', () => {
      const settingsData = generateSettings();
      const response = apiPut(ENDPOINTS.SETTINGS, settingsData, 'settings_update');
      checkResponse(response, {
        'PUT settings status is 200': (r) => r.status === 200,
      });
      thinkTime(0.3, 0.5);
    });

    // Verify settings update
    group('Verify Settings Update', () => {
      const response = apiGet(ENDPOINTS.SETTINGS, 'settings_verify');
      checkResponse(response, {
        'GET settings after update is 200': (r) => r.status === 200,
      });
      thinkTime(0.2, 0.4);
    });
  });
}
