const { test, describe } = require('node:test');
const assert = require('node:assert/strict');
const core = require('../public/sw-core.js');

const BERLIN = { timeZone: 'Europe/Berlin' };

describe('Token-Restlaufzeit', () => {
  test('expiresInMs rechnet Unix-Sekunden in Millisekunden Restlaufzeit um', () => {
    assert.equal(core.expiresInMs('1000', 400_000), 600_000);
  });
  test('expiresInMs liefert null ohne Cookie oder Wert', () => {
    assert.equal(core.expiresInMs(undefined, 0), null);
    assert.equal(core.expiresInMs('', 0), null);
  });
  test('shouldRefresh: fehlend, abgelaufen oder unter 5 s', () => {
    assert.equal(core.shouldRefresh(null), true);
    assert.equal(core.shouldRefresh(0), true);
    assert.equal(core.shouldRefresh(-1), true);
    assert.equal(core.shouldRefresh(4_999), true);
    assert.equal(core.shouldRefresh(5_000), false);
  });
  test('refreshDelayMs: 30 s vor Ablauf, nie negativ; null bei ungültigem Token', () => {
    assert.equal(core.refreshDelayMs(120_000), 90_000);
    assert.equal(core.refreshDelayMs(10_000), 0);
    assert.equal(core.refreshDelayMs(null), null);
    assert.equal(core.refreshDelayMs(0), null);
    assert.equal(core.refreshDelayMs(-5), null);
  });
});

describe('classifyRequest', () => {
  test('ordnet Pfade zu', () => {
    assert.equal(core.classifyRequest('/agenda'), 'page');
    assert.equal(core.classifyRequest('/main.js'), 'page');
    assert.equal(core.classifyRequest('/api/auth/isLoggedIn'), 'is-logged-in');
    assert.equal(core.classifyRequest('/api/auth/login'), 'auth');
    assert.equal(core.classifyRequest('/api/auth/refresh'), 'auth');
    assert.equal(core.classifyRequest('/api/v2/appointments'), 'api');
    assert.equal(core.classifyRequest('/api/config'), 'api');
  });
});

function fakeRequest(url = 'https://x.test/api/v2/a') {
  return { url, clone: () => fakeRequest(url), id: Symbol() };
}

describe('createTokenRefresher', () => {
  test('dedupliziert gleichzeitige Aufrufe und gibt danach wieder frei', async () => {
    let calls = 0;
    let release;
    const gate = new Promise((r) => (release = r));
    const settled = [];
    const refresh = core.createTokenRefresher({
      fetch: async (url, init) => {
        calls++;
        assert.equal(url, '/api/auth/refresh');
        assert.equal(init.method, 'POST');
        await gate;
        return { ok: true };
      },
      onSettled: () => settled.push(1),
    });
    const a = refresh();
    const b = refresh();
    assert.equal(a, b);
    release();
    await Promise.all([a, b]);
    assert.equal(calls, 1);
    assert.equal(settled.length, 1);
    await refresh();
    assert.equal(calls, 2);
  });
  test('wirft token_refresh_failed bei nicht-ok und gibt trotzdem frei', async () => {
    let calls = 0;
    const refresh = core.createTokenRefresher({
      fetch: async () => (++calls, { ok: false }),
      onSettled: () => {},
    });
    await assert.rejects(refresh(), /token_refresh_failed/);
    await assert.rejects(refresh(), /token_refresh_failed/);
    assert.equal(calls, 2);
  });
});

