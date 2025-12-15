export function useContacts() {
    const loading = ref<boolean>(false);

    async function searchContacts(searchQuery: string) {
        loading.value = true

        const data = await $fetch(`/api/contacts/`, {
            query: {
                search: searchQuery,
            }
        })

        loading.value = false
        if (!data) {
            return [];
        } else {
            return data;
        }
    }

    return {
        loading,
        searchContacts
    }
}