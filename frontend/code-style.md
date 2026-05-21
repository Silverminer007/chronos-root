# Code Style Guide — Chronos Frontend

## Stack

- **Nuxt 3** + **Vue 3** (Composition API, `<script setup lang="ts">`)
- **Pinia** for state management (`app/stores/`)
- **PrimeVue** for UI components
- **Tailwind CSS** for styling (utility-first, dark mode via `dark:` prefix)
- **TypeScript** throughout

---

## Vue SFC Structure

Block order within `.vue` files:

```vue
<template>
  ...
</template>

<script setup lang="ts">
  ...
</script>

<style scoped>
  ...
</style>
```

- Always use `<script setup lang="ts">` — never Options API in components
- `<style scoped>` only when component-specific CSS is strictly necessary; prefer Tailwind
- Use `:deep()` to style child component internals from a scoped style block

---

## TypeScript

- All files use TypeScript (`lang="ts"`)
- Props typed via `defineProps<Props>()` with a local `interface Props`
- Emits typed via `defineEmits<{ 'event-name': [arg: Type] }>()`
- Use `as const` for readonly arrays/tuples
- Prefer `interface` over `type` for object shapes; use `type` for unions/aliases
- Type imports use the `import type` syntax

```ts
import type { Appointment, UserParticipant } from '~/types'
```

---

## Imports

- Path alias `~/` maps to `app/` — use it consistently
- Auto-imports are active for Vue composables (`ref`, `computed`, `watch`, `onMounted`), Nuxt composables (`navigateTo`, `useRoute`, `useRouter`, `useFetch`), and all local composables/stores — **do not add explicit imports for these**
- Do import PrimeVue components explicitly when used in `<script>` logic (e.g. `Dialog`, `Button`); auto-imported in templates
- Group imports: types → stores → composables → components → third-party

---

## Naming

| Thing | Convention | Example |
|---|---|---|
| Components | PascalCase | `AppointmentCard.vue` |
| Pages | kebab-case or `[param].vue` | `[appointmentId].vue` |
| Stores | camelCase, `use...Store` | `useAppointmentsStore` |
| Composables | camelCase, `use...` | `useDateFormatter` |
| Constants | UPPER_SNAKE_CASE | `ALL_FEATURES`, `STORAGE_KEY` |
| UI event handlers | `handle...` prefix | `handleApprove`, `handleUnlink` |
| Data fetch functions | `fetch...` prefix | `fetchAppointment`, `fetchPasskeys` |
| Boolean refs | descriptive adjective | `saving`, `loading`, `loadingLinked` |

---

## Pinia Stores

Two patterns are used — choose based on complexity:

**Setup store** (preferred for new stores — simpler, better TS inference):

```ts
export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null)
    const authenticated = computed(() => !!user.value)

    const fetchUser = async () => { ... }

    return { user, authenticated, fetchUser }
})
```

**Options store** (used in `appointments.ts` — fine for stores with many getters):

```ts
export const useAppointmentsStore = defineStore('appointments', {
    state: () => ({ ... }),
    getters: { ... },
    actions: { ... }
})
```

### Store action pattern

```ts
async fetchSomething(id: number) {
    this.loading = true
    this.error = null
    try {
        const response = await $fetch(`/api/v2/something/${id}`)
        this.data = response
        return response
    } catch (err: any) {
        this.error = err.message || 'Fehler beim Laden'
        throw err
    } finally {
        this.loading = false
    }
}
```

- Always reset `error` to `null` at the start of an action
- Always use `finally` to reset `loading`
- Re-throw errors so callers can react
- Error messages are in **German**

---

## API Calls

- **Mutations** (POST, PATCH, DELETE): use `$fetch()`
- **Reads in setup/onMounted**: use `$fetch()` inside store actions
- **SSR-safe reads**: use `useFetch()` (returns `{ data, error }`)
- Base path: `/api/v2/...`
- Paginated responses follow `{ data: [...], meta: { page, size, total } }`

```ts
const response = await $fetch('/api/v2/appointments', {
    method: 'POST',
    body: data
})

const items = response.data ?? response   // handle both paginated and plain responses
const meta = response.meta
```

---

## Composables