describe('createRequestHandler', () => {
  function setup({ expiresIn, refresh = async () => {}, responses = [] }) {
    const fetched = [];
    const queue = [...responses];
    const handle = core.createRequestHandler({
      fetch: async (req) => {
        fetched.push(req);
        return queue.shift() ?? new Response('ok', { status: 200 });
      },
      getExpiresIn: async () => expiresIn,
      refreshTokens: refresh,
    });
    return { handle, fetched };
  }

  test('Nicht-API-Anfragen gehen unverändert durch', async () => {
    const { handle, fetched } = setup({ expiresIn: null });
    const req = fakeRequest('https://x.test/agenda');
    await handle(req);
    assert.deepEqual(fetched, [req]);
  });

  test('isLoggedIn: 204 ohne Server-Roundtrip bei gültigem Token', async () => {
    const { handle, fetched } = setup({ expiresIn: 60_000 });
    const res = await handle(fakeRequest('https://x.test/api/auth/isLoggedIn'));
    assert.equal(res.status, 204);
    assert.equal(fetched.length, 0);
  });

  test('isLoggedIn: erneuert bei < 5 s, 204 bei Erfolg, 401 bei Fehlschlag', async () => {
    let refreshed = 0;
    const ok = setup({ expiresIn: 1_000, refresh: async () => void refreshed++ });
    assert.equal((await ok.handle(fakeRequest('https://x.test/api/auth/isLoggedIn'))).status, 204);
    assert.equal(refreshed, 1);
    const failing = setup({
      expiresIn: null,
      refresh: async () => {
        throw new Error('token_refresh_failed');
      },
    });
    assert.equal(
      (await failing.handle(fakeRequest('https://x.test/api/auth/isLoggedIn'))).status,
      401,
    );
  });

  test('Auth-Routen werden nicht abgefangen', async () => {
    let refreshed = 0;
    const { handle, fetched } = setup({ expiresIn: null, refresh: async () => void refreshed++ });
    await handle(fakeRequest('https://x.test/api/auth/refresh'));
    assert.equal(fetched.length, 1);
    assert.equal(refreshed, 0);
  });

  test('API: proaktiver Refresh bei < 5 s, dann eine Anfrage', async () => {
    let refreshed = 0;
    const { handle, fetched } = setup({ expiresIn: 2_000, refresh: async () => void refreshed++ });
    const res = await handle(fakeRequest());
    assert.equal(res.status, 200);
    assert.equal(refreshed, 1);
    assert.equal(fetched.length, 1);
  });

  test('API: fehlgeschlagener proaktiver Refresh sendet die Anfrage trotzdem', async () => {
    const { handle, fetched } = setup({
      expiresIn: null,
      refresh: async () => {
        throw new Error('x');
      },
    });
    assert.equal((await handle(fakeRequest())).status, 200);
    assert.equal(fetched.length, 1);
  });

  test('API: 401 löst genau einen Refresh und einen Retry mit dem Klon aus', async () => {
    let refreshed = 0;
    const { handle, fetched } = setup({
      expiresIn: 60_000,
      refresh: async () => void refreshed++,
      responses: [new Response(null, { status: 401 }), new Response('neu', { status: 200 })],
    });
    const req = fakeRequest();
    const res = await handle(req);
    assert.equal(res.status, 200);
    assert.equal(refreshed, 1);
    assert.equal(fetched.length, 2);
    assert.equal(fetched[0], req);
    assert.notEqual(fetched[1], req);
  });

  test('API: 401 mit fehlgeschlagenem Refresh liefert die 401-Antwort', async () => {
    const unauthorized = new Response(null, { status: 401 });
    const { handle } = setup({
      expiresIn: 60_000,
      refresh: async () => {
        throw new Error('token_refresh_failed');
      },
      responses: [unauthorized],
    });
    assert.equal(await handle(fakeRequest()), unauthorized);
  });
});

describe('createRefreshScheduler', () => {
  function setup(expiresIn) {
    const timers = [];
    const cleared = [];
    let refreshed = 0;
    const scheduler = core.createRefreshScheduler({
      getExpiresIn: async () => expiresIn,
      refresh: async () => void refreshed++,
      setTimeout: (fn, ms) => (timers.push({ fn, ms }), timers.length),
      clearTimeout: (id) => cleared.push(id),
    });
    return { scheduler, timers, cleared, refreshed: () => refreshed };
  }

  test('setzt Timer 30 s vor Ablauf und ersetzt einen vorhandenen', async () => {
    const t = setup(100_000);
    await t.scheduler();
    await t.scheduler();
    assert.deepEqual(t.timers.map((x) => x.ms), [70_000, 70_000]);
    assert.deepEqual(t.cleared, [undefined, 1]);
  });
  test('ohne gültiges Token wird nichts geplant', async () => {
    const t = setup(null);
    await t.scheduler();
    assert.equal(t.timers.length, 0);
  });
  test('Timer löst Refresh aus', async () => {
    const t = setup(100_000);
    await t.scheduler();
    t.timers[0].fn();
    assert.equal(t.refreshed(), 1);
  });
});

describe('Datumsformatierung', () => {
  test('formatDate: Tag.Monat.Jahr', () => {
    assert.equal(core.formatDate('2026-10-21T18:00:00+02:00', BERLIN), '21.10.2026');
  });
  test('formatTimeRange am selben Tag', () => {
    const text = core.formatTimeRange('2026-10-21T18:00:00+02:00', '2026-10-21T20:30:00+02:00', BERLIN);
    assert.match(text, /^Mi\.?,? 21\.10\.2026,? 18:00 bis 20:30 Uhr$/);
  });
  test('formatTimeRange über mehrere Tage', () => {
    const text = core.formatTimeRange('2026-10-21T18:00:00+02:00', '2026-10-22T09:00:00+02:00', BERLIN);
    assert.match(text, /^Mi\.?,? 21\.10\.2026,? 18:00 Uhr bis Do\.?,? 22\.10\.2026,? 09:00 Uhr$/);
  });
});

