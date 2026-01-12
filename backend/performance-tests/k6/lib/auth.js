import http from 'k6/http';
import { check } from 'k6';
import {
  KEYCLOAK_URL,
  KEYCLOAK_REALM,
  KEYCLOAK_CLIENT_ID,
  KEYCLOAK_CLIENT_SECRET,
  TEST_USERS,
} from '../config/constants.js';

// Token cache per VU to avoid re-authentication
const tokenCache = {};

export function getToken(userIndex) {
  // Use VU number to assign user
  const vuId = __VU || 0;
  const effectiveIndex = userIndex !== undefined ? userIndex : vuId % TEST_USERS.length;
  const user = TEST_USERS[effectiveIndex];

  // Check if we have a valid cached token
  const cacheKey = user.username;
  const cached = tokenCache[cacheKey];
  if (cached && cached.expiresAt > Date.now()) {
    return cached.token;
  }

  // Acquire new token
  const tokenUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;

  const payload = {
    grant_type: 'password',
    client_id: KEYCLOAK_CLIENT_ID,
    client_secret: KEYCLOAK_CLIENT_SECRET,
    username: user.username,
    password: user.password,
    scope: 'openid',
  };

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    tags: { name: 'token_acquisition' },
  };

  const response = http.post(tokenUrl, payload, params);

  check(response, {
    'token acquired': (r) => r.status === 200,
    'token has access_token': (r) => {
      try {
        return JSON.parse(r.body).access_token !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (response.status === 200) {
    const tokenData = JSON.parse(response.body);
    const token = tokenData.access_token;
    const expiresIn = tokenData.expires_in || 300;

    // Cache with some buffer time (refresh 60s before expiry)
    tokenCache[cacheKey] = {
      token: token,
      expiresAt: Date.now() + (expiresIn - 60) * 1000,
    };

    return token;
  }

  console.error(`Failed to acquire token for ${user.username}: ${response.status} - ${response.body}`);
  return null;
}

export function getAuthHeaders(userIndex) {
  const token = getToken(userIndex);
  if (!token) {
    return { 'Content-Type': 'application/json' };
  }
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

export function getCurrentUser() {
  const vuId = __VU || 0;
  return TEST_USERS[vuId % TEST_USERS.length];
}

export function getUserIndex() {
  const vuId = __VU || 0;
  return vuId % TEST_USERS.length;
}
