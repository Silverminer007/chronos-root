import {defineStore} from 'pinia'
import type {User, LinkedAccount, Passkey} from '~/types'

export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null)
    const authenticated = computed(() => !!user.value)

    const initUser = async () => {
        const data = await $fetch<User>('/api/v2/user', {method: 'POST'})
        if (data) user.value = data
    }

    const fetchUser = async () => {
        try {
            const {data} = await useFetch('/api/v2/user')

            if (data.value) {
                user.value = data.value
            } else {
                user.value = null
            }

            if (user.value && !user.value.first_name && !user.value.last_name) {
                await initUser()
            }
        } catch {
            user.value = null
        }
    }

    const updateProfile = async (payload: { first_name: string; last_name: string; email: string }) => {
        const {user: updatedUser, verifyEmailUrl} = await $fetch<{ user: User; verifyEmailUrl?: string }>('/api/v2/user', {
            method: 'PATCH',
            query: {redirectUri: window.location.origin + window.location.pathname},
            body: payload
        })
        if (updatedUser) user.value = updatedUser

        if (verifyEmailUrl) {
            window.location.href = verifyEmailUrl
        }
    }

    const changePassword = async () => {
        const {url} = await $fetch<{ url: string }>('/api/v2/user/change-password', {
            query: {redirectUri: window.location.origin + window.location.pathname}
        })
        window.location.href = url
    }

    const fetchLinkedAccounts = async (): Promise<LinkedAccount[]> => {
        return await $fetch<LinkedAccount[]>('/api/v2/user/linked')
    }

    const unlinkAccount = async (providerId: string): Promise<void> => {
        await $fetch(`/api/v2/user/linked/${providerId}`, {method: 'DELETE'})
    }

    const linkAccount = async (providerId: string): Promise<void> => {
        const {url} = await $fetch<{ url: string }>('/api/v2/user/link/' + providerId, {
            query: {redirectUri: window.location.origin + window.location.pathname}
        })

        // Direkt navigieren – User geht zu Google/Apple, kommt zurück
        window.location.href = url
    }

    const fetchPasskeys = async (): Promise<Passkey[]> => {
        return await $fetch<Passkey[]>('/api/v2/user/passkeys')
    }

    const createPasskey = async (): Promise<void> => {
        const {url} = await $fetch<{ url: string }>('/api/v2/user/link/passkey', {
            query: {redirectUri: window.location.origin + window.location.pathname}
        })

        // Direkt navigieren – User geht zu Google/Apple, kommt zurück
        window.location.href = url
    }

    const deletePasskey = async (passkeyId: string): Promise<void> => {
        const {url} = await $fetch<{ url: string }>('/api/v2/user/unlink/passkey', {
            query: {
                redirectUri: window.location.origin + window.location.pathname,
                passkeyId
            }
        })

        // Direkt navigieren – User geht zu Google/Apple, kommt zurück
        window.location.href = url
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
        updateProfile,
        changePassword,
        fetchLinkedAccounts,
        unlinkAccount,
        linkAccount,
        fetchPasskeys,
        createPasskey,
        deletePasskey,
        logout
    }
})