describe('Texte', () => {
  test('getStatusText', () => {
    assert.equal(core.getStatusText('APPROVED'), 'Zugesagt');
    assert.equal(core.getStatusText('REJECTED'), 'Abgesagt');
    assert.equal(core.getStatusText('PENDING'), 'Ausstehend');
    assert.equal(core.getStatusText('X'), 'X');
  });
});

const appointment = {
  id: 'a1',
  name: 'Gruppenstunde',
  start: '2026-10-21T18:00:00+02:00',
  end: '2026-10-21T20:00:00+02:00',
  minimal_attendees: 4,
};
const withAppointment = (type, extra = {}) => ({
  type,
  appointment: JSON.stringify(appointment),
  ...extra,
});
const actionIds = (n) => n.options.actions.map((a) => a.action);

describe('buildNotification', () => {
  const build = (data, status = 'PENDING') => core.buildNotification(data, status, BERLIN);

  test('Standardwerte', () => {
    const n = build({ type: 'WAS_ANDERES' });
    assert.equal(n.title, 'Neue Benachrichtigung');
    assert.equal(n.options.body, 'Du hast eine neue Nachricht erhalten.');
    assert.equal(n.options.tag, 'WAS_ANDERES');
    assert.deepEqual(n.options.vibrate, [200, 100, 200]);
    assert.equal(n.options.requireInteraction, false);
  });
  test('generisch mit title/body', () => {
    const n = build({ type: 'X', title: 'T', body: 'B' });
    assert.equal(n.title, 'T');
    assert.equal(n.options.body, 'B');
  });
  test('SURVEY_INVITATION', () => {
    const n = build({ type: 'SURVEY_INVITATION', survey: 's9' });
    assert.equal(n.title, 'Hilf mit Chronos zu verbessern - dauert keine Minute');
    assert.equal(n.options.tag, 'survey-s9');
  });
  test('APPOINTMENT_MOVED: Aktionen je nach eigenem Status', () => {
    const data = withAppointment('APPOINTMENT_MOVED', { acting_user_name: 'Max' });
    const n = build(data, 'APPROVED');
    assert.equal(n.title, 'Max hat Gruppenstunde verschoben');
    assert.match(n.options.body, /^Gruppenstunde geht jetzt vom .* 18:00 bis 20:00 Uhr$/);
    assert.equal(n.options.tag, 'appointment-moved-a1');
    assert.deepEqual(actionIds(n), ['reject']);
    assert.deepEqual(actionIds(build(data, 'REJECTED')), ['approve']);
    assert.deepEqual(actionIds(build(data, 'PENDING')), ['approve', 'reject']);
  });
  test('APPOINTMENT_CANCELLED', () => {
    const n = build(withAppointment('APPOINTMENT_CANCELLED', { who_cancelled: 'Anna' }));
    assert.equal(n.title, 'Gruppenstunde hat abgesagt');
    assert.equal(n.options.body, '"Gruppenstunde" wurde von Anna abgesagt.');
    assert.equal(n.options.tag, 'appointment-cancelled-a1');
  });
  test('NEW_PARTICIPANT', () => {
    const n = build(withAppointment('NEW_PARTICIPANT', { new_attendee: 'Eva', acting_user_name: 'Max' }));
    assert.equal(n.title, 'Neuer Teilnehmer');
    assert.equal(n.options.body, 'Max hat Eva zu Gruppenstunde am 21.10.2026 hinzugefügt');
    assert.equal(n.options.tag, 'new-attendee-a1');
  });
  test('Gruppen-Benachrichtigungen', () => {
    const group = JSON.stringify({ id: 'g1', name: 'Wölflinge' });
    const added = build({ type: 'NEW_GROUP_MEMBER', group, new_member: 'Eva' });
    assert.equal(added.options.body, 'Eva wurde der Gruppe "Wölflinge" hinzugefügt.');
    assert.equal(added.options.tag, 'new-member-g1');
    const self = build({ type: 'ADDED_TO_GROUP', group });
    assert.equal(self.title, 'Zu Gruppe hinzugefügt');
    assert.equal(self.options.body, 'Du wurdest zur Gruppe "Wölflinge" hinzugefügt.');
    assert.equal(self.options.tag, 'added-to-group-g1');
    const left = build({ type: 'GROUP_MEMBER_LEFT', group, old_member: 'Tom' });
    assert.equal(left.title, 'Mitglied hat Gruppe verlassen');
    assert.equal(left.options.body, 'Tom hat die Gruppe "Wölflinge" verlassen.');
    assert.equal(left.options.tag, 'member-left-g1');
  });
  test('PARTICIPATION_STATUS_CHANGED', () => {
    const n = build(
      withAppointment('PARTICIPATION_STATUS_CHANGED', {
        new_participation_status: 'REJECTED',
        user_name: 'Tom',
      }),
    );
    assert.equal(n.options.body, 'Tom hat den Status für "Gruppenstunde" am 21.10.2026 auf "Abgesagt" geändert.');
    assert.equal(n.options.tag, 'status-changed-a1');
  });
  test('APPOINTMENT_PARTICIPATION_INVALID', () => {
    const data = withAppointment('APPOINTMENT_PARTICIPATION_INVALID', {
      approved_participation: 2,
      rejected_participation: 1,
      pending_participation: 3,
    });
    const n = build(data, 'PENDING');
    assert.equal(n.title, 'Nicht genug Teilnehmer (2 / 4)');
    assert.equal(
      n.options.body,
      '"Gruppenstunde" hat nicht genug Zusagen (2 zugesagt, 1 abgesagt, 3 ausstehend).',
    );
    assert.deepEqual(actionIds(n), []);
    const rejected = build(data, 'REJECTED');
    assert.match(rejected.options.body, / Du hast bisher abgesagt\. Falls du doch kannst/);
    assert.deepEqual(actionIds(rejected), ['approve']);
    assert.equal(rejected.options.tag, 'not-enough-a1');
  });
  test('PARTICIPATION_STATUS_PENDING', () => {
    const n = build(withAppointment('PARTICIPATION_STATUS_PENDING'));
    assert.equal(n.title, 'Deine Rückmeldung fehlt noch');
    assert.equal(n.options.body, 'Deine Rückmeldung zu Gruppenstunde am 21.10.2026 fehlt noch.');
    assert.deepEqual(actionIds(n), ['approve', 'reject']);
    assert.equal(n.options.tag, 'pending-a1');
  });
  test('PARTICIPATION_REMINDER', () => {
    const n = build(withAppointment('PARTICIPATION_REMINDER'), 'APPROVED');
    assert.equal(n.title, 'Erinnerung: "Gruppenstunde" steht bald an!');
    assert.equal(n.options.body, 'Du hast Zugesagt');
    assert.equal(n.options.tag, 'reminder-a1');
  });
  test('PARTICIPATION_STATUS_RECHECK', () => {
    const approved = build(withAppointment('PARTICIPATION_STATUS_RECHECK', { participation_status: 'APPROVED' }));
    assert.equal(approved.title, 'Rückmeldung überprüfen');
    assert.equal(
      approved.options.body,
      'Du hast bisher Zugesagt zu Gruppenstunde am 21.10.2026. Ist das noch aktuell?',
    );
    assert.deepEqual(actionIds(approved), ['none', 'reject']);
    const rejected = build(withAppointment('PARTICIPATION_STATUS_RECHECK', { participation_status: 'REJECTED' }));
    assert.deepEqual(actionIds(rejected), ['none', 'approve']);
    assert.equal(rejected.options.tag, 'recheck-a1');
  });
  test('Daten bleiben für den Klick-Handler erhalten', () => {
    const data = withAppointment('PARTICIPATION_REMINDER');
    assert.equal(build(data).options.data, data);
  });
  test('needsAttendanceStatus nur mit Termin', () => {
    assert.equal(core.needsAttendanceStatus({ type: 'X' }), false);
    assert.equal(core.needsAttendanceStatus(withAppointment('X')), true);
  });
});

