import * as Sentry from "@sentry/nuxt";

/**
 * Listens for SW_ERROR messages posted by the service worker (push-sw.js)
 * and forwards them to Sentry as captured exceptions.
 */
export default defineNuxtPlugin(() => {
    if (!("serviceWorker" in navigator)) return;

    navigator.serviceWorker.addEventListener("message", (event: MessageEvent) => {
        if (event.data?.type !== "SW_ERROR") return;

        const { message, stack, context } = event.data as {
            type: string;
            message: string;
            stack?: string;
            context: string;
        };

        const error = new Error(message);
        if (stack) error.stack = stack;

        Sentry.captureException(error, {
            tags: { source: "service-worker", swContext: context },
        });
    });
});
