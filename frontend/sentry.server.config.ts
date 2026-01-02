import * as Sentry from "@sentry/nuxt";
 
Sentry.init({
  dsn: "https://3086f6df95185071f20aa25878194fbf@o4510641728258048.ingest.de.sentry.io/4510641773674576",

  // We recommend adjusting this value in production, or using tracesSampler
  // for finer control
  tracesSampleRate: 1.0,

  // Enable logs to be sent to Sentry
  enableLogs: true,

  // Enable sending of user PII (Personally Identifiable Information)
  // https://docs.sentry.io/platforms/javascript/guides/nuxt/configuration/options/#sendDefaultPii
  sendDefaultPii: true,

  // Setting this option to true will print useful information to the console while you're setting up Sentry.
  debug: false,
});
