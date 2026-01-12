import { group } from 'k6';
import {
  apiGet,
  checkResponse,
  thinkTime,
  parseJsonBody,
} from '../lib/http-helpers.js';
import { ENDPOINTS } from '../config/constants.js';

export function testFriendships() {
  group('Friendships API', () => {
    // GET /api/v2/friendships/suggestions
    group('Get Suggestions', () => {
      const response = apiGet(ENDPOINTS.FRIENDSHIP_SUGGESTIONS, 'friendships_suggestions');
      checkResponse(response, {
        'GET suggestions status is 200': (r) => r.status === 200,
      });
      thinkTime(0.3, 0.6);
    });

    // GET /api/v2/friendships/suggestions with search
    group('Search Suggestions', () => {
      const response = apiGet(
        `${ENDPOINTS.FRIENDSHIP_SUGGESTIONS}?search=test`,
        'friendships_suggestions_search'
      );
      checkResponse(response, {
        'GET suggestions search status is 200': (r) => r.status === 200,
      });
      thinkTime(0.3, 0.5);
    });

    // GET /api/v2/friendships/requests/incoming
    group('Get Incoming Requests', () => {
      const response = apiGet(ENDPOINTS.FRIENDSHIP_REQUESTS_INCOMING, 'friendships_incoming');
      checkResponse(response, {
        'GET incoming requests status is 200': (r) => r.status === 200,
        'GET incoming returns array': (r) => {
          const body = parseJsonBody(r);
          return Array.isArray(body);
        },
      });
      thinkTime(0.3, 0.5);
    });

    // GET /api/v2/friendships/requests/outgoing
    group('Get Outgoing Requests', () => {
      const response = apiGet(ENDPOINTS.FRIENDSHIP_REQUESTS_OUTGOING, 'friendships_outgoing');
      checkResponse(response, {
        'GET outgoing requests status is 200': (r) => r.status === 200,
      });
      thinkTime(0.3, 0.5);
    });

    // GET /api/v2/friendships/friends
    group('Get Friends List', () => {
      const response = apiGet(ENDPOINTS.FRIENDS, 'friends_list');
      checkResponse(response, {
        'GET friends status is 200': (r) => r.status === 200,
        'GET friends returns array': (r) => {
          const body = parseJsonBody(r);
          return Array.isArray(body);
        },
      });
      thinkTime(0.3, 0.6);
    });

    // Note: Creating and accepting friendship requests requires coordination
    // between test users, which is complex in load testing scenarios.
    // The above read operations cover the main performance concerns.
  });
}
