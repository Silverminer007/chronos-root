<script setup lang="ts">
import { useAuthStore } from '~/stores/auth';
import { useToast } from 'primevue/usetoast';

const { fetchUser } = useAuthStore();
await fetchUser();

const route = useRoute();
const toast = useToast();
const surveyId = route.params.surveyId as string;
const fromPush = route.query.from === 'push';

const alreadyCompleted = ref(false);
const submitted = ref(false);
const submitting = ref(false);

// Eligibility check — skip when arriving from a push notification deep-link
if (!fromPush) {
  const { data: eligible, error: eligibleError } = await useFetch<boolean>(
    `/api/v2/survey/${surveyId}/eligible`
  );
  if (!eligibleError.value && eligible.value === false) {
    alreadyCompleted.value = true;
  }
}

// Q1 — Single choice
const q1 = ref('');
const q1Other = ref('');
const q1Options = [
  { value: 'plan_event', label: 'Einen neuen Termin planen' },
  { value: 'check_attendance', label: 'Nachschauen, wer zu einem Termin kommt' },
  { value: 'respond_invite', label: 'Rückmeldung zu einem Termin geben' },
  { value: 'message', label: 'Eine Nachricht zu einem Termin lesen oder senden' },
  { value: 'manage_group', label: 'Eine Gruppe oder Kontakte verwalten' },
  { value: 'other', label: 'Etwas anderes' },
];

// Q2 — Open text (max 30 words)
const q2 = ref('');
const q2WordCount = computed(() => (q2.value.trim() ? q2.value.trim().split(/\s+/).length : 0));

// Q3 — Multi-select checkboxes
const q3 = ref<string[]>([]);
const q3Options = [
  { value: 'friends', label: 'Jemanden als Freund hinzufügen' },
  { value: 'groups', label: 'Eine Gruppe erstellen oder nutzen' },
  { value: 'messaging', label: 'Innerhalb eines Termins schreiben' },
  { value: 'notifications', label: 'Push-Benachrichtigungen als Erinnerungen erhalten' },
  { value: 'rsvp', label: 'Zu einem Termin zu- oder absagen' },
  { value: 'none', label: 'Keines davon' },
];

function toggleQ3(value: string) {
  if (value === 'none') {
    q3.value = q3.value.includes('none') ? [] : ['none'];
    return;
  }
  const idx = q3.value.indexOf(value);
  if (idx >= 0) {
    q3.value.splice(idx, 1);
  } else {
    q3.value = q3.value.filter(v => v !== 'none');
    q3.value.push(value);
  }
}

// Q4 — Multi-select checkboxes with two labelled groups
const q4 = ref<string[]>([]);
const q4OptionsResponding = [
  { value: 'maybe_option', label: "Eine 'Vielleicht / unverbindlich'-Option" },
  { value: 'suggest_time', label: 'Einen anderen Zeitvorschlag machen können' },
  { value: 'response_note', label: 'Beim Antworten eine kurze Notiz hinzufügen' },
];
const q4OptionsOrganizing = [
  { value: 'know_lateness', label: 'Wissen, ob ein Teilnehmer später kommt oder früher geht' },
  { value: 'know_window', label: "Das genaue Zeitfenster eines Teilnehmers kennen (z. B. 'nur 18:00–20:00 Uhr')" },
  { value: 'know_contribution', label: 'Wissen, was ein Teilnehmer beisteuern möchte (Essen, Aufgabe usw.)' },
  { value: 'none', label: 'Keines davon – Zu-/Absagen reicht mir' },
];

function toggleQ4(value: string) {
  if (value === 'none') {
    q4.value = q4.value.includes('none') ? [] : ['none'];
    return;
  }
  const idx = q4.value.indexOf(value);
  if (idx >= 0) {
    q4.value.splice(idx, 1);
  } else {
    q4.value = q4.value.filter(v => v !== 'none');
    q4.value.push(value);
  }
}

// Q5 — Open text (max 20 words)
const q5 = ref('');
const q5WordCount = computed(() => (q5.value.trim() ? q5.value.trim().split(/\s+/).length : 0));

