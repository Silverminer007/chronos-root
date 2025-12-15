export function useGroups() {
    const loading = ref<boolean>(false);

    async function searchGroups(searchQuery: string) {
        loading.value = true

        const data = await $fetch(`/api/groups/`, {
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
        searchGroups
    }
}