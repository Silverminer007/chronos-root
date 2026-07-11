<template>
  <footer class="container mx-auto px-4 sm:px-6 py-6 sm:py-8 text-center text-white/70">
    <p class="text-sm sm:text-base mb-2">&copy; {{ new Date().getUTCFullYear() }} Chronos. Terminplanung für Jugendverbände.</p>
    <div class="flex justify-center gap-4 text-sm">
      <NuxtLink to="/public/impressum" class="hover:text-white transition-colors">Impressum</NuxtLink>
      <span>|</span>
      <NuxtLink to="/public/datenschutz" class="hover:text-white transition-colors">Datenschutz</NuxtLink>
    </div>
  </footer>
</template>
