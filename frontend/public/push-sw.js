// public/push-sw.js

self.addEventListener('push', event => {
    let data = {};
    try {
        data = event.data.json();
    } catch (e) {
        console.error("Invalid push payload", e);
    }

    const title = data.title || "Chronos Notification";
    const options = {
        body: data.body,
        icon: "/icon.png",
        badge: "/badge.png",
        data: {
            url: data.url,          // Deep Link
            notificationId: data.notificationId,
        },
        actions: data.actions || [
            { action: "open", title: "Öffnen" }
        ]
    };

    event.waitUntil(
        self.registration.showNotification(title, options)
    );
});

self.addEventListener('notificationclick', event => {
    event.notification.close();

    const { action } = event;
    const { url, notificationId } = event.notification.data;

    // Beispiel: API aufrufen für "gelesen", "zusagen" usw.
    if (action === "accept") {
        event.waitUntil(fetch(`/api/notifications/${notificationId}/accept`, { method: "POST" }));
        return;
    }
    if (action === "decline") {
        event.waitUntil(fetch(`/api/notifications/${notificationId}/decline`, { method: "POST" }));
        return;
    }

    // Default Action → Tab öffnen oder Fokus setzen
    event.waitUntil(
        clients.matchAll({ type: "window", includeUncontrolled: true }).then(windowClients => {
            for (const client of windowClients) {
                if (client.url === url && "focus" in client) {
                    return client.focus();
                }
            }
            return clients.openWindow(url);
        })
    );
});

self.addEventListener('fetch', (event) => {
    // Minimal offline response, required for PWA installation
    // Wir lassen Requests einfach "durchfallen"
    event.respondWith(fetch(event.request));
});