- Return a plain object of named functions/refs — no default exports
- Use `import.meta.client` (not `process.client`) for client-only code

```ts
export function useNewFeatures() {
    function getUnshownFeatures(): NewFeature[] { ... }
    function markShown(id: string): void { ... }

    return { getUnshownFeatures, markShown }
}
```

---

## Types (`app/types/index.ts`)

- All shared types live in `app/types/index.ts`
- API field names use `snake_case` to match the backend
- UI/form-only fields use `camelCase`
- String union types for enums:

```ts
export type Role = "NONE" | "GUEST" | "ATTENDANT" | "HELPER" | "RESPONSIBLE"
export type AppointmentStatus = "PLANNED" | "CANCELLED" | "DELETED" | "NOT_ENOUGH_ATTENDEES"
```

---

## Template Style

- **2-space indentation** inside `<template>`
- Multi-attribute elements: each attribute on its own line, indented 4 spaces from the tag

```html
<button
    :disabled="hasApproved"
    @click.stop="handleApprove"
    class="..."
>
  Label
</button>
```

- Event handlers: `@click`, `@input`, `@close` — no parentheses for simple calls, parentheses when passing arguments
- Use `v-if` / `v-else-if` / `v-else` for conditional rendering; avoid `v-show` unless toggling frequently
- Use `v-for` with `:key` always — prefer a stable id over index
- HTML comments for section labels: `<!-- Header -->`, `<!-- Action Buttons -->`

---

## Tailwind Patterns

### Colors (always paired light + dark)

| Purpose | Light | Dark |
|---|---|---|
| Page background | `bg-gray-50` | `dark:bg-neutral-900` |
| Card background | `bg-white` | `dark:bg-neutral-800` |
| Card border | `border-gray-200` | `dark:border-neutral-700` |
| Primary text | `text-gray-900` | `dark:text-white` |
| Secondary text | `text-gray-600` | `dark:text-gray-400` |
| Muted text | `text-gray-500` | `dark:text-gray-400` |
| Input background | `bg-gray-50` | `dark:bg-neutral-700` |

### Brand gradient

```html
bg-linear-to-r from-purple-600 to-pink-500
```

For hover: `hover:from-purple-700 hover:to-pink-600`

Avatar/icon backgrounds: `bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30`

### Common shapes

- Cards: `rounded-xl shadow-sm hover:shadow-lg transition-all duration-300`
- Buttons/inputs: `rounded-lg`
- Icon wrappers: `rounded-xl` or `rounded-full`

### Responsive

Mobile-first. Common breakpoints: `sm:` (640px), `lg:` (1024px).
Typical layout: single column on mobile, grid on desktop:

```html
<div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
```

---

## Icons

- UI icons: `<Icon name="lucide:icon-name" />`
- Brand/provider logos: `<Icon name="simple-icons:google" />`
- Always include a `class` attribute for sizing/color when needed

---

## Error Handling & Toasts

```ts
import { useToast } from 'primevue/usetoast'
const toast = useToast()

try {
    await someAction()
    toast.add({ severity: 'success', summary: 'Gespeichert', life: 3000 })
} catch (err) {
    console.error(err)
    toast.add({ severity: 'error', summary: 'Fehler', detail: 'Beschreibung', life: 3000 })
}
```

- `summary` is always short (1–3 words)
- `detail` provides the human-readable explanation
- `life: 3000` (3 s) is the standard duration
- All user-facing messages are in **German**

---

## Navigation

```ts
navigateTo('/path')          // programmatic navigation (Nuxt auto-import)
router.back()                // go back in history
<NuxtLink to="/path">        // declarative links
```

---

## Language

All UI text, labels, toast messages, placeholder text, and inline code comments are written in **German**.

---

## File Organization

```
app/
  assets/css/       # global CSS (main.css)
  components/       # shared/reusable components (PascalCase)
  composables/      # use...() composables
  layouts/          # default.vue (authenticated), landingpage.vue
  middleware/       # auth.global.ts
  pages/            # file-based routing
  plugins/          # client-side plugins (*.client.ts)
  stores/           # Pinia stores
  types/            # shared TypeScript types (index.ts)
  utils/            # pure utility functions
```