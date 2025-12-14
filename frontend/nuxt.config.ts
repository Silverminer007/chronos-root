import tailwindcss from "@tailwindcss/vite";
import Aura from '@primeuix/themes/aura';
import {definePreset} from '@primeuix/themes';

const MyPreset = definePreset(Aura, {
    semantic: {
        primary: {
            50: '{indigo.50}',
            100: '{indigo.100}',
            200: '{indigo.200}',
            300: '{indigo.300}',
            400: '{indigo.400}',
            500: '{indigo.500}',
            600: '{indigo.600}',
            700: '{indigo.700}',
            800: '{indigo.800}',
            900: '{indigo.900}',
            950: '{indigo.950}'
        },
        surface: {
            ground: '{surface.700}'
        }
    },
    components: {
        card: {
            colorScheme: {
                light: {
                    root: {
                        background: '{surface.300}',
                        color: '{surface.800}'
                    }
                },
                dark: {
                    root: {
                        background: '{surface.800}',
                        color: '{surface.0}'
                    }
                }
            }
        },
        button: {
            colorScheme: {
                light: {
                    root: {
                        secondary: {
                            borderColor: '{surface.400}',
                        }
                    }
                },
                dark: {
                    root: {
                        secondary: {
                            borderColor: '{surface.900}',
                        }
                    }
                }
            }
        }
    }
});


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
                preset: MyPreset
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