// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
    compatibilityDate: '2025-07-15',
    devtools: {enabled: true},
    runtimeConfig: {
        auth: {
            issuer: "",          // wird per ENV überschrieben
            clientId: "",
            clientSecret: "",
            redirectUri: "",
        },

        // Optional: API Endpoint zu Quarkus
        quarkusUrl: ""
    }
})