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
          <NuxtLink to="/" class="flex items-center gap-2 sm:gap-3 shrink-0">
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

      <!-- Content Section -->
      <section class="container mx-auto px-4 sm:px-6 py-8 sm:py-12">
        <div class="max-w-4xl mx-auto">
          <div class="inline-flex items-center gap-2 bg-white/20 dark:bg-white/10 backdrop-blur-sm px-3 sm:px-4 py-2 rounded-full mb-4 sm:mb-6">
            <Icon name="lucide:file-text" class="text-white text-sm sm:text-base" />
            <span class="text-white text-xs sm:text-sm font-medium">Rechtliches</span>
          </div>

          <h1 class="text-3xl sm:text-5xl font-bold text-white mb-4 sm:mb-6 leading-tight">
            Impressum
          </h1>

          <p class="text-base sm:text-xl text-white/90 mb-8 max-w-2xl">
            Angaben gemäß § 5 TMG
          </p>
        </div>
      </section>

      <!-- Impressum Content -->
      <section class="container mx-auto px-4 sm:px-6 pb-12 sm:pb-20">
        <div class="max-w-4xl mx-auto">
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8 space-y-8">

            <!-- Verantwortlich -->
            <div>
              <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
                <Icon name="lucide:user" class="text-purple-600 dark:text-purple-400" />
                Verantwortlich für den Inhalt
              </h2>
              <div class="text-gray-600 dark:text-gray-300 space-y-1">
                <p class="font-semibold">{{ config.impressumName || '[Name nicht konfiguriert]' }}</p>
                <p>{{ config.impressumStreet || '[Straße nicht konfiguriert]' }}</p>
                <p>{{ config.impressumCity || '[Stadt nicht konfiguriert]' }}</p>
              </div>
            </div>

            <!-- Kontakt -->
            <div>
              <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
                <Icon name="lucide:mail" class="text-purple-600 dark:text-purple-400" />
                Kontakt
              </h2>
              <div class="text-gray-600 dark:text-gray-300 space-y-2">
                <p v-if="config.impressumEmail">
                  <span class="font-medium">E-Mail:</span>
                  <a :href="'mailto:' + config.impressumEmail" class="text-purple-600 dark:text-purple-400 hover:underline ml-2">
                    {{ config.impressumEmail }}
                  </a>
                </p>
                <p v-else class="text-amber-600 dark:text-amber-400">[E-Mail nicht konfiguriert]</p>
                <p v-if="config.impressumPhone">
                  <span class="font-medium">Telefon:</span>
                  <a :href="'tel:' + config.impressumPhone" class="text-purple-600 dark:text-purple-400 hover:underline ml-2">
                    {{ config.impressumPhone }}
                  </a>
                </p>
              </div>
            </div>

            <!-- Haftungsausschluss -->
            <div>
              <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
                <Icon name="lucide:shield" class="text-purple-600 dark:text-purple-400" />
                Haftungsausschluss
              </h2>

              <div class="space-y-4 text-gray-600 dark:text-gray-300">
                <div>
                  <h3 class="font-semibold text-gray-800 dark:text-gray-100 mb-2">Haftung für Inhalte</h3>
                  <p>
                    Die Inhalte unserer Seiten wurden mit größter Sorgfalt erstellt. Für die Richtigkeit,
                    Vollständigkeit und Aktualität der Inhalte können wir jedoch keine Gewähr übernehmen.
                    Als Diensteanbieter sind wir gemäß § 7 Abs.1 TMG für eigene Inhalte auf diesen Seiten
                    nach den allgemeinen Gesetzen verantwortlich.
                  </p>
                </div>

                <div>
                  <h3 class="font-semibold text-gray-800 dark:text-gray-100 mb-2">Haftung für Links</h3>
                  <p>
                    Unser Angebot enthält Links zu externen Webseiten Dritter, auf deren Inhalte wir keinen
                    Einfluss haben. Deshalb können wir für diese fremden Inhalte auch keine Gewähr übernehmen.
                    Für die Inhalte der verlinkten Seiten ist stets der jeweilige Anbieter oder Betreiber
                    der Seiten verantwortlich.
                  </p>
                </div>

                <div>
                  <h3 class="font-semibold text-gray-800 dark:text-gray-100 mb-2">Urheberrecht</h3>
                  <p>
                    Die durch die Seitenbetreiber erstellten Inhalte und Werke auf diesen Seiten unterliegen
                    dem deutschen Urheberrecht. Die Vervielfältigung, Bearbeitung, Verbreitung und jede Art
                    der Verwertung außerhalb der Grenzen des Urheberrechtes bedürfen der schriftlichen
                    Zustimmung des jeweiligen Autors bzw. Erstellers.
                  </p>
                </div>
              </div>
            </div>

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

const config = useRuntimeConfig().public
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
