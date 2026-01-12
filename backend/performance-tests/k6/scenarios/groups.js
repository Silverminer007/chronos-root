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
import { generateGroup } from '../lib/data-generators.js';
import { ENDPOINTS } from '../config/constants.js';

export function testGroups() {
  let createdGroupId = null;

  group('Groups API', () => {
    // GET /api/v2/groups - List groups
    group('List Groups', () => {
      const response = apiGet(ENDPOINTS.GROUPS, 'groups_list');
      checkResponse(response, {
        'GET groups status is 200': (r) => r.status === 200,
        'GET groups returns array': (r) => {
          const body = parseJsonBody(r);
          return Array.isArray(body);
        },
      });
      thinkTime(0.3, 0.6);
    });

    // GET /api/v2/groups with search
    group('Search Groups', () => {
      const response = apiGet(`${ENDPOINTS.GROUPS}?search=test`, 'groups_search');
      checkResponse(response, {
        'GET groups search status is 200': (r) => r.status === 200,
      });
      thinkTime(0.3, 0.5);
    });

    // POST /api/v2/groups - Create group
    group('Create Group', () => {
      const groupData = generateGroup();
      const response = apiPost(ENDPOINTS.GROUPS, groupData, 'groups_create');

      checkResponse(response, {
        'POST group status is 200': (r) => r.status === 200,
        'POST group returns id': (r) => {
          const body = parseJsonBody(r);
          if (body && body.id) {
            createdGroupId = body.id;
            return true;
          }
          return false;
        },
      });
      thinkTime(0.5, 1);
    });

    if (createdGroupId) {
      // GET /api/v2/groups/{group}/users
      group('Get Group Users', () => {
        const response = apiGet(ENDPOINTS.GROUP_USERS(createdGroupId), 'groups_users_list');
        checkResponse(response, {
          'GET group users status is 200': (r) => r.status === 200,
        });
        thinkTime(0.3, 0.5);
      });

      // PATCH /api/v2/groups/{group} - Update group
      group('Update Group', () => {
        const updateData = { name: `Updated Group ${Date.now()}` };
        const response = apiPatch(ENDPOINTS.GROUP(createdGroupId), updateData, 'groups_update');
        checkResponse(response, {
          'PATCH group status is 200': (r) => r.status === 200,
        });
        thinkTime(0.3, 0.6);
      });

      // DELETE /api/v2/groups/{group} - Delete group
      group('Delete Group', () => {
        const response = apiDelete(ENDPOINTS.GROUP(createdGroupId), 'groups_delete');
        checkResponse(response, {
          'DELETE group status is 200 or 204': (r) => r.status === 200 || r.status === 204,
        });
        thinkTime(0.3, 0.5);
      });
    }
  });
}
