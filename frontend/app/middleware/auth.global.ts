import {useAuthStore} from "~/stores/auth";
import {useOnboarding} from "~/composables/useOnboarding";

export default defineNuxtRouteMiddleware(async (to, from) => {
    const authStore = useAuthStore();
    if (!authStore.authenticated) {
        await authStore.fetchUser();
    } else {
        const {shouldShow} = useOnboarding();
        if (shouldShow() && to.path !== '/onboarding') {
            return navigateTo('/onboarding')
        }
    }
})