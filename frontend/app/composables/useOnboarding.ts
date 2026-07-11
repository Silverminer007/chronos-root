export function useOnboarding() {
    const SHOWN_AT_STORAGE_KEY = "onboardingShownAt"

    function shouldShow() {
        if (!import.meta.client) return false

        const shownAt = localStorage.getItem(SHOWN_AT_STORAGE_KEY)

        return !shownAt
    }

    function markShown() {
        if (!import.meta.client) return
        localStorage.setItem(SHOWN_AT_STORAGE_KEY, Date.now().toString())
    }

    return {
        shouldShow,
        markShown,
    }
}