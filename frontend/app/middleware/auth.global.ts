import {useAuthStore} from "~/stores/auth";

export default defineNuxtRouteMiddleware(async (to, from) => {
    const {authenticated, fetchUser} = useAuthStore();
    if (!authenticated) {
        await fetchUser();
    }
})