<template>
  <div class="min-h-screen bg-linear-to-br relative overflow-hidden transition-colors duration-300">
    <!-- Animated Background -->
    <div class="absolute inset-0 bg-linear-to-br from-purple-600 via-pink-500 to-orange-400 dark:from-purple-900 dark:via-pink-900 dark:to-orange-900 opacity-100">
      <div class="absolute inset-0 opacity-20">
        <div class="absolute top-20 left-10 w-72 h-72 bg-white rounded-full mix-blend-overlay filter blur-xl animate-pulse"></div>
        <div class="absolute bottom-20 right-10 w-96 h-96 bg-white rounded-full mix-blend-overlay filter blur-xl animate-pulse animation-delay-1000"></div>
      </div>
    </div>

    <div class="relative z-10">
      <!-- Header -->
      <header class="container mx-auto px-4 sm:px-6 py-4 sm:py-6">
        <nav class="flex items-center justify-between gap-4">
          <NuxtLink to="/app/pages/public" class="flex items-center gap-2 sm:gap-3 shrink-0">
            <img src="/icons/icon.png" alt="Chronos Logo" class="w-8 h-8 sm:w-10 sm:h-10 rounded-lg shadow-lg" />
            <span class="text-xl sm:text-2xl font-bold text-white">Chronos</span>
          </NuxtLink>
          <div class="flex gap-2 sm:gap-3 shrink-0">
            <a href="/api/auth/login" class="px-3 sm:px-6 py-2 sm:py-2.5 text-sm sm:text-base rounded-lg font-medium transition-all shadow-md border-2 border-purple-600 dark:border-purple-400 text-purple-600 dark:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 bg-white dark:bg-neutral-800 whitespace-nowrap">
              Anmelden
            </a>
            <a href="/api/auth/register" class="px-3 sm:px-6 py-2 sm:py-2.5 text-sm sm:text-base rounded-lg font-medium text-white transition-all shadow-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400 dark:hover:from-purple-600 dark:hover:to-pink-500 whitespace-nowrap">
              Registrieren
            </a>
          </div>
        </nav>
      </header>

      <!-- Hero Section -->
      <section class="container mx-auto px-4 sm:px-6 py-8 sm:py-12 text-center">
        <div class="max-w-4xl mx-auto">
          <div class="inline-flex items-center gap-2 bg-white/20 dark:bg-white/10 backdrop-blur-sm px-3 sm:px-4 py-2 rounded-full mb-4 sm:mb-6">
            <Icon name="lucide:download" class="text-white text-sm sm:text-base" />
            <span class="text-white text-xs sm:text-sm font-medium">App installieren</span>
          </div>

          <h1 class="text-3xl sm:text-5xl font-bold text-white mb-4 sm:mb-6 leading-tight px-4">
            Chronos installieren
          </h1>

          <p class="text-base sm:text-xl text-white/90 mb-8 max-w-2xl mx-auto px-4">
            Installiere Chronos als App auf deinem Gerät für schnellen Zugriff und Push-Benachrichtigungen.
          </p>
        </div>
      </section>

      <!-- Browser Instructions -->
      <section class="container mx-auto px-4 sm:px-6 pb-12 sm:pb-20">
        <div class="max-w-4xl mx-auto space-y-6">

          <template v-for="browser in browserOrder" :key="browser">
            <!-- Chrome / Edge (Desktop & Android) -->
            <div v-if="browser === 'chrome'" class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 rounded-xl p-6 sm:p-8" :class="detectedBrowser === 'chrome' ? 'border-purple-500 dark:border-purple-400 ring-2 ring-purple-500/20' : 'border-purple-200 dark:border-purple-600'">
              <div class="flex items-center gap-4 mb-6">
                <div class="w-12 h-12 sm:w-14 sm:h-14 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center">
                  <Icon name="lucide:chrome" class="text-purple-600 dark:text-purple-400 text-xl sm:text-2xl" />
                </div>
                <div class="flex-1">
                  <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100">
                    Chrome / Edge
                  </h2>
                  <p class="text-sm text-gray-500 dark:text-gray-400">Desktop & Android</p>
                </div>
                <span v-if="detectedBrowser === 'chrome'" class="px-3 py-1 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-xs font-medium rounded-full">
                  Dein Browser
                </span>
              </div>

              <!-- Desktop Instructions -->
              <div class="mb-6">
                <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-3 flex items-center gap-2">
                  <Icon name="lucide:monitor" class="text-purple-600 dark:text-purple-400" />
                  Desktop (Windows, macOS, Linux)
                </h3>
                <ol class="space-y-3 text-gray-600 dark:text-gray-300">
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">1</span>
                    <span>Öffne <a :href="requestUrl.origin" class="font-semibold text-purple-600 dark:text-purple-400 hover:underline">{{ websiteUrl }}</a> in Chrome oder Edge</span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">2</span>
                    <span>Klicke auf das <strong>Installieren-Symbol</strong> <Icon name="lucide:plus-square" class="inline text-gray-500" /> in der Adressleiste (rechts)</span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">3</span>
                    <span>Klicke auf <strong>„Installieren"</strong> im Dialog</span>
                  </li>
                </ol>
              </div>

              <!-- Android Instructions -->
              <div>
                <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-3 flex items-center gap-2">
                  <Icon name="lucide:smartphone" class="text-purple-600 dark:text-purple-400" />
                  Android
                </h3>
                <ol class="space-y-3 text-gray-600 dark:text-gray-300">
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">1</span>
                    <span>Öffne <a :href="requestUrl.origin" class="font-semibold text-purple-600 dark:text-purple-400 hover:underline">{{ websiteUrl }}</a> in Chrome</span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">2</span>
                    <span>Tippe auf das <strong>Drei-Punkte-Menü</strong> <Icon name="lucide:more-vertical" class="inline text-gray-500" /> oben rechts</span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">3</span>
                    <span>Wähle <strong>„Zum Startbildschirm hinzufügen"</strong> oder <strong>„App installieren"</strong></span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">4</span>
                    <span>Bestätige mit <strong>„Installieren"</strong></span>
                  </li>
                </ol>
              </div>
            </div>

            <!-- Safari (iOS & macOS) -->
            <div v-else-if="browser === 'safari'" class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 rounded-xl p-6 sm:p-8" :class="detectedBrowser === 'safari' ? 'border-purple-500 dark:border-purple-400 ring-2 ring-purple-500/20' : 'border-purple-200 dark:border-purple-600'">
              <div class="flex items-center gap-4 mb-6">
                <div class="w-12 h-12 sm:w-14 sm:h-14 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center">
                  <Icon name="lucide:compass" class="text-purple-600 dark:text-purple-400 text-xl sm:text-2xl" />
                </div>
                <div class="flex-1">
                  <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100">
                    Safari
                  </h2>
                  <p class="text-sm text-gray-500 dark:text-gray-400">iPhone, iPad & macOS</p>
                </div>
                <span v-if="detectedBrowser === 'safari'" class="px-3 py-1 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-xs font-medium rounded-full">
                  Dein Browser
                </span>
              </div>

              <!-- iOS Instructions -->
              <div class="mb-6">
                <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-3 flex items-center gap-2">
                  <Icon name="lucide:smartphone" class="text-purple-600 dark:text-purple-400" />
                  iPhone & iPad
                </h3>
                <ol class="space-y-3 text-gray-600 dark:text-gray-300">
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">1</span>
                    <span>Öffne <a :href="requestUrl.origin" class="font-semibold text-purple-600 dark:text-purple-400 hover:underline">{{ websiteUrl }}</a> in Safari</span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">2</span>
                    <span>Tippe auf das <strong>Teilen-Symbol</strong> <Icon name="lucide:share" class="inline text-gray-500" /> unten in der Mitte</span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">3</span>
                    <span>Scrolle nach unten und tippe auf <strong>„Zum Home-Bildschirm"</strong></span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">4</span>
                    <span>Tippe auf <strong>„Hinzufügen"</strong> oben rechts</span>
                  </li>
                </ol>
                <div class="mt-4 p-3 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg">
                  <p class="text-sm text-amber-800 dark:text-amber-200 flex items-start gap-2">
                    <Icon name="lucide:info" class="text-amber-600 dark:text-amber-400 mt-0.5 flex-shrink-0" />
                    <span><strong>Wichtig:</strong> Auf iOS muss Safari verwendet werden. Andere Browser unterstützen keine PWA-Installation.</span>
                  </p>
                </div>
              </div>

              <!-- macOS Instructions -->
              <div>
                <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-3 flex items-center gap-2">
                  <Icon name="lucide:monitor" class="text-purple-600 dark:text-purple-400" />
                  macOS (Safari 17+)
                </h3>
                <ol class="space-y-3 text-gray-600 dark:text-gray-300">
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">1</span>
                    <span>Öffne <a :href="requestUrl.origin" class="font-semibold text-purple-600 dark:text-purple-400 hover:underline">{{ websiteUrl }}</a> in Safari</span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">2</span>
                    <span>Klicke in der Menüleiste auf <strong>Ablage → Zum Dock hinzufügen</strong></span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">3</span>
                    <span>Bestätige im Dialog mit <strong>„Hinzufügen"</strong></span>
                  </li>
                </ol>
              </div>
            </div>

            <!-- Firefox -->
            <div v-else-if="browser === 'firefox'" class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 rounded-xl p-6 sm:p-8" :class="detectedBrowser === 'firefox' ? 'border-purple-500 dark:border-purple-400 ring-2 ring-purple-500/20' : 'border-purple-200 dark:border-purple-600'">
              <div class="flex items-center gap-4 mb-6">
                <div class="w-12 h-12 sm:w-14 sm:h-14 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center">
                  <Icon name="lucide:flame" class="text-purple-600 dark:text-purple-400 text-xl sm:text-2xl" />
                </div>
                <div class="flex-1">
                  <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100">
                    Firefox
                  </h2>
                  <p class="text-sm text-gray-500 dark:text-gray-400">Desktop & Android</p>
                </div>
                <span v-if="detectedBrowser === 'firefox'" class="px-3 py-1 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-xs font-medium rounded-full">
                  Dein Browser
                </span>
              </div>

              <div class="mb-6 p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
                <p class="text-sm text-blue-800 dark:text-blue-200 flex items-start gap-2">
                  <Icon name="lucide:info" class="text-blue-600 dark:text-blue-400 mt-0.5 flex-shrink-0" />
                  <span>Firefox unterstützt PWAs nur eingeschränkt. Für das beste Erlebnis empfehlen wir Chrome, Edge oder Safari.</span>
                </p>
              </div>

              <!-- Firefox Android -->
              <div class="mb-6">
                <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-3 flex items-center gap-2">
                  <Icon name="lucide:smartphone" class="text-purple-600 dark:text-purple-400" />
                  Android
                </h3>
                <ol class="space-y-3 text-gray-600 dark:text-gray-300">
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">1</span>
                    <span>Öffne <a :href="requestUrl.origin" class="font-semibold text-purple-600 dark:text-purple-400 hover:underline">{{ websiteUrl }}</a> in Firefox</span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">2</span>
                    <span>Tippe auf das <strong>Drei-Punkte-Menü</strong> <Icon name="lucide:more-vertical" class="inline text-gray-500" /></span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">3</span>
                    <span>Wähle <strong>„Zum Startbildschirm hinzufügen"</strong></span>
                  </li>
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 w-6 h-6 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full flex items-center justify-center text-sm font-medium">4</span>
                    <span>Bestätige mit <strong>„Hinzufügen"</strong></span>
                  </li>
                </ol>
              </div>

              <!-- Firefox Desktop -->
              <div>
                <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-3 flex items-center gap-2">
                  <Icon name="lucide:monitor" class="text-purple-600 dark:text-purple-400" />
                  Desktop
                </h3>
                <p class="text-gray-600 dark:text-gray-300">
                  Firefox Desktop unterstützt derzeit keine PWA-Installation. Du kannst Chronos als Lesezeichen speichern oder einen anderen Browser wie Chrome oder Edge verwenden.
                </p>
              </div>
            </div>
          </template>

          <!-- What is a PWA? -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <div class="flex items-center gap-4 mb-6">
              <div class="w-12 h-12 sm:w-14 sm:h-14 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center">
                <Icon name="lucide:help-circle" class="text-purple-600 dark:text-purple-400 text-xl sm:text-2xl" />
              </div>
              <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100">
                Was ist eine PWA?
              </h2>
            </div>

            <p class="text-gray-600 dark:text-gray-300 mb-4">
              Eine <strong>Progressive Web App (PWA)</strong> ist eine Website, die wie eine native App funktioniert. Nach der Installation erhältst du:
            </p>

            <ul class="space-y-3 text-gray-600 dark:text-gray-300">
              <li class="flex items-center gap-3">
                <Icon name="lucide:rocket" class="text-purple-600 dark:text-purple-400 flex-shrink-0" />
                <span><strong>Schnellerer Zugriff</strong> – Öffne Chronos direkt vom Startbildschirm</span>
              </li>
              <li class="flex items-center gap-3">
                <Icon name="lucide:bell" class="text-purple-600 dark:text-purple-400 flex-shrink-0" />
                <span><strong>Push-Benachrichtigungen</strong> – Erhalte Erinnerungen an wichtige Termine</span>
              </li>
              <li class="flex items-center gap-3">
                <Icon name="lucide:maximize-2" class="text-purple-600 dark:text-purple-400 flex-shrink-0" />
                <span><strong>Vollbild-Modus</strong> – Nutze die App ohne Browser-Oberfläche</span>
              </li>
              <li class="flex items-center gap-3">
                <Icon name="lucide:hard-drive" class="text-purple-600 dark:text-purple-400 flex-shrink-0" />
                <span><strong>Kein App Store nötig</strong> – Installiere direkt aus dem Browser</span>
              </li>
            </ul>
          </div>

        </div>
      </section>

      <LandingFooter />
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({
  layout: 'landingpage'
})

