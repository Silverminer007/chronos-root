// public/push-sw.js
importScripts('https://cdn.jsdelivr.net/npm/luxon@3.4.4/build/global/luxon.min.js');

const API_BASE_URL = 'https://chronos-new-netlify.app';

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

    switch (type) {
        case 'EVENT_MOVED':
            const movedEvent = JSON.parse(data.new_event);
            title = `${movedEvent.name} wurde verschoben`;
            body = `${movedEvent.name} geht jetzt vom ${formatTimeRange(movedEvent.start, movedEvent.end)}`;
            tag = `event-moved-${movedEvent.id}`;
            if (attendanceStatus === "APPROVED") {
                actions = [
                    {action: 'reject', title: '✗ Absagen', icon: '/icons/cross.png'}
                ];
            } else if (attendanceStatus === "REJECTED") {
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

        case 'EVENT_CANCELLED':
            const cancelledEvent = JSON.parse(data.event);
            const whoCancelled = data.who_cancelled;
            title = `${cancelledEvent.name} abgesagt`;
            body = `"${cancelledEvent.name}" wurde von ${whoCancelled} abgesagt.`;
            tag = `event-cancelled-${cancelledEvent.id}`;
            break;

        case 'NEW_ATTENDEE':
            const attendeeEvent = JSON.parse(data.event);
            const newAttendee = data.new_attendee;
            const attendeeType = data.attendee_type;
            title = `${newAttendee} ist jetzt ${getRoleName(attendeeType)} bei ${attendeeEvent.name}`;
            body = `Für weitere Informationen tippe auf diese Nachricht`;
            tag = `new-attendee-${attendeeEvent.id}`;
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

        case 'ATTENDANCE_STATUS_CHANGED':
            const statusEvent = JSON.parse(data.event);
            const newStatus = data.new_attendance_status;
            const userName = data.user_name;
            const statusText = getStatusText(newStatus);
            title = 'Teilnahmestatus geändert';
            body = `${userName} hat den Status für "${statusEvent.name}" auf "${statusText}" geändert.`;
            tag = `status-changed-${statusEvent.id}`;
            break;

        case 'NOT_ENOUGH_ATTENDEES':
            const notEnoughEvent = JSON.parse(data.event);
            const approved = data.approved_attendances;
            const rejected = data.rejected_attendances;
            const pending = data.pending_attendances;
            title = `Nicht genug Teilnehmer (${approved} / ${notEnoughEvent.minimal_attendees}`;
            body = `"${notEnoughEvent.name}" hat nicht genug Zusagen (${approved} zugesagt, ${rejected} abgesagt, ${pending} ausstehend).`;
            if (notEnoughEvent.own_attendance_status === "REJECTED") {
                body += ' Du hast bisher abgesagt. Falls du doch kannst, gib bitte so schnell wie möglich bescheid';
                actions = [
                    {action: 'approve', title: '✓ Zusagen', icon: '/icons/check.png'}
                ];
            }
            tag = `not-enough-${notEnoughEvent.id}`;
            break;

        case 'ATTENDANCE_STATUS_PENDING':
            const pendingEvent = JSON.parse(data.event);
            title = `Deine Rückmeldung zu ${pendingEvent.name} fehlt noch`;
            body = `Bitte melde dich so schnell wie möglich.`;
            actions = [
                {action: 'approve', title: '✓ Zusagen', icon: '/icons/check.png'},
                {action: 'reject', title: '✗ Absagen', icon: '/icons/cross.png'}
            ];
            tag = `pending-${pendingEvent.id}`;
            break;

        case 'EVENT_REMINDER':
            const reminderEvent = JSON.parse(data.event);
            title = `Erinnerung: "${reminderEvent.name}" steht bald an!`;
            body = `Du hast ${getStatusText(reminderEvent.own_attendance_status)}`;
            tag = `reminder-${reminderEvent.id}`;
            break;

        case 'ATTENDANCE_STATUS_RECHECK':
            const recheckEvent = JSON.parse(data.event);
            const attendanceStatus = data.attendance_status;
            const recheckStatusText = getStatusText(attendanceStatus);
            title = `Rückmeldung zu ${recheckEvent.name} überprüfen`;
            body = `Du hast bisher ${recheckStatusText} zu ${recheckEvent.name}. Ist das noch aktuell?`;
            if (attendanceStatus === "APPROVED") {
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
            tag = `recheck-${recheckEvent.id}`;
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

        const eventId = data.eventId;
        if (!eventId) {
            console.error('Keine Event-ID gefunden');
            return;
        }

        const status = action === 'approve' ? 'APPROVED' : 'REJECTED';

        // Backend-Request ausführen
        event.waitUntil(
            updateAttendanceStatus(eventId, status)
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
            if (data.event) {
                const eventData = JSON.parse(data.event);
                urlToOpen = `/event/${eventData.id}`;
            } else if (data.new_event) {
                const eventData = JSON.parse(data.new_event);
                urlToOpen = `/event/${eventData.id}`;
            } else if (data.group) {
                const groupData = JSON.parse(data.group);
                urlToOpen = `/groups/${groupData.id}`;
            }
        } catch (error) {
            console.error('Fehler beim Parsen der Benachrichtigungsdaten:', error);
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

// Funktion zum Aktualisieren des Attendance-Status
async function updateAttendanceStatus(eventId, status) {
    const response = await fetch(`${API_BASE_URL}/api/event/${eventId}/attendance`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify({
            status: status
        })
    });

    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }

    return response.json();
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