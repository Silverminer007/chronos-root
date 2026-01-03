import {base64ToUint8Array} from "~/utils/base64";

export function usePush() {
    const subscribe = async () => {
        if (!("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window)) {
            console.warn("Push not supported");
            return null;
        }

        const registration = await navigator.serviceWorker.ready;

        // Hole Public Key vom Backend
        const vapidPublicKey = await $fetch<string>("/api/v2/push/public-key");

        const subscription = await registration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: base64ToUint8Array(vapidPublicKey),
        });

        await $fetch("/api/v2/push/subscribe", {
            method: "POST",
            body: {
                endpoint: subscription.endpoint,
                keys: {
                    p256dh: subscription.toJSON().keys.p256dh,
                    auth: subscription.toJSON().keys.auth
                }
            }
        });

        return subscription;
    };

    const SHOWN_AT_STORAGE_KEY = "pushPromptShownAt"
    const ANSWER_STORAGE_KEY = "pushPromptAnswer"

    const permission = ref<NotificationPermission>('default')

    if (process.client) {
        permission.value = Notification.permission
    }

    function isPushEnabled() {
        if (!("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window)) {
            return false;
        }
        return Notification.permission === 'granted' &&
            localStorage.getItem('pushPromptAnswer') === 'granted';
    }

    function isPushAvailable() {
        return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
    }

    async function shouldAsk() {
        if (!("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window)) {
            return false;
        }
        if (!process.client) return false

        // Already decided by the browser
        if (Notification.permission === 'denied') return false

        const answer = localStorage.getItem(ANSWER_STORAGE_KEY)

        if (answer === 'denied') return false

        if (answer === 'granted') {
            if (Notification.permission !== 'granted') {
                return true
            }
            const registration = await navigator.serviceWorker.ready;
            const existing = await registration.pushManager.getSubscription();
            if (!existing) {
                await subscribe();
            } else {
                const subscriptionStatus = await $fetch("/api/v2/push/status", {
                    method: "GET",
                    query: {
                        endpoint: existing.endpoint,
                    }
                });
                if (!subscriptionStatus.exists) {
                    console.warn("Server lost push subscription, reconnecting");
                    await existing.unsubscribe();
                    await subscribe();
                }
            }
            return false;
        }

        const lastAsked = localStorage.getItem(SHOWN_AT_STORAGE_KEY)

        if (!lastAsked) return true // Never asked on this device

        // Optional: ask again after 7 days
        const DAYS_7 = 1000 * 60 * 60 * 24 * 7

        return Date.now() - Number(lastAsked) > DAYS_7
    }

    function markAsked(newAnswer: 'granted' | 'denied' | 'dismissed') {
        if (!process.client) return
        localStorage.setItem(SHOWN_AT_STORAGE_KEY, Date.now().toString())
        const savedAnswer = localStorage.getItem(ANSWER_STORAGE_KEY)
        if (!savedAnswer || savedAnswer === 'dismissed') {
            localStorage.setItem(ANSWER_STORAGE_KEY, newAnswer)
        }
    }

    return {
        permission,
        shouldAsk,
        markAsked,
        subscribe,
        isPushEnabled,
        isPushAvailable
    }
}