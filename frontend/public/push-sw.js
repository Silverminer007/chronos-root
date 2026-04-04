// public/push-sw.js
importScripts('https://cdn.jsdelivr.net/npm/luxon@3.4.4/build/global/luxon.min.js');

/**
 * Report an error from the service worker to all connected clients.
 * The client-side Sentry plugin listens for these messages and forwards them to Sentry.
 */
function reportSwError(error, context) {
    const payload = {
        type: 'SW_ERROR',
        context: context,
        message: error && error.message ? error.message : String(error),
        stack: error && error.stack ? error.stack : undefined,
    };
    self.clients.matchAll({includeUncontrolled: true, type: 'window'}).then(function (clients) {
        clients.forEach(function (client) {
            client.postMessage(payload);
        });
    });
}

// Catch unhandled errors and promise rejections in the service worker
self.addEventListener('error', function (event) {
    reportSwError(event.error || new Error(event.message), 'unhandled-error');
});

self.addEventListener('unhandledrejection', function (event) {
    reportSwError(event.reason instanceof Error ? event.reason : new Error(String(event.reason)), 'unhandled-rejection');
});

const API_BASE_URL = self.location.origin;

const DateTime = luxon.DateTime;

self.addEventListener('push', function (event) {
    if (!event.data) {
        console.log('Push-Nachricht ohne Daten empfangen');
        return;
    }

    try {
        const data = event.data.json();
        console.log('Push-Benachrichtigung empfangen:', data);

        const notificationPromise = handleNotification(data);
        event.waitUntil(notificationPromise);
    } catch (error) {
        console.error('Fehler beim Verarbeiten der Push-Nachricht:', error);
        reportSwError(error, 'push-handler');
    }
});

function formatTimeRange(start, end) {
    const startDateTime = DateTime.fromISO(start);
    const endDateTime = DateTime.fromISO(end);

    if (startDateTime.hasSame(endDateTime, 'day')) {
        return `${startDateTime.toFormat('EEE D t')} bis ${endDateTime.toFormat('HH:mm')} Uhr`;
    }
    return `${startDateTime.toFormat('EEE D t')} Uhr bis ${endDateTime.toFormat('EEE D t')} Uhr`;
}

function formatDate(start) {
    const startDateTime = DateTime.fromISO(start);

    return startDateTime.toFormat('D');
}

function getRoleName(roleId) {
    switch (roleId) {
        case "GUEST":
            return 'Gast';
        case "ATTENDANT":
            return 'Teilnehmer';
        case "RESPONSIBLE":
            return 'Verantwortlich';
        default:
            return "";
    }
}

