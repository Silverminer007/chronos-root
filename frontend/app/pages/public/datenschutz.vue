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
            <Icon name="lucide:shield-check" class="text-white text-sm sm:text-base" />
            <span class="text-white text-xs sm:text-sm font-medium">Rechtliches</span>
          </div>

          <h1 class="text-3xl sm:text-5xl font-bold text-white mb-4 sm:mb-6 leading-tight">
            Datenschutzerklärung
          </h1>

          <p class="text-base sm:text-xl text-white/90 mb-8 max-w-2xl">
            Informationen zur Verarbeitung Ihrer personenbezogenen Daten
          </p>
        </div>
      </section>

      <!-- Privacy Policy Content -->
      <section class="container mx-auto px-4 sm:px-6 pb-12 sm:pb-20">
        <div class="max-w-4xl mx-auto space-y-6">

          <!-- Verantwortlicher -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:user" class="text-purple-600 dark:text-purple-400" />
              1. Verantwortlicher
            </h2>
            <div class="text-gray-600 dark:text-gray-300 space-y-2">
              <p>Verantwortlich für die Datenverarbeitung auf dieser Website ist:</p>
              <div class="mt-3 pl-4 border-l-2 border-purple-200 dark:border-purple-600">
                <p class="font-semibold">{{ config.impressumName || '[Name nicht konfiguriert]' }}</p>
                <p>{{ config.impressumStreet || '[Straße nicht konfiguriert]' }}</p>
                <p>{{ config.impressumCity || '[Stadt nicht konfiguriert]' }}</p>
                <p v-if="config.impressumEmail" class="mt-2">
                  E-Mail: <a :href="'mailto:' + config.impressumEmail" class="text-purple-600 dark:text-purple-400 hover:underline">{{ config.impressumEmail }}</a>
                </p>
              </div>
            </div>
          </div>

          <!-- Erhobene Daten -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:database" class="text-purple-600 dark:text-purple-400" />
              2. Welche Daten wir erheben
            </h2>
            <div class="text-gray-600 dark:text-gray-300 space-y-4">
              <div>
                <h3 class="font-semibold text-gray-800 dark:text-gray-100 mb-2">Benutzerkonto</h3>
                <p>Bei der Registrierung und Nutzung von Chronos erheben wir:</p>
                <ul class="list-disc list-inside mt-2 space-y-1 ml-4">
                  <li>Name (zur Anzeige in der App)</li>
                  <li>E-Mail-Adresse (für Anmeldung und Benachrichtigungen)</li>
                  <li>Profilbild (optional, von Ihnen hochgeladen)</li>
                  <li>Ihre Teilnahmestatus bei Terminen</li>
                  <li>Gruppenmitgliedschaften</li>
                </ul>
              </div>

              <div>
                <h3 class="font-semibold text-gray-800 dark:text-gray-100 mb-2">Technische Daten</h3>
                <p>Bei der Nutzung unserer Website werden automatisch erfasst:</p>
                <ul class="list-disc list-inside mt-2 space-y-1 ml-4">
                  <li>IP-Adresse (anonymisiert)</li>
                  <li>Browsertyp und -version</li>
                  <li>Betriebssystem</li>
                  <li>Datum und Uhrzeit des Zugriffs</li>
                </ul>
              </div>
            </div>
          </div>

          <!-- Cookies -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:cookie" class="text-purple-600 dark:text-purple-400" />
              3. Cookies
            </h2>
            <div class="text-gray-600 dark:text-gray-300 space-y-4">
              <p>
                Wir verwenden nur technisch notwendige Cookies, die für den Betrieb der Website
                unbedingt erforderlich sind. Diese Cookies dienen ausschließlich der Authentifizierung
                und Sitzungsverwaltung.
              </p>

              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-purple-200 dark:border-purple-600">
                      <th class="text-left py-2 pr-4 font-semibold text-gray-800 dark:text-gray-100">Cookie</th>
                      <th class="text-left py-2 pr-4 font-semibold text-gray-800 dark:text-gray-100">Zweck</th>
                      <th class="text-left py-2 font-semibold text-gray-800 dark:text-gray-100">Dauer</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr class="border-b border-purple-100 dark:border-purple-800">
                      <td class="py-2 pr-4 font-mono text-xs">kc_access</td>
                      <td class="py-2 pr-4">Zugriffstoken zur Authentifizierung bei API-Anfragen</td>
                      <td class="py-2">30 Tage</td>
                    </tr>
                    <tr class="border-b border-purple-100 dark:border-purple-800">
                      <td class="py-2 pr-4 font-mono text-xs">kc_refresh</td>
                      <td class="py-2 pr-4">Aktualisierungstoken zur Erneuerung des Zugriffstokens</td>
                      <td class="py-2">30 Tage</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <p>
                Die Cookies werden bei jedem Seitenbesuch automatisch erneuert, um Sie angemeldet zu halten.
              </p>

              <p>
                Es werden keine Tracking-Cookies oder Cookies von Drittanbietern verwendet.
              </p>
            </div>
          </div>

          <!-- Zweck der Verarbeitung -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:target" class="text-purple-600 dark:text-purple-400" />
              4. Zweck der Datenverarbeitung
            </h2>
            <div class="text-gray-600 dark:text-gray-300 space-y-2">
              <p>Wir verarbeiten Ihre Daten ausschließlich für folgende Zwecke:</p>
              <ul class="list-disc list-inside mt-2 space-y-1 ml-4">
                <li>Bereitstellung und Betrieb der Terminplanungs-Funktionen</li>
                <li>Verwaltung Ihres Benutzerkontos</li>
                <li>Anzeige Ihrer Verfügbarkeit gegenüber anderen Gruppenmitgliedern</li>
                <li>Versand von Push-Benachrichtigungen (sofern aktiviert)</li>
                <li>Technische Sicherstellung des Betriebs</li>
              </ul>
            </div>
          </div>

          <!-- Rechtsgrundlage -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:scale" class="text-purple-600 dark:text-purple-400" />
              5. Rechtsgrundlage
            </h2>
            <div class="text-gray-600 dark:text-gray-300 space-y-2">
              <p>Die Verarbeitung Ihrer Daten erfolgt auf Grundlage von:</p>
              <ul class="list-disc list-inside mt-2 space-y-1 ml-4">
                <li><strong>Art. 6 Abs. 1 lit. b DSGVO</strong> – Vertragserfüllung (Bereitstellung des Dienstes)</li>
                <li><strong>Art. 6 Abs. 1 lit. a DSGVO</strong> – Einwilligung (z.B. für Push-Benachrichtigungen)</li>
                <li><strong>Art. 6 Abs. 1 lit. f DSGVO</strong> – Berechtigtes Interesse (technischer Betrieb, Sicherheit)</li>
              </ul>
            </div>
          </div>

          <!-- Datenweitergabe -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:share-2" class="text-purple-600 dark:text-purple-400" />
              6. Datenweitergabe
            </h2>
            <div class="text-gray-600 dark:text-gray-300 space-y-2">
              <p>
                Ihre Daten werden grundsätzlich nicht an Dritte weitergegeben. Eine Ausnahme besteht nur:
              </p>
              <ul class="list-disc list-inside mt-2 space-y-1 ml-4">
                <li>Gegenüber anderen Nutzern innerhalb Ihrer Gruppen (Name, Profilbild, Teilnahmestatus)</li>
                <li>Gegenüber anderen Nutzern mit denen Sie befreundet sind (Name, Profilbild, E-Mail Adresse)</li>
                <li>Wenn Sie ausdrücklich eingewilligt haben</li>
                <li>Wenn wir gesetzlich dazu verpflichtet sind</li>
              </ul>
            </div>
          </div>

          <!-- Speicherdauer -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:clock" class="text-purple-600 dark:text-purple-400" />
              7. Speicherdauer
            </h2>
            <div class="text-gray-600 dark:text-gray-300">
              <p>
                Ihre personenbezogenen Daten werden gespeichert, solange Ihr Benutzerkonto aktiv ist.
                Nach Löschung Ihres Kontos werden alle personenbezogenen Daten innerhalb von 30 Tagen
                vollständig gelöscht, sofern keine gesetzlichen Aufbewahrungspflichten entgegenstehen.
              </p>
            </div>
          </div>

          <!-- Ihre Rechte -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:check-circle" class="text-purple-600 dark:text-purple-400" />
              8. Ihre Rechte
            </h2>
            <div class="text-gray-600 dark:text-gray-300 space-y-2">
              <p>Sie haben folgende Rechte bezüglich Ihrer personenbezogenen Daten:</p>
              <ul class="list-disc list-inside mt-2 space-y-1 ml-4">
                <li><strong>Auskunftsrecht</strong> (Art. 15 DSGVO) – Erfahren Sie, welche Daten wir über Sie speichern</li>
                <li><strong>Berichtigungsrecht</strong> (Art. 16 DSGVO) – Lassen Sie unrichtige Daten korrigieren</li>
                <li><strong>Löschungsrecht</strong> (Art. 17 DSGVO) – Verlangen Sie die Löschung Ihrer Daten</li>
                <li><strong>Einschränkungsrecht</strong> (Art. 18 DSGVO) – Schränken Sie die Verarbeitung ein</li>
                <li><strong>Datenübertragbarkeit</strong> (Art. 20 DSGVO) – Erhalten Sie Ihre Daten in einem gängigen Format</li>
                <li><strong>Widerspruchsrecht</strong> (Art. 21 DSGVO) – Widersprechen Sie der Verarbeitung</li>
              </ul>
              <p class="mt-4" v-if="config.impressumEmail">
                Zur Ausübung Ihrer Rechte kontaktieren Sie uns bitte unter:
                <a :href="'mailto:' + config.impressumEmail" class="text-purple-600 dark:text-purple-400 hover:underline">{{ config.impressumEmail }}</a>
              </p>
              <p class="mt-4">
                Sie haben außerdem das Recht, sich bei einer Datenschutz-Aufsichtsbehörde zu beschweren.
              </p>
            </div>
          </div>

          <!-- Sicherheit -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:lock" class="text-purple-600 dark:text-purple-400" />
              9. Datensicherheit
            </h2>
            <div class="text-gray-600 dark:text-gray-300">
              <p>
                Wir setzen technische und organisatorische Sicherheitsmaßnahmen ein, um Ihre Daten
                gegen zufällige oder vorsätzliche Manipulation, Verlust, Zerstörung oder den Zugriff
                unberechtigter Personen zu schützen. Die Übertragung aller Daten erfolgt verschlüsselt
                über HTTPS.
              </p>
            </div>
          </div>

          <!-- Änderungen -->
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-6 sm:p-8">
            <h2 class="text-xl sm:text-2xl font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-3">
              <Icon name="lucide:refresh-cw" class="text-purple-600 dark:text-purple-400" />
              10. Änderungen dieser Datenschutzerklärung
            </h2>
            <div class="text-gray-600 dark:text-gray-300">
              <p>
                Wir behalten uns vor, diese Datenschutzerklärung bei Bedarf anzupassen, um sie an
                geänderte Rechtslagen oder Änderungen unseres Dienstes anzupassen. Die aktuelle
                Version ist stets auf dieser Seite abrufbar.
              </p>
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
