import * as Sentry from "@sentry/nuxt";

Sentry.init({
    dsn: process.env.NUXT_PUBLIC_SENTRY_DSN,
    tracesSampleRate: process.env.NODE_ENV === "production" ? 0.1 : 1.0,
});
