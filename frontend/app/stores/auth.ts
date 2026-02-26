import { defineStore } from 'pinia'
import type { User } from '~/types'

export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null)
    const authenticated = computed(() => !!user.value)

    const initUser = async () => {
        const data = await $fetch<User>('/api/v2/user', { method: 'POST' })
        if (data) user.value = data
    }

    const fetchUser = async () => {
        try {
            const { data } = await useFetch('/api/v2/user')

            if (data.value) {
                user.value = data.value
            } else {
                user.value = null
            }

            if (user.value && !user.value.first_name && !user.value.last_name) {
                await initUser()
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