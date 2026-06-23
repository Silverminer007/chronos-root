import {useAuthStore} from "~/stores/auth";

export default defineNuxtRouteMiddleware(async (to) => {
    const authStore = useAuthStore();
    const isLoggedIn = await authStore.checkSession();
    if (!isLoggedIn) {
        if (to.path !== '/' && !to.path.startsWith('/public')) {
            return navigateTo('/');
        }
        return;
    }
    if (!authStore.user) {
        await authStore.fetchUser();
    }
    const {shouldShow} = useOnboarding();
    if (shouldShow() && to.path !== '/onboarding') {
        return navigateTo('/onboarding')
    }
})