describe('Klick-Ziel und Aktion', () => {
  test('notificationTarget', () => {
    assert.equal(core.notificationTarget(undefined), '/');
    assert.equal(core.notificationTarget({ type: 'SURVEY_INVITATION', survey: 's9' }), '/survey/s9');
    assert.equal(core.notificationTarget(withAppointment('X')), '/appointment/a1');
    assert.equal(core.notificationTarget({ group: JSON.stringify({ id: 'g1' }) }), '/groups/g1');
    assert.equal(core.notificationTarget({}), '/');
  });
  test('notificationTarget wirft bei ungültigem JSON', () => {
    assert.throws(() => core.notificationTarget({ appointment: '{' }));
  });
  test('statusForAction', () => {
    assert.equal(core.statusForAction('approve'), 'APPROVED');
    assert.equal(core.statusForAction('reject'), 'REJECTED');
    assert.equal(core.statusForAction('none'), null);
    assert.equal(core.statusForAction(''), null);
  });
  test('appointmentIdOf liest die ID aus dem JSON-String', () => {
    assert.equal(core.appointmentIdOf(withAppointment('X')), 'a1');
    assert.equal(core.appointmentIdOf({}), undefined);
  });
  test('attendanceEndpoint', () => {
    assert.equal(core.attendanceEndpoint('a1'), '/api/v2/appointments/a1/participation');
  });
});