const requestUrl = useRequestURL()
const websiteUrl = computed(() => requestUrl.host)

type BrowserType = 'chrome' | 'safari' | 'firefox' | 'unknown'

const detectedBrowser = ref<BrowserType>('unknown')

onMounted(() => {
  detectedBrowser.value = detectBrowser()
})

function detectBrowser(): BrowserType {
  const ua = navigator.userAgent.toLowerCase()

  // Check Safari first (but not Chrome-based browsers on iOS)
  if (ua.includes('safari') && !ua.includes('chrome') && !ua.includes('chromium')) {
    return 'safari'
  }

  // Check Firefox
  if (ua.includes('firefox')) {
    return 'firefox'
  }

  // Check Chrome/Chromium-based browsers (Chrome, Edge, Opera, etc.)
  if (ua.includes('chrome') || ua.includes('chromium') || ua.includes('edg')) {
    return 'chrome'
  }

  return 'unknown'
}

const browserOrder = computed(() => {
  const order: BrowserType[] = ['chrome', 'safari', 'firefox']

  if (detectedBrowser.value !== 'unknown') {
    const index = order.indexOf(detectedBrowser.value)
    if (index > 0) {
      order.splice(index, 1)
      order.unshift(detectedBrowser.value)
    }
  }

  return order
})
</script>

<style scoped>
.animation-delay-1000 {
  animation-delay: 1s;
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.2;
  }
  50% {
    opacity: 0.4;
  }
}

.animate-pulse {
  animation: pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
</style>
