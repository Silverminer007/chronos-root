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
        quarkusUrl: ""
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
    modules: [
        '@primevue/nuxt-module',
        '@pinia/nuxt'
    ],
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
    }
})