import tailwindcss from "@tailwindcss/vite";
import Aura from '@primeuix/themes/aura';

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
    css: [
        "./app/assets/css/main.css",],
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
                preset: Aura
            }
        },
        directives: {
            include: ['Tooltip']
        }
    }
})