import { group } from 'k6';
import {
  apiGet,
  apiPost,
  apiDelete,
  checkResponse,
  thinkTime,
  parseJsonBody,
} from '../lib/http-helpers.js';
import { generatePushSubscription } from '../lib/data-generators.js';
import { ENDPOINTS } from '../config/constants.js';

export function testPush() {
  let subscriptionEndpoint = null;

  group('Push API', () => {
    // GET /api/v2/push/public-key
    group('Get Public Key', () => {
      const response = apiGet(ENDPOINTS.PUSH_PUBLIC_KEY, 'push_public_key');
      checkResponse(response, {
        'GET public key status is 200': (r) => r.status === 200,
        'GET public key returns string': (r) => {
          return r.body && r.body.length > 0;
        },
      });
      thinkTime(0.2, 0.4);
    });

    // POST /api/v2/push/subscribe
    group('Subscribe to Push', () => {
      const subscriptionData = generatePushSubscription();
      subscriptionEndpoint = subscriptionData.endpoint;
      const response = apiPost(ENDPOINTS.PUSH_SUBSCRIBE, subscriptionData, 'push_subscribe');
      // subscribe returns void (no content)
      checkResponse(response, {
        'POST subscribe status is 200 or 204': (r) => r.status === 200 || r.status === 204,
      });
      thinkTime(0.3, 0.5);
    });

    // GET /api/v2/push/status
    if (subscriptionEndpoint) {
      group('Get Push Status', () => {
        const response = apiGet(
          `${ENDPOINTS.PUSH_STATUS}?endpoint=${encodeURIComponent(subscriptionEndpoint)}`,
          'push_status'
        );
        checkResponse(response, {
          'GET push status is 200': (r) => r.status === 200,
          'GET push status shows subscribed': (r) => {
            const body = parseJsonBody(r);
            return body && body.subscribed === true;
          },
        });
        thinkTime(0.2, 0.4);
      });

      // DELETE /api/v2/push/unsubscribe
      group('Unsubscribe from Push', () => {
        const response = apiDelete(
          `${ENDPOINTS.PUSH_UNSUBSCRIBE}?endpoint=${encodeURIComponent(subscriptionEndpoint)}`,
          'push_unsubscribe'
        );
        checkResponse(response, {
          'DELETE unsubscribe status is 200 or 204': (r) => r.status === 200 || r.status === 204,
        });
        thinkTime(0.2, 0.4);
      });
    }
  });
}
