import {beforeEach, describe, expect, it, vi} from 'vitest'
import {createPinia, setActivePinia} from 'pinia'
import {getErrorMessage} from '../../app/utils/errors'
import {useGroupsStore} from '../../app/stores/groups'

const fetchMock = vi.fn()

describe('groups store: fetchGroups', () => {
    beforeEach(() => {
        fetchMock.mockReset()
        vi.stubGlobal('$fetch', fetchMock)
        // Nuxt-Auto-Import, im Node-Test nicht verfügbar
        vi.stubGlobal('getErrorMessage', getErrorMessage)
        setActivePinia(createPinia())
    })

    it('setzt die Liste beim Laden', async () => {
        const groups = [{id: 1, name: 'Jungschar'}, {id: 2, name: 'Teens'}]
        fetchMock.mockResolvedValue(groups)
        const store = useGroupsStore()

        await store.fetchGroups()

        expect(fetchMock).toHaveBeenCalledWith('/api/v2/groups')
        expect(store.groups).toEqual(groups)
        expect(store.loading).toBe(false)
        expect(store.error).toBeNull()
    })

    it('lässt die Liste im Fehlerfall unverändert', async () => {
        const existing = [{id: 1, name: 'Jungschar'}]
        fetchMock.mockRejectedValue(new Error('boom'))
        const store = useGroupsStore()
        store.groups = [...existing] as never

        await expect(store.fetchGroups()).rejects.toThrow('boom')

        expect(store.groups).toEqual(existing)
        expect(store.error).toBe('boom')
        expect(store.loading).toBe(false)
    })

    it('liefert bei leerer Antwort eine leere Liste', async () => {
        fetchMock.mockResolvedValue(undefined)
        const store = useGroupsStore()
        store.groups = [{id: 1, name: 'alt'}] as never

        await store.fetchGroups()

        expect(store.groups).toEqual([])
    })
})
