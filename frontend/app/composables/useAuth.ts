import { ref, computed } from 'vue'

export function useAuth() {
    // Reactive state
    const user = ref<any | null>(null)
    const authenticated = computed(() => !!user.value)

    // Lade User-Info vom BFF
    const fetchUser = async () => {
        try {
            const res = await $fetch('/api/me', {
                credentials: 'include' // Wichtig für HTTP-only Cookies
            })
            if (res?.authenticated) {
                user.value = res.user
            } else {
                user.value = null
            }
        } catch (err) {
            console.error('Fehler beim Abrufen der UserInfo:', err)
            user.value = null
        }
    }

    // Login: Redirect zum BFF Login
    const login = () => {
        window.location.href = '/api/auth/login'
    }

    // Logout: BFF-Logout aufrufen + User clearen
    const logout = async () => {
        try {
            await $fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
            user.value = null
            login()

        } catch (err) {
            console.error('Logout fehlgeschlagen:', err)
        }
    }

    return { user, authenticated, fetchUser, login, logout }
}