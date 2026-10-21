/**
 * Reine Funktionen des Service Workers (`push-sw.js`), getrennt von den Browser-APIs,
 * damit sie mit dem Node-Testrunner getestet werden können (`sw-tests/`).
 * Im Service Worker per `importScripts` geladen (globales `ChronosSw`), in Node per `require`.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  } else {
    root.ChronosSw = api;
  }
})(typeof self !== 'undefined' ? self : this, function () {
  const REFRESH_URL = '/api/auth/refresh';
  const REFRESH_THRESHOLD_MS = 5_000;
  const PROACTIVE_REFRESH_LEAD_MS = 30_000;
  const LOCALE = 'de-DE';
  const ICON = '/icons/icon.png';
  const BADGE = '/badge.svg';
  const VIBRATE = [200, 100, 200];

  const APPROVE = { action: 'approve', title: '✓ Zusagen' };
  const REJECT = { action: 'reject', title: '✗ Absagen' };
  const KEEP = { action: 'none', title: '✓ Ja' };

  // --- Token-Laufzeit ---------------------------------------------------------------------

  /** Restlaufzeit in ms aus dem Wert des Cookies `kc_expires` (Unix-Sekunden); `null` ohne Wert. */
  function expiresInMs(cookieValue, now) {
    if (!cookieValue) return null;
    return cookieValue * 1000 - now;
  }

  /** Erneuern, wenn die Laufzeit unbekannt, abgelaufen oder kürzer als 5 s ist. */
  function shouldRefresh(expiresIn) {
    return !expiresIn || expiresIn < REFRESH_THRESHOLD_MS;
  }

  /** Wartezeit bis zur proaktiven Erneuerung (30 s vor Ablauf); `null`, wenn kein gültiges Token. */
  function refreshDelayMs(expiresIn) {
    if (!expiresIn || expiresIn <= 0) return null;
    return Math.max(0, expiresIn - PROACTIVE_REFRESH_LEAD_MS);
  }

  // --- Request-Abfangen -------------------------------------------------------------------

  /** `page` (nicht abfangen), `is-logged-in` (synthetisch), `auth` (durchreichen) oder `api`. */
  function classifyRequest(pathname) {
    if (!pathname.startsWith('/api')) return 'page';
    if (pathname === '/api/auth/isLoggedIn') return 'is-logged-in';
    if (pathname.startsWith('/api/auth/')) return 'auth';
    return 'api';
  }

  /** Erneuerung mit Deduplizierung: gleichzeitige Aufrufe teilen sich ein Promise. */
  function createTokenRefresher({ fetch, onSettled }) {
    let inflight = null;
    return function refreshTokens() {
      if (inflight) return inflight;
      inflight = fetch(REFRESH_URL, { method: 'POST' })
        .then((res) => {
          if (!res.ok) throw new Error('token_refresh_failed');
          return res;
        })
        .finally(() => {
          inflight = null;
          onSettled();
        });
      return inflight;
    };
  }

  function createRequestHandler({ fetch, getExpiresIn, refreshTokens }) {
    return async function handleRequest(request) {
      const kind = classifyRequest(new URL(request.url).pathname);
      if (kind === 'page' || kind === 'auth') return fetch(request);

      if (kind === 'is-logged-in') {
        if (shouldRefresh(await getExpiresIn())) {
          try {
            await refreshTokens();
          } catch {
            return new Response(null, { status: 401 });
          }
        }
        return new Response(null, { status: 204 });
      }

      if (shouldRefresh(await getExpiresIn())) {
        try {
          await refreshTokens();
        } catch {
          // Proaktive Erneuerung fehlgeschlagen: Anfrage trotzdem senden, der 401-Zweig versucht es erneut.
        }
      }
      const retry = request.clone();
      const response = await fetch(request);
      if (response.status !== 401) return response;
      try {
        await refreshTokens();
        return await fetch(retry);
      } catch {
        return response;
      }
    };
  }

  /** Plant die Erneuerung 30 s vor Ablauf; ein neuer Aufruf ersetzt den vorhandenen Timer. */
  function createRefreshScheduler({ getExpiresIn, refresh, setTimeout, clearTimeout, onError }) {
    let timerId;
    return async function scheduleRefresh() {
      const delay = refreshDelayMs(await getExpiresIn());
      if (delay === null) return null;
      clearTimeout(timerId);
      timerId = setTimeout(() => {
        refresh().catch((err) => onError && onError(err));
      }, delay);
      return delay;
    };
  }

  // --- Benachrichtigungen -----------------------------------------------------------------

  const dateTimeFormat = (options) =>
    new Intl.DateTimeFormat(LOCALE, {
      weekday: 'short',
      day: 'numeric',
      month: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      ...options,
    });

  /** Format `D` (Datum) wie bisher mit Luxon, jetzt über `Intl`; `options.timeZone` für Tests. */
  function formatDate(iso, options) {
    return new Intl.DateTimeFormat(LOCALE, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      ...options,
    }).format(new Date(iso));
  }

  function formatTime(date, options) {
    return new Intl.DateTimeFormat(LOCALE, { hour: '2-digit', minute: '2-digit', ...options }).format(date);
  }

  function formatTimeRange(start, end, options) {
    const startDate = new Date(start);
    const endDate = new Date(end);
    const startText = dateTimeFormat(options).format(startDate);
    if (formatDate(start, options) === formatDate(end, options)) {
      return `${startText} bis ${formatTime(endDate, options)} Uhr`;
    }
    return `${startText} Uhr bis ${dateTimeFormat(options).format(endDate)} Uhr`;
  }

  function getStatusText(status) {
    switch (status) {
      case 'APPROVED':
        return 'Zugesagt';
      case 'REJECTED':
        return 'Abgesagt';
      case 'PENDING':
        return 'Ausstehend';
      default:
        return status;
    }
  }

  function needsAttendanceStatus(data) {
    return Boolean(data.appointment);
  }

  /**
   * Baut Titel und Optionen für `showNotification`. `ownStatus` ist der eigene Teilnahmestatus
   * (nur relevant bei Terminen); `options` reicht `timeZone` an die Formatierung durch.
   */
  function buildNotification(data, ownStatus, options) {
    const type = data.type;
    let title = 'Chronos';
    let body = '';
    let tag = type;
    let actions = [];
    const appointment = data.appointment ? JSON.parse(data.appointment) : undefined;

    switch (type) {
      case 'SURVEY_INVITATION':
        title = 'Hilf mit Chronos zu verbessern - dauert keine Minute';
        body = 'Klicke einfach auf diese Benachrichtigung um 5 Fragen zu beantworten';
        tag = `survey-${data.survey}`;
        break;

      case 'APPOINTMENT_MOVED':
        title = `${data.acting_user_name} hat ${appointment.name} verschoben`;
        body = `${appointment.name} geht jetzt vom ${formatTimeRange(appointment.start, appointment.end, options)}`;
        tag = `appointment-moved-${appointment.id}`;
        if (ownStatus === 'APPROVED') {
          actions = [REJECT];
        } else if (ownStatus === 'REJECTED') {
          actions = [APPROVE];
        } else {
          actions = [APPROVE, REJECT];
        }
        break;

      case 'APPOINTMENT_CANCELLED':
        title = `${appointment.name} hat abgesagt`;
        body = `"${appointment.name}" wurde von ${data.who_cancelled} abgesagt.`;
        tag = `appointment-cancelled-${appointment.id}`;
        break;

      case 'NEW_PARTICIPANT':
        title = 'Neuer Teilnehmer';
        body = `${data.acting_user_name} hat ${data.new_attendee} zu ${appointment.name} am ${formatDate(appointment.start, options)} hinzugefügt`;
        tag = `new-attendee-${appointment.id}`;
        break;

      case 'NEW_GROUP_MEMBER': {
        const group = JSON.parse(data.group);
        title = 'Neues Gruppenmitglied';
        body = `${data.new_member} wurde der Gruppe "${group.name}" hinzugefügt.`;
        tag = `new-member-${group.id}`;
        break;
      }

      case 'ADDED_TO_GROUP': {
        const group = JSON.parse(data.group);
        title = 'Zu Gruppe hinzugefügt';
        body = `Du wurdest zur Gruppe "${group.name}" hinzugefügt.`;
        tag = `added-to-group-${group.id}`;
        break;
      }

      case 'GROUP_MEMBER_LEFT': {
        const group = JSON.parse(data.group);
        title = 'Mitglied hat Gruppe verlassen';
        body = `${data.old_member} hat die Gruppe "${group.name}" verlassen.`;
        tag = `member-left-${group.id}`;
        break;
      }

      case 'PARTICIPATION_STATUS_CHANGED':
        title = 'Teilnahmestatus geändert';
        body = `${data.user_name} hat den Status für "${appointment.name}" am ${formatDate(appointment.start, options)} auf "${getStatusText(data.new_participation_status)}" geändert.`;
        tag = `status-changed-${appointment.id}`;
        break;

      case 'APPOINTMENT_PARTICIPATION_INVALID':
        title = `Nicht genug Teilnehmer (${data.approved_participation} / ${appointment.minimal_attendees})`;
        body = `"${appointment.name}" hat nicht genug Zusagen (${data.approved_participation} zugesagt, ${data.rejected_participation} abgesagt, ${data.pending_participation} ausstehend).`;
        if (ownStatus === 'REJECTED') {
          body += ' Du hast bisher abgesagt. Falls du doch kannst, gib bitte so schnell wie möglich bescheid';
          actions = [APPROVE];
        }
        tag = `not-enough-${appointment.id}`;
        break;

      case 'PARTICIPATION_STATUS_PENDING':
        title = 'Deine Rückmeldung fehlt noch';
        body = `Deine Rückmeldung zu ${appointment.name} am ${formatDate(appointment.start, options)} fehlt noch.`;
        actions = [APPROVE, REJECT];
        tag = `pending-${appointment.id}`;
        break;

      case 'PARTICIPATION_REMINDER':
        title = `Erinnerung: "${appointment.name}" steht bald an!`;
        body = `Du hast ${getStatusText(ownStatus)}`;
        tag = `reminder-${appointment.id}`;
        break;

      case 'PARTICIPATION_STATUS_RECHECK':
        title = 'Rückmeldung überprüfen';
        body = `Du hast bisher ${getStatusText(data.participation_status)} zu ${appointment.name} am ${formatDate(appointment.start, options)}. Ist das noch aktuell?`;
        actions = [KEEP, data.participation_status === 'APPROVED' ? REJECT : APPROVE];
        tag = `recheck-${appointment.id}`;
        break;

      default:
        if (data.title && data.body) {
          title = data.title;
          body = data.body;
        } else {
          title = 'Neue Benachrichtigung';
          body = 'Du hast eine neue Nachricht erhalten.';
        }
    }

    return {
      title,
      options: {
        body,
        icon: ICON,
        badge: BADGE,
        tag,
        data,
        requireInteraction: false,
        vibrate: VIBRATE,
        actions,
      },
    };
  }

  // --- Klick-Behandlung -------------------------------------------------------------------

  /** Ziel-URL eines Klicks auf den Benachrichtigungskörper; wirft bei ungültigem JSON. */
  function notificationTarget(data) {
    if (!data) return '/';
    if (data.type === 'SURVEY_INVITATION') return `/survey/${data.survey}`;
    if (data.appointment) return `/appointment/${JSON.parse(data.appointment).id}`;
    if (data.group) return `/groups/${JSON.parse(data.group).id}`;
    return '/';
  }

  function statusForAction(action) {
    if (action === 'approve') return 'APPROVED';
    if (action === 'reject') return 'REJECTED';
    return null;
  }

  function appointmentIdOf(data) {
    return data && data.appointment ? JSON.parse(data.appointment).id : undefined;
  }

  function attendanceEndpoint(appointmentId, status) {
    const action = status === 'APPROVED' ? 'approve' : 'reject';
    return `/api/v2/appointments/${appointmentId}/participants/${action}`;
  }

  return {
    ICON,
    BADGE,
    expiresInMs,
    shouldRefresh,
    refreshDelayMs,
    classifyRequest,
    createTokenRefresher,
    createRequestHandler,
    createRefreshScheduler,
    formatDate,
    formatTimeRange,
    getStatusText,
    needsAttendanceStatus,
    buildNotification,
    notificationTarget,
    statusForAction,
    appointmentIdOf,
    attendanceEndpoint,
  };
});
