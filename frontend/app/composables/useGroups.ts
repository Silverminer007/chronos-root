import type {Group} from '~/types';

export function useGroups() {
    const loading = ref<boolean>(false);

    async function searchGroups(searchQuery: string) {
        loading.value = true

        const data = await $fetch<(Group & { member_count?: number })[]>(`/api/v2/groups/`, {
            query: {
                search: searchQuery
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