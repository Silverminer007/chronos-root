import type {Friend} from '~/types';

export function useFriends() {
    const loading = ref<boolean>(false);
    const friends: Ref<Friend[]> = ref([]);

    async function loadFriends() {
        loading.value = true;

        const data = await $fetch(`/api/v2/friendships/friends/`);

        if (data) {
            friends.value = data;
        }
        loading.value = false
    }

    async function searchFriends(searchQuery: string) {
        if (friends.value.length < 1) {
            await loadFriends();
        }
        return friends.value.filter((f) => f.name.toLowerCase().includes(searchQuery.toLowerCase()));
    }

    return {
        loading,
        searchContacts: searchFriends
    }
}