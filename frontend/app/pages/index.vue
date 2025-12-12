<template>
  <div class="landing-page">
    <!-- Hero Section -->
    <section class="hero">
      <div class="hero-content">
        <h1 class="title">Chronos</h1>
        <p class="subtitle">
          Verwalte Team-Termine effizient – Zusagen, Absagen und alles in einem Überblick.
        </p>
        <Button label="Jetzt einloggen" class="p-button-raised p-button-primary" @click="login"/>
      </div>
      <div class="hero-image">
        <img src="/images/hero-illustration.svg" alt="Team Termine Illustration"/>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features">
      <h2>Warum Chronos?</h2>
      <div class="features-grid">
        <div class="feature-card">
          <img src="/images/icons/calendar.svg" alt="Kalender"/>
          <h3>Intuitive Terminplanung</h3>
          <p>Plane und verwalte Termine im Team einfach und schnell.</p>
        </div>
        <div class="feature-card">
          <img src="/images/icons/check.svg" alt="Zusage"/>
          <h3>Zusage & Absage</h3>
          <p>Alle Teilnehmer können direkt Zusagen oder Absagen eintragen.</p>
        </div>
        <div class="feature-card">
          <img src="/images/icons/team.svg" alt="Team"/>
          <h3>Teamorientiert</h3>
          <p>Optimiert für Zusammenarbeit und Teamübersicht.</p>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
      <p>© 2025 Chronos. Alle Rechte vorbehalten.</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import Button from 'primevue/button';
import {useAuthStore} from "~/stores/auth";

const {fetchUser, authenticated} = useAuthStore()
await fetchUser()
const login = () => {
  // Redirect zum Keycloak Login
  window.location.href = '/api/auth/login'
};
if (authenticated) {
  navigateTo('/agenda');
}
</script>

<style scoped>
.landing-page {
  font-family: 'Inter', sans-serif;
  color: #1a1a1a;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* Hero Section */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4rem 2rem;
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: white;
  flex-wrap: wrap;
}

.hero-content {
  max-width: 500px;
}

.title {
  font-size: 3rem;
  font-weight: 700;
  margin-bottom: 1rem;
}

.subtitle {
  font-size: 1.25rem;
  margin-bottom: 2rem;
}

.hero-image img {
  max-width: 400px;
}

/* Features Section */
.features {
  padding: 4rem 2rem;
  text-align: center;
}

.features-grid {
  display: flex;
  gap: 2rem;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 2rem;
}

.feature-card {
  background: white;
  padding: 2rem;
  border-radius: 1rem;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
  max-width: 250px;
  text-align: center;
}

.feature-card img {
  width: 60px;
  margin-bottom: 1rem;
}

.feature-card h3 {
  font-size: 1.25rem;
  margin-bottom: 0.5rem;
}

.feature-card p {
  font-size: 1rem;
  color: #4b5563;
}

/* Footer */
.footer {
  margin-top: auto;
  padding: 2rem;
  background: #f9f9f9;
  text-align: center;
  font-size: 0.875rem;
  color: #6b7280;
}
</style>