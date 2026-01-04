import { defineStore } from 'pinia'
import type { User } from '~/types'

export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null)
    const authenticated = computed(() => !!user.value)

    const fetchUser = async () => {
        try {
            const { data } = await useFetch('/api/v2/user')

            if (data.value) {
                user.value = data.value
            } else {
                user.value = null
            }
        } catch (err) {
            user.value = null
        }
    }

    const logout = async () => {
        try {
            await $fetch('/api/auth/logout', {
                method: 'POST',
                credentials: 'include'
            })

            user.value = null
        } catch (err) {
            console.error('Logout fehlgeschlagen:', err)
        }
        window.location.href = '/';
    }

    return {
        user,
        authenticated,
        fetchUser,
        logout
    }
})