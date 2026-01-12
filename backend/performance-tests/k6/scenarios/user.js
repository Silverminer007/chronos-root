import { group } from 'k6';
import {
  apiGet,
  apiPost,
  apiPatch,
  checkResponse,
  thinkTime,
  parseJsonBody,
} from '../lib/http-helpers.js';
import { getCurrentUser } from '../lib/auth.js';
import { generateUserUpdate } from '../lib/data-generators.js';
import { ENDPOINTS } from '../config/constants.js';

export function testUser() {
  let userId = null;

  group('User API', () => {
    // POST /api/v2/user - Create/Register user (first request for new users)
    group('Create User', () => {
      const response = apiPost(ENDPOINTS.USER, {}, 'user_create');
      // This may return 200 if user exists or create new user
      checkResponse(response, {
        'POST user status is 200 or 400': (r) => r.status === 200 || r.status === 400,
      });
      thinkTime(0.3, 0.5);
    });

    // GET /api/v2/user - Get current user
    group('Get Current User', () => {
      const response = apiGet(ENDPOINTS.USER, 'user_get');
      checkResponse(response, {
        'GET user status is 200': (r) => r.status === 200,
        'GET user returns user data': (r) => {
          const body = parseJsonBody(r);
          if (body && body.id) {
            userId = body.id;
            return body.email !== undefined;
          }
          return false;
        },
      });
      thinkTime(0.2, 0.4);
    });

    // PATCH /api/v2/user - Update user
    if (userId) {
      group('Update User', () => {
        const currentUser = getCurrentUser();
        const updateData = generateUserUpdate(
          userId,
          currentUser.email,
          currentUser.firstName,
          currentUser.lastName
        );
        const response = apiPatch(ENDPOINTS.USER, updateData, 'user_update');
        checkResponse(response, {
          'PATCH user status is 200': (r) => r.status === 200,
        });
        thinkTime(0.3, 0.5);
      });

      // Reset user name back to original
      group('Reset User', () => {
        const currentUser = getCurrentUser();
        const resetData = {
          id: userId,
          email: currentUser.email,
          first_name: currentUser.firstName,
          last_name: currentUser.lastName,
        };
        apiPatch(ENDPOINTS.USER, resetData, 'user_reset');
        thinkTime(0.2, 0.4);
      });
    }
  });
}
