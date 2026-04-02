import tailwindcss from "@tailwindcss/vite";
import {ChronosTheme} from "./theme";

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
    compatibilityDate: '2025-07-15',
    devtools: {enabled: true},

    runtimeConfig: {
        auth: {
            issuer: "",          // wird per ENV überschrieben
            clientId: "",
            redirectUri: "",
        },

        // Optional: API Endpoint zu Quarkus
        quarkusUrl: "",

        public: {
            // Impressum - set via NUXT_PUBLIC_IMPRESSUM_* environment variables
            impressumName: "",
            impressumStreet: "",
            impressumCity: "",
            impressumEmail: "",
            impressumPhone: "",
            // Sentry DSN - set via NUXT_PUBLIC_SENTRY_DSN
            sentryDsn: "",
        }
    },

    devServer: {
        port: 3000,
        host: '0.0.0.0' // do not put localhost (only accessible from the host machine)
    },

    css: ["./app/assets/css/main.css",],

    vite: {
        plugins: [
            tailwindcss(),
        ],
    },

    modules: ['@primevue/nuxt-module', '@pinia/nuxt', '@nuxt/icon', '@sentry/nuxt/module'],

    sentry: {
        dsn: process.env.NUXT_PUBLIC_SENTRY_DSN,
        sourceMapsUploadOptions: {
            org: 'justus-henze',
            project: 'chronos-frontend',
            authToken: process.env.SENTRY_AUTH_TOKEN,
        },
    },

    primevue: {
        options: {
            theme: {
                preset: ChronosTheme
            }
        },
        directives: {
            include: ['Tooltip']
        },
    },

    app: {
        head: {
            link: [
                {
                    rel: 'manifest',
                    href: '/manifest.webmanifest'
                },
                {
                    rel: 'icon',
                    href: '/icons/icon-192.png',
                    sizes: '192x192',
                    type: 'image/png'
                },
                {
                    rel: 'icon',
                    href: '/icons/icon-512.png',
                    sizes: '512x512',
                    type: 'image/png'
                }
            ]
        }
    },

    nitro: {
        compressPublicAssets: true,

        routeRules: {
            // Icons für 1 Jahr cachen
            '/icons/**': {
                headers: {
                    'cache-control': 'public, max-age=31536000, immutable'
                }
            },

            // Bilder
            '/images/**': {
                headers: {
                    'cache-control': 'public, max-age=2592000' // 30 Tage
                }
            },

            // Favicon mit kürzerem Cache
            '/favicon.*': {
                headers: {
                    'cache-control': 'public, max-age=86400' // 1 Tag
                }
            }
        }
    },
})