async function submit() {
  submitting.value = true;
  const answers: Record<string, unknown> = {};

  if (q1.value) {
    answers.q1 = q1.value;
    if (q1.value === 'other' && q1Other.value.trim()) {
      answers.q1_other = q1Other.value.trim();
    }
  }
  if (q2.value.trim()) answers.q2 = q2.value.trim();
  if (q3.value.length) answers.q3 = [...q3.value];
  if (q4.value.length) answers.q4 = [...q4.value];
  if (q5.value.trim()) answers.q5 = q5.value.trim();

  try {
    await $fetch(`/api/v2/survey/${surveyId}/responses`, {
      method: 'POST',
      body: { answers },
    });
    submitted.value = true;
    setTimeout(() => navigateTo('/agenda'), 2000);
  } catch (e: any) {
    if (e?.response?.status === 400) {
      alreadyCompleted.value = true;
    } else {
      toast.add({
        severity: 'error',
        summary: 'Fehler',
        detail: 'Antworten konnten nicht gesendet werden. Bitte versuche es erneut.',
        life: 4000,
      });
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader />

    <div class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-2xl mx-auto">

        <!-- Submitted state -->
        <div v-if="submitted" class="flex flex-col items-center justify-center min-h-[60vh] text-center px-6">
          <div class="w-20 h-20 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mb-6">
            <Icon name="lucide:check" class="text-4xl text-purple-600 dark:text-purple-400" />
          </div>
          <h2 class="text-2xl font-bold text-gray-900 dark:text-white mb-3">Danke!</h2>
          <p class="text-gray-600 dark:text-gray-400">Dein Feedback hilft uns, Chronos zu verbessern.</p>
        </div>

        <!-- Already completed state -->
        <div v-else-if="alreadyCompleted" class="flex flex-col items-center justify-center min-h-[60vh] text-center px-6">
          <div class="w-20 h-20 bg-linear-to-br from-green-100 to-teal-100 dark:from-green-900/30 dark:to-teal-900/30 rounded-full flex items-center justify-center mb-6">
            <Icon name="lucide:check-circle" class="text-4xl text-green-600 dark:text-green-400" />
          </div>
          <h2 class="text-2xl font-bold text-gray-900 dark:text-white mb-3">Danke, du hast bereits geantwortet!</h2>
          <p class="text-gray-600 dark:text-gray-400 mb-6">Du hast diese Umfrage bereits ausgefüllt. Dein Feedback hilft uns.</p>
          <NuxtLink
            to="/agenda"
            class="px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all"
          >
            Zur Übersicht
          </NuxtLink>
        </div>

        <!-- Survey form -->
        <div v-else>
          <!-- Page header -->
          <div class="mb-8 pt-6">
            <div class="flex items-center gap-2 text-sm text-purple-600 dark:text-purple-400 font-medium mb-2">
              <Icon name="lucide:list-checks" />
              <span>5 kurze Fragen</span>
            </div>
            <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">Kurze Umfrage</h1>
            <p class="text-gray-500 dark:text-gray-400 mt-1">Alle Fragen sind optional – du kannst jederzeit überspringen.</p>
          </div>

          <div class="space-y-6">

            <!-- Q1 -->
            <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6">
              <div class="flex items-start gap-3 mb-4">
                <span class="shrink-0 w-7 h-7 rounded-full bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-sm font-bold flex items-center justify-center">1</span>
                <p class="font-semibold text-gray-900 dark:text-white leading-snug">Was wolltest du beim letzten Öffnen von Chronos tun?</p>
              </div>
              <div class="space-y-3 ml-10">
                <label v-for="opt in q1Options" :key="opt.value" class="flex items-center gap-3 cursor-pointer group">
                  <input v-model="q1" type="radio" :value="opt.value" class="w-4 h-4 accent-purple-600 cursor-pointer shrink-0" />
                  <span class="text-gray-700 dark:text-gray-300 group-hover:text-gray-900 dark:group-hover:text-white transition-colors">{{ opt.label }}</span>
                </label>
                <div v-if="q1 === 'other'" class="pt-1">
                  <input
                    v-model="q1Other"
                    type="text"
                    placeholder="Bitte kurz beschreiben..."
                    class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-purple-500"
                  />
                </div>
              </div>
            </div>

            <!-- Q2 -->
            <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6">
              <div class="flex items-start gap-3 mb-4">
                <span class="shrink-0 w-7 h-7 rounded-full bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-sm font-bold flex items-center justify-center">2</span>
                <p class="font-semibold text-gray-900 dark:text-white leading-snug">
                  Wie hast du die letzten Terminabstimmungen gemacht? Was hat gut geklappt und was nicht?
                  <span class="font-normal text-gray-500 dark:text-gray-400"> (Muss nicht in Chronos gewesen sein)</span>
                </p>
              </div>
              <div class="ml-10">
                <textarea
                  v-model="q2"
                  rows="3"
                  placeholder="Optional – du kannst diese Frage auch überspringen..."
                  class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-purple-500 resize-none"
                ></textarea>
                <div class="flex justify-end mt-1">
                  <span class="text-xs" :class="q2WordCount > 30 ? 'text-red-500 font-medium' : 'text-gray-400 dark:text-gray-500'">
                    {{ q2WordCount }} / 30 Wörter
                  </span>
                </div>
              </div>
            </div>

            <!-- Q3 -->
            <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6">
              <div class="flex items-start gap-3 mb-4">
                <span class="shrink-0 w-7 h-7 rounded-full bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-sm font-bold flex items-center justify-center">3</span>
                <p class="font-semibold text-gray-900 dark:text-white leading-snug">Was davon hast du in Chronos schon genutzt? Tippe alles an, was du kennst.</p>
              </div>
              <div class="space-y-3 ml-10">
                <label v-for="opt in q3Options" :key="opt.value" class="flex items-center gap-3 cursor-pointer group">
                  <input
                    type="checkbox"
                    :checked="q3.includes(opt.value)"
                    class="w-4 h-4 accent-purple-600 cursor-pointer rounded shrink-0"
                    @change="toggleQ3(opt.value)"
                  />
                  <span class="text-gray-700 dark:text-gray-300 group-hover:text-gray-900 dark:group-hover:text-white transition-colors">{{ opt.label }}</span>
                </label>
              </div>
            </div>

            <!-- Q4 -->
            <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6">
              <div class="flex items-start gap-3 mb-4">
                <span class="shrink-0 w-7 h-7 rounded-full bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-sm font-bold flex items-center justify-center">4</span>
                <p class="font-semibold text-gray-900 dark:text-white leading-snug">Was wäre beim Rückmelden zu Terminen für dich nützlich? Wähle alles aus, was passt.</p>
              </div>
              <div class="ml-10 space-y-5">
                <!-- Responding group -->
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500 mb-2">Als Teilnehmer beim Rückmelden</p>
                  <div class="space-y-3">
                    <label v-for="opt in q4OptionsResponding" :key="opt.value" class="flex items-center gap-3 cursor-pointer group">
                      <input
                        type="checkbox"
                        :checked="q4.includes(opt.value)"
                        class="w-4 h-4 accent-purple-600 cursor-pointer rounded shrink-0"
                        @change="toggleQ4(opt.value)"
                      />
                      <span class="text-gray-700 dark:text-gray-300 group-hover:text-gray-900 dark:group-hover:text-white transition-colors">{{ opt.label }}</span>
                    </label>
                  </div>
                </div>
                <!-- Organizing group -->
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500 mb-2">Als Person, die einen Termin organisiert</p>
                  <div class="space-y-3">
                    <label v-for="opt in q4OptionsOrganizing" :key="opt.value" class="flex items-center gap-3 cursor-pointer group">
                      <input
                        type="checkbox"
                        :checked="q4.includes(opt.value)"
                        class="w-4 h-4 accent-purple-600 cursor-pointer rounded shrink-0"
                        @change="toggleQ4(opt.value)"
                      />
                      <span class="text-gray-700 dark:text-gray-300 group-hover:text-gray-900 dark:group-hover:text-white transition-colors">{{ opt.label }}</span>
                    </label>
                  </div>
                </div>
              </div>
            </div>

            <!-- Q5 -->
            <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6">
              <div class="flex items-start gap-3 mb-4">
                <span class="shrink-0 w-7 h-7 rounded-full bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-sm font-bold flex items-center justify-center">5</span>
                <p class="font-semibold text-gray-900 dark:text-white leading-snug">Wenn du eine Sache an Chronos ändern könntest – was wäre das?</p>
              </div>
              <div class="ml-10">
                <textarea
                  v-model="q5"
                  rows="2"
                  placeholder="Optional – du kannst diese Frage auch überspringen..."
                  class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-purple-500 resize-none"
                ></textarea>
                <div class="flex justify-end mt-1">
                  <span class="text-xs" :class="q5WordCount > 20 ? 'text-red-500 font-medium' : 'text-gray-400 dark:text-gray-500'">
                    {{ q5WordCount }} / 20 Wörter
                  </span>
                </div>
              </div>
            </div>

            <!-- Submit -->
            <button
              :disabled="submitting"
              class="w-full py-4 rounded-xl font-semibold text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 disabled:opacity-60 disabled:cursor-not-allowed transition-all shadow-lg flex items-center justify-center gap-2"
              @click="submit"
            >
              <Icon v-if="submitting" name="lucide:loader-circle" class="animate-spin" />
              <Icon v-else name="lucide:send" />
              <span>{{ submitting ? 'Wird gesendet...' : 'Absenden' }}</span>
            </button>

          </div>
        </div>

      </div>
    </div>

    <Toast />
  </div>
</template>