async function handleNotification(data) {
    const type = data.type;
    let title = 'Chronos';
    let body = '';
    let icon = '/icon.png'; // Pfad zu deinem App-Icon
    let badge = '/badge.svg'; // Kleines Monochrom-Icon
    let tag = type;
    let notificationData = data;
    let actions = []; // Quick Actions

    const appointment = data.appointment ? JSON.parse(data.appointment) : undefined;
    const ownAttendanceStatus = appointment ? await getOwnAttendanceStatus(appointment.id) : 'PENDING';

    switch (type) {
        case 'SURVEY_INVITATION':
            const surveyId = data.survey;
            title = 'Hilf mit Chronos zu verbessern - dauert keine Minute';
            body = 'Klicke einfach auf diese Benachrichtigung um 5 Fragen zu beantworten';
            tag = `survey-${surveyId}`
            break;

        case 'APPOINTMENT_MOVED':
            title = `${data.acting_user_name} hat ${appointment.name} verschoben`;
            body = `${appointment.name} geht jetzt vom ${formatTimeRange(appointment.start, appointment.end)}`;
            tag = `appointment-moved-${appointment.id}`;
            if (ownAttendanceStatus === "APPROVED") {
                actions = [
                    {action: 'reject', title: '✗ Absagen', icon: '/icons/cross.png'}
                ];
            } else if (ownAttendanceStatus === "REJECTED") {
                actions = [
                    {action: 'approve', title: '✓ Zusagen', icon: '/icons/check.png'}
                ];
            } else {
                actions = [
                    {action: 'approve', title: '✓ Zusagen', icon: '/icons/check.png'},
                    {action: 'reject', title: '✗ Absagen', icon: '/icons/cross.png'}
                ];
            }
            break;

        case 'APPOINTMENT_CANCELLED':
            const whoCancelled = data.who_cancelled;
            title = `${appointment.name} hat abgesagt`;
            body = `"${appointment.name}" wurde von ${whoCancelled} abgesagt.`;
            tag = `appointment-cancelled-${appointment.id}`;
            break;

        case 'NEW_PARTICIPANT':
            const newAttendee = data.new_attendee;
            const actingUserName = data.acting_user_name;
            title = `Neuer Teilnehmer`;
            body = `${actingUserName} hat ${newAttendee} zu ${appointment.name} am ${formatDate(appointment.start)} hinzugefügt`;
            tag = `new-attendee-${appointment.id}`;
            break;

        case 'NEW_GROUP_MEMBER':
            const group = JSON.parse(data.group);
            const newMember = data.new_member;
            title = 'Neues Gruppenmitglied';
            body = `${newMember} wurde der Gruppe "${group.name}" hinzugefügt.`;
            tag = `new-member-${group.id}`;
            break;

        case 'ADDED_TO_GROUP':
            const addedGroup = JSON.parse(data.group);
            title = 'Zu Gruppe hinzugefügt';
            body = `Du wurdest zur Gruppe "${addedGroup.name}" hinzugefügt.`;
            tag = `added-to-group-${addedGroup.id}`;
            break;

        case 'GROUP_MEMBER_LEFT':
            const leftGroup = JSON.parse(data.group);
            const oldMember = data.old_member;
            title = 'Mitglied hat Gruppe verlassen';
            body = `${oldMember} hat die Gruppe "${leftGroup.name}" verlassen.`;
            tag = `member-left-${leftGroup.id}`;
            break;

        case 'PARTICIPATION_STATUS_CHANGED':
            const newStatus = data.new_participation_status;
            const userName = data.user_name;
            const statusText = getStatusText(newStatus);
            title = 'Teilnahmestatus geändert';
            body = `${userName} hat den Status für "${appointment.name}" am ${formatDate(appointment.start)} auf "${statusText}" geändert.`;
            tag = `status-changed-${appointment.id}`;
            break;

        case 'APPOINTMENT_PARTICIPATION_INVALID':
            const approved = data.approved_participation;
            const rejected = data.rejected_participation;
            const pending = data.pending_participation;
            title = `Nicht genug Teilnehmer (${approved} / ${appointment.minimal_attendees}`;
            body = `"${appointment.name}" hat nicht genug Zusagen (${approved} zugesagt, ${rejected} abgesagt, ${pending} ausstehend).`;
            if (ownAttendanceStatus === "REJECTED") {
                body += ' Du hast bisher abgesagt. Falls du doch kannst, gib bitte so schnell wie möglich bescheid';
                actions = [
                    {action: 'approve', title: '✓ Zusagen', icon: '/icons/check.png'}
                ];
            }
            tag = `not-enough-${appointment.id}`;
            break;

        case 'PARTICIPATION_STATUS_PENDING':
            title = `Deine Rückmeldung fehlt noch`;
            body = `Deine Rückmeldung zu ${appointment.name} am ${formatDate(appointment.start)} fehlt noch.`;
            actions = [
                {action: 'approve', title: '✓ Zusagen', icon: '/icons/check.png'},
                {action: 'reject', title: '✗ Absagen', icon: '/icons/cross.png'}
            ];
            tag = `pending-${appointment.id}`;
            break;

        case 'PARTICIPATION_REMINDER':
            title = `Erinnerung: "${appointment.name}" steht bald an!`;
            body = `Du hast ${getStatusText(ownAttendanceStatus)}`;
            tag = `reminder-${appointment.id}`;
            break;

        case 'PARTICIPATION_STATUS_RECHECK':
            const participationStatus = data.participation_status;
            const recheckStatusText = getStatusText(participationStatus);
            title = `Rückmeldung überprüfen`;
            body = `Du hast bisher ${recheckStatusText} zu ${appointment.name} am ${formatDate(appointment.start)}. Ist das noch aktuell?`;
            if (participationStatus === "APPROVED") {
                actions = [
                    {action: 'none', title: '✓ Ja', icon: '/icons/check.png'},
                    {action: 'reject', title: '✗ Absagen', icon: '/icons/cross.png'}
                ];
            } else {
                actions = [
                    {action: 'none', title: '✓ Ja', icon: '/icons/check.png'},
                    {action: 'approve', title: '✓ Zusagen', icon: '/icons/check.png'}
                ];
            }
            tag = `recheck-${appointment.id}`;
            break;

        default:
            // Fallback für generische Benachrichtigungen
            if (data.title && data.body) {
                title = data.title;
                body = data.body;
            } else {
                title = 'Neue Benachrichtigung';
                body = 'Du hast eine neue Nachricht erhalten.';
            }
    }

    const options = {
        body: body,
        icon: icon,
        badge: badge,
        tag: tag,
        data: notificationData,
        requireInteraction: false,
        vibrate: [200, 100, 200],
        actions: actions,
    };

    return self.registration.showNotification(title, options);
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

// Klick-Handler für Benachrichtigungen
self.addEventListener('notificationclick', function (event) {
    const action = event.action;
    const data = event.notification.data;

    if (action === 'approve' || action === 'reject') {
        event.notification.close();

        const appointmentId = data.appointment.id;
        if (!appointmentId) {
            console.error('Keine Appointment-ID gefunden');
            return;
        }

        const status = action === 'approve' ? 'APPROVED' : 'REJECTED';

        // Backend-Request ausführen
        event.waitUntil(
            updateAttendanceStatus(appointmentId, status)
                .then(() => {
                    console.log(`Status erfolgreich auf ${status} gesetzt`);
                    // Optional: Erfolgs-Benachrichtigung anzeigen
                    return self.registration.showNotification('Status aktualisiert', {
                        body: status === 'APPROVED' ? 'Du hast zugesagt' : 'Du hast abgesagt',
                        icon: '/icon.png',
                        badge: '/badge.png',
                        tag: 'status-update-success',
                        requireInteraction: false
                    });
                })
                .catch(error => {
                    console.error('Fehler beim Aktualisieren des Status:', error);
                    reportSwError(error, 'notificationclick-update-status');
                    // Fehler-Benachrichtigung
                    return self.registration.showNotification('Fehler', {
                        body: 'Status konnte nicht aktualisiert werden. Bitte öffne die App.',
                        icon: '/icon.png',
                        badge: '/badge.png',
                        tag: 'status-update-error'
                    });
                })
        );
        return;
    }

    event.notification.close();

    let urlToOpen = '/'; // Standard-URL

    // Bestimme die URL basierend auf dem Benachrichtigungstyp
    if (data) {
        try {
            if (data.type && data.type === 'SURVEY_INVITATION') {
                urlToOpen = `/survey/${data.survey}`;
            } else if (data.appointment) {
                const appointmentData = JSON.parse(data.appointment);
                urlToOpen = `/appointment/${appointmentData.id}`;
            } else if (data.group) {
                const groupData = JSON.parse(data.group);
                urlToOpen = `/groups/${groupData.id}`;
            }
        } catch (error) {
            console.error('Fehler beim Parsen der Benachrichtigungsdaten:', error);
            reportSwError(error, 'notificationclick-parse');
        }
    }

    // Öffne die App oder fokussiere ein bestehendes Fenster
    event.waitUntil(
        clients.matchAll({type: 'window', includeUncontrolled: true})
            .then(function (clientList) {
                // Prüfe, ob bereits ein Fenster mit der App offen ist
                for (let client of clientList) {
                    if (client.url.includes(self.registration.scope) && 'focus' in client) {
                        return client.focus().then(() => {
                            if ('navigate' in client) {
                                return client.navigate(urlToOpen);
                            }
                        });
                    }
                }
                // Wenn kein Fenster offen ist, öffne ein neues
                if (clients.openWindow) {
                    return clients.openWindow(urlToOpen);
                }
            })
    );
});

async function getOwnAttendanceStatus(appointmentId) {
    const response = await fetch(`${API_BASE_URL}/api/v2/appointments/${appointmentId}/participants/status`, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        },
        credentials: 'include'
    });

    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }

    return (await response.json()).status;
}

// Funktion zum Aktualisieren des Attendance-Status
async function updateAttendanceStatus(appointmentId, status) {
    if (status === 'APPROVED') {
        const response = await fetch(`${API_BASE_URL}/api/v2/appointments/${appointmentId}/participants/approve`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
    } else {
        const response = await fetch(`${API_BASE_URL}/api/v2/appointments/${appointmentId}/participants/reject`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
    }

}

// Optional: Installation und Aktivierung des Service Workers
self.addEventListener('install', function (event) {
    console.log('Service Worker installiert');
    self.skipWaiting();
});

self.addEventListener('activate', function (event) {
    console.log('Service Worker aktiviert');
    event.waitUntil(clients.claim());
});

self.addEventListener('fetch', (event) => {
    // Minimal offline response, required for PWA installation
    // Wir lassen Requests einfach "durchfallen"
    event.respondWith(fetch(event.request));
});