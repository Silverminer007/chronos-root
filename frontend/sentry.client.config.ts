import * as Sentry from "@sentry/nuxt";

Sentry.init({
    dsn: import.meta.env.NUXT_PUBLIC_SENTRY_DSN,

    integrations: [
        Sentry.browserTracingIntegration(),
        Sentry.replayIntegration({
            maskAllText: false,
            blockAllMedia: false,
        }),
    ],

    // Capture 10% of traces in production, 100% in development
    tracesSampleRate: import.meta.env.DEV ? 1.0 : 0.1,

    // Capture replays for 10% of sessions, 100% for sessions with errors
    replaysSessionSampleRate: 0.1,
    replaysOnErrorSampleRate: 1.0,
});
