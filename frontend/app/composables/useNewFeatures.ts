export interface NewFeature {
    id: string
    icon: string
    title: string
    description: string
    link?: string
    linkLabel?: string
}

const STORAGE_KEY = 'shownFeatureIds'

// Add new entries here – existing users will see them on next login.
const ALL_FEATURES: NewFeature[] = [
    {
        id: 'profile-page-v1',
        icon: 'lucide:user-round',
        title: 'Dein Profil',
        description: 'Ab sofort kannst du deinen Namen und deine E-Mail-Adresse direkt in der App bearbeiten.',
        link: '/profile',
        linkLabel: 'Zum Profil',
    },
    {
        id: 'linked-accounts-v1',
        icon: 'lucide:link',
        title: 'Verknüpfte Konten',
        description: 'Verbinde dein Konto mit Google, Microsoft oder Apple – für eine schnellere Anmeldung ohne Passwort.',
        link: '/profile',
        linkLabel: 'Konten verknüpfen',
    },
    {
        id: 'passkeys-v1',
        icon: 'lucide:fingerprint',
        title: 'Passkeys',
        description: 'Melde dich per Fingerabdruck, Gesichtserkennung oder Geräte-PIN an – ganz ohne Passwort.',
        link: '/profile',
        linkLabel: 'Passkey einrichten',
    },
    {
        id: 'change-password-v1',
        icon: 'lucide:lock',
        title: 'Passwort ändern',
        description: 'Dein Passwort kannst du jetzt direkt in Chronos ändern – ohne Umwege über externe Seiten.',
        link: '/profile',
        linkLabel: 'Passwort ändern',
    },
]

export function useNewFeatures() {
    function getShownIds(): string[] {
        if (!import.meta.client) return []
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
        } catch {
            return []
        }
    }

    function getUnshownFeatures(): NewFeature[] {
        const shown = getShownIds()
        return ALL_FEATURES.filter(f => !shown.includes(f.id))
    }

    function markShown(id: string): void {
        if (!import.meta.client) return
        const shown = getShownIds()
        if (!shown.includes(id)) {
            shown.push(id)
            localStorage.setItem(STORAGE_KEY, JSON.stringify(shown))
        }
    }

    function markAllShown(): void {
        if (!import.meta.client) return
        localStorage.setItem(STORAGE_KEY, JSON.stringify(ALL_FEATURES.map(f => f.id)))
    }

    return { getUnshownFeatures, markShown, markAllShown }
}
