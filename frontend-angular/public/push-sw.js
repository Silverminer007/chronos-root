// Service Worker: Token-Erneuerung für /api/*, synthetisches isLoggedIn, Sitzungsende, Web-Push.
// Die reinen Funktionen liegen in sw-core.js (mit Node getestet).
importScripts('/sw-core.js');

const {
  ICON,
  BADGE,
  expiresInMs,
  createTokenRefresher,
  createRequestHandler,
  createRefreshScheduler,
  needsAttendanceStatus,
  buildNotification,
  notificationTarget,
  statusForAction,
  appointmentIdOf,
  attendanceEndpoint,
} = self.ChronosSw;

const EXPIRES_COOKIE = 'kc_expires';

/**
 * Meldet einen Fehler des Service Workers an alle Fenster.
 * Der Client kann die Nachrichten (`SW_ERROR`) an ein Fehler-Monitoring weiterleiten.
 */
function reportSwError(error, context) {
  const payload = {
    type: 'SW_ERROR',
    context: context,
    message: error && error.message ? error.message : String(error),
    stack: error && error.stack ? error.stack : undefined,
  };
  self.clients.matchAll({ includeUncontrolled: true, type: 'window' }).then((clients) => {
    clients.forEach((client) => client.postMessage(payload));
  });
}

self.addEventListener('error', (event) => {
  reportSwError(event.error || new Error(event.message), 'unhandled-error');
});

self.addEventListener('unhandledrejection', (event) => {
  reportSwError(
    event.reason instanceof Error ? event.reason : new Error(String(event.reason)),
    'unhandled-rejection',
  );
});

// --- Token-Erneuerung ---------------------------------------------------------------------

async function getTokenExpiresIn() {
  const cookie = await cookieStore.get(EXPIRES_COOKIE);
  return expiresInMs(cookie && cookie.value, Date.now());
}

const refreshTokens = createTokenRefresher({
  fetch: (url, init) => fetch(url, init),
  onSettled: () => scheduleRefresh().catch((err) => reportSwError(err, 'schedule-refresh')),
});

const scheduleRefresh = createRefreshScheduler({
  getExpiresIn: getTokenExpiresIn,
  refresh: refreshTokens,
  setTimeout: (fn, ms) => setTimeout(fn, ms),
  clearTimeout: (id) => clearTimeout(id),
  onError: (err) => reportSwError(err, 'scheduled-refresh'),
});

const handleRequest = createRequestHandler({
  fetch: (request) => fetch(request),
  getExpiresIn: getTokenExpiresIn,
  refreshTokens,
});

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    clients.claim().then(() =>
      scheduleRefresh().catch((err) => reportSwError(err, 'activate-schedule-refresh')),
    ),
  );
});

self.addEventListener('fetch', (event) => {
  event.respondWith(handleRequest(event.request));
});

cookieStore.addEventListener('change', ({ deleted, changed }) => {
  // kc_expires vom Server gelöscht: Sitzung ungültig, alle offenen Tabs benachrichtigen.
  if (deleted.some((c) => c.name === EXPIRES_COOKIE)) {
    self.clients.matchAll().then((windows) => {
      windows.forEach((client) => client.postMessage({ type: 'SESSION_EXPIRED' }));
    });
  }
  if (changed.some((c) => c.name === EXPIRES_COOKIE)) {
    scheduleRefresh().catch((err) => reportSwError(err, 'cookie-change-schedule-refresh'));
  }
});

// --- Push ---------------------------------------------------------------------------------

async function getOwnAttendanceStatus(appointmentId) {
  const response = await fetch(`/api/v2/appointments/${appointmentId}/participants/status`, {
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return (await response.json()).status;
}

async function handleNotification(data) {
  let ownStatus = 'PENDING';
  if (needsAttendanceStatus(data)) {
    try {
      ownStatus = await getOwnAttendanceStatus(JSON.parse(data.appointment).id);
    } catch (error) {
      // Ohne Status trotzdem benachrichtigen (Browser verlangen für jede Push-Nachricht eine Anzeige).
      reportSwError(error, 'push-own-status');
    }
  }
  const { title, options } = buildNotification(data, ownStatus);
  return self.registration.showNotification(title, options);
}

self.addEventListener('push', (event) => {
  if (!event.data) return;
  try {
    event.waitUntil(handleNotification(event.data.json()));
  } catch (error) {
    reportSwError(error, 'push-handler');
  }
});

// --- Klick --------------------------------------------------------------------------------

async function updateAttendanceStatus(appointmentId, status) {
  const response = await fetch(attendanceEndpoint(appointmentId, status), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
}

async function answerFromNotification(data, status) {
  try {
    await updateAttendanceStatus(appointmentIdOf(data), status);
    await self.registration.showNotification('Status aktualisiert', {
      body: status === 'APPROVED' ? 'Du hast zugesagt' : 'Du hast abgesagt',
      icon: ICON,
      badge: BADGE,
      tag: 'status-update-success',
      requireInteraction: false,
    });
  } catch (error) {
    reportSwError(error, 'notificationclick-update-status');
    await self.registration.showNotification('Fehler', {
      body: 'Status konnte nicht aktualisiert werden. Bitte öffne die App.',
      icon: ICON,
      badge: BADGE,
      tag: 'status-update-error',
    });
  }
}

async function openOrFocus(urlToOpen) {
  const windows = await clients.matchAll({ type: 'window', includeUncontrolled: true });
  for (const client of windows) {
    if (client.url.includes(self.registration.scope) && 'focus' in client) {
      await client.focus();
      if ('navigate' in client) await client.navigate(urlToOpen);
      return;
    }
  }
  if (clients.openWindow) await clients.openWindow(urlToOpen);
}

self.addEventListener('notificationclick', (event) => {
  const data = event.notification.data;
  event.notification.close();

  const status = statusForAction(event.action);
  if (status) {
    event.waitUntil(answerFromNotification(data, status));
    return;
  }

  let urlToOpen = '/';
  try {
    urlToOpen = notificationTarget(data);
  } catch (error) {
    reportSwError(error, 'notificationclick-parse');
  }
  event.waitUntil(openOrFocus(urlToOpen));
});
