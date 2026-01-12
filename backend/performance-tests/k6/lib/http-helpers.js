import http from 'k6/http';
import {check, sleep} from 'k6';
import {Trend, Counter, Rate} from 'k6/metrics';
import {API_BASE_URL} from '../config/constants.js';
import {getAuthHeaders} from './auth.js';

// Pre-declare all endpoint tags used in scenarios
const ENDPOINT_TAGS = [
    // appointments
    'appointments_list',
    'appointments_list_filtered',
    'appointments_create',
    'appointments_get',
    'appointments_update',
    'appointments_confirm',
    'appointments_delete',
    'appointments_cancel',
    // groups
    'groups_list',
    'groups_search',
    'groups_create',
    'groups_users_list',
    'groups_update',
    'groups_delete',
    // user
    'user_create',
    'user_get',
    'user_update',
    'user_reset',
    // participants
    'participants_setup_create',
    'participants_list',
    'participants_status',
    'participants_vote',
    'participants_cleanup_delete',
    'participants_approve',
    // messages
    'messages_setup_create',
    'messages_list',
    'messages_send',
    'messages_send_batch',
    'messages_verify',
    'messages_cleanup_delete',
    // friendships
    'friendships_suggestions',
    'friendships_search',
    'friendships_incoming',
    'friendships_outgoing',
    'friends_list',
    'friendships_suggestions_search',
    // settings
    'settings_get',
    'settings_update',
    'settings_verify',
    // push
    'push_public_key',
    'push_subscribe',
    'push_subscriptions_list',
    'push_unsubscribe',
    'push_status'
];

// Custom metrics per endpoint (pre-declared in init context)
const endpointMetrics = {};
for (const tag of ENDPOINT_TAGS) {
    endpointMetrics[tag] = {
        duration: new Trend(`http_req_duration_${tag}`, true),
        requests: new Counter(`http_reqs_${tag}`),
        failures: new Rate(`http_req_failed_${tag}`),
    };
}

function getOrCreateMetrics(endpointName) {
    if (!endpointMetrics[endpointName]) {
        console.warn(`Unknown endpoint tag: ${endpointName} - add it to ENDPOINT_TAGS`);
        return null;
    }
    return endpointMetrics[endpointName];
}

export function apiGet(endpoint, endpointTag, userIndex) {
    const url = `${API_BASE_URL}${endpoint}`;
    const params = {
        headers: getAuthHeaders(userIndex),
        tags: {endpoint: endpointTag},
    };

    const response = http.get(url, params);
    const metrics = getOrCreateMetrics(endpointTag);

    if (metrics) {
        metrics.duration.add(response.timings.duration);
        metrics.requests.add(1);
        metrics.failures.add(response.status >= 400);
    }

    return response;
}

export function apiPost(endpoint, body, endpointTag, userIndex) {
    const url = `${API_BASE_URL}${endpoint}`;
    const params = {
        headers: getAuthHeaders(userIndex),
        tags: {endpoint: endpointTag},
    };

    const payload = body ? JSON.stringify(body) : null;
    const response = http.post(url, payload, params);
    const metrics = getOrCreateMetrics(endpointTag);

    if (metrics) {
        metrics.duration.add(response.timings.duration);
        metrics.requests.add(1);
        metrics.failures.add(response.status >= 400);
    }

    return response;
}

export function apiPatch(endpoint, body, endpointTag, userIndex) {
    const url = `${API_BASE_URL}${endpoint}`;
    const params = {
        headers: getAuthHeaders(userIndex),
        tags: {endpoint: endpointTag},
    };

    const response = http.patch(url, JSON.stringify(body), params);
    const metrics = getOrCreateMetrics(endpointTag);

    if (metrics) {
        metrics.duration.add(response.timings.duration);
        metrics.requests.add(1);
        metrics.failures.add(response.status >= 400);
    }

    return response;
}

export function apiPut(endpoint, body, endpointTag, userIndex) {
    const url = `${API_BASE_URL}${endpoint}`;
    const params = {
        headers: getAuthHeaders(userIndex),
        tags: {endpoint: endpointTag},
    };

    const response = http.put(url, JSON.stringify(body), params);
    const metrics = getOrCreateMetrics(endpointTag);

    if (metrics) {
        metrics.duration.add(response.timings.duration);
        metrics.requests.add(1);
        metrics.failures.add(response.status >= 400);
    }

    return response;
}

export function apiDelete(endpoint, endpointTag, userIndex) {
    const url = `${API_BASE_URL}${endpoint}`;
    const params = {
        headers: getAuthHeaders(userIndex),
        tags: {endpoint: endpointTag},
    };

    const response = http.del(url, null, params);
    const metrics = getOrCreateMetrics(endpointTag);

    if (metrics) {
        metrics.duration.add(response.timings.duration);
        metrics.requests.add(1);
        metrics.failures.add(response.status >= 400);
    }

    return response;
}

export function checkResponse(response, checks, name) {
    return name ? check(response, checks, {name}) : check(response, checks);
}

export function thinkTime(min = 0.5, max = 2) {
    sleep(Math.random() * (max - min) + min);
}

export function parseJsonBody(response) {
    try {
        return JSON.parse(response.body);
    } catch (e) {
        return null;
    }
}
