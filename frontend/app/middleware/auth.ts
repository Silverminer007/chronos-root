import {useAuth} from "~/composables/useAuth";

export default defineNuxtRouteMiddleware(async (to, from) => {
    const {authenticated, login, fetchUser} = useAuth();
    if (!authenticated) {
        login();
        await fetchUser();
    }
})