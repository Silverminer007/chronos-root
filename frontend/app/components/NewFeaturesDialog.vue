<script setup lang="ts">
import { useNewFeatures } from '~/composables/useNewFeatures'
import type { NewFeature } from '~/composables/useNewFeatures'

const { getUnshownFeatures, markShown, markAllShown } = useNewFeatures()

const features = ref<NewFeature[]>([])
const currentStep = ref(0)
const visible = ref(false)
const leaving = ref(false)

const currentFeature = computed(() => features.value[currentStep.value])
const isLast = computed(() => currentStep.value === features.value.length - 1)
const isFirst = computed(() => currentStep.value === 0)

onMounted(() => {
    features.value = getUnshownFeatures()
    if (features.value.length > 0) {
        visible.value = true
    }
})

function next() {
    markShown(currentFeature.value.id)
    if (isLast.value) {
        close()
    } else {
        currentStep.value++
    }
}

function back() {
    if (!isFirst.value) currentStep.value--
}

function skipAll() {
    markAllShown()
    close()
}

function goToLink() {
    markShown(currentFeature.value.id)
    close()
    navigateTo(currentFeature.value.link!)
}

function close() {
    leaving.value = true
    setTimeout(() => {
        visible.value = false
        leaving.value = false
    }, 200)
}
</script>

<template>
    <Teleport to="body">
        <Transition name="backdrop">
            <div
                v-if="visible"
                class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
            >
                <!-- Backdrop -->
                <div
                    class="absolute inset-0 bg-black/60 backdrop-blur-sm"
                    @click="skipAll"
                />

                <!-- Dialog -->
                <Transition name="dialog">
                    <div
                        v-if="visible"
                        class="relative w-full sm:max-w-md bg-white dark:bg-neutral-900 rounded-t-2xl sm:rounded-2xl shadow-2xl overflow-hidden"
                        :class="{ 'leaving': leaving }"
                    >
                        <!-- Gradient top strip -->
                        <div class="h-1.5 bg-linear-to-r from-purple-500 to-pink-500" />

                        <div class="p-6 sm:p-8">
                            <!-- Header row -->
                            <div class="flex items-start justify-between mb-6">
                                <div class="flex items-center gap-2">
                                    <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300">
                                        <Icon name="lucide:sparkles" class="text-xs" />
                                        Neu in Chronos
                                    </span>
                                    <span class="text-xs text-gray-400 dark:text-gray-500">
                                        {{ currentStep + 1 }}/{{ features.length }}
                                    </span>
                                </div>
                                <button
                                    class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-neutral-700"
                                    title="Schließen"
                                    @click="skipAll"
                                >
                                    <Icon name="lucide:x" />
                                </button>
                            </div>

                            <!-- Feature icon -->
                            <div class="flex justify-center mb-5">
                                <div class="w-20 h-20 rounded-2xl bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 flex items-center justify-center shadow-inner">
                                    <Icon
                                        :name="currentFeature?.icon ?? 'lucide:star'"
                                        class="text-purple-600 dark:text-purple-400 text-4xl"
                                    />
                                </div>
                            </div>

                            <!-- Content -->
                            <div class="text-center mb-8">
                                <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-2">
                                    {{ currentFeature?.title }}
                                </h2>
                                <p class="text-gray-500 dark:text-gray-400 text-sm leading-relaxed">
                                    {{ currentFeature?.description }}
                                </p>
                            </div>

                            <!-- CTA link -->
                            <button
                                v-if="currentFeature?.link"
                                class="w-full flex items-center justify-center gap-2 px-4 py-3 mb-4 rounded-xl font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-sm"
                                @click="goToLink"
                            >
                                <Icon name="lucide:arrow-right" class="text-sm" />
                                <span>{{ currentFeature.linkLabel ?? 'Jetzt ansehen' }}</span>
                            </button>

                            <!-- Navigation -->
                            <div class="flex items-center gap-3">
                                <button
                                    v-if="!isFirst"
                                    class="flex items-center justify-center w-10 h-10 rounded-lg border border-gray-200 dark:border-neutral-700 text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-neutral-800 transition-colors shrink-0"
                                    @click="back"
                                >
                                    <Icon name="lucide:chevron-left" />
                                </button>

                                <!-- Progress dots -->
                                <div class="flex-1 flex items-center justify-center gap-1.5">
                                    <button
                                        v-for="(_, i) in features"
                                        :key="i"
                                        class="rounded-full transition-all duration-200"
                                        :class="i === currentStep
                                            ? 'w-5 h-2 bg-purple-500'
                                            : 'w-2 h-2 bg-gray-200 dark:bg-neutral-700 hover:bg-gray-300 dark:hover:bg-neutral-600'"
                                        @click="currentStep = i"
                                    />
                                </div>

                                <button
                                    class="flex items-center justify-center gap-1.5 px-4 h-10 rounded-lg font-medium text-sm text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-neutral-700 hover:bg-gray-50 dark:hover:bg-neutral-800 transition-colors shrink-0"
                                    @click="next"
                                >
                                    <span>{{ isLast ? 'Fertig' : 'Weiter' }}</span>
                                    <Icon :name="isLast ? 'lucide:check' : 'lucide:chevron-right'" class="text-sm" />
                                </button>
                            </div>

                            <!-- Skip all (only when multiple features) -->
                            <div v-if="features.length > 1" class="text-center mt-4">
                                <button
                                    class="text-xs text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
                                    @click="skipAll"
                                >
                                    Alle überspringen
                                </button>
                            </div>
                        </div>
                    </div>
                </Transition>
            </div>
        </Transition>
    </Teleport>
</template>

<style scoped>
.backdrop-enter-active,
.backdrop-leave-active {
    transition: opacity 0.2s ease;
}
.backdrop-enter-from,
.backdrop-leave-to {
    opacity: 0;
}

.dialog-enter-active {
    transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.2s ease;
}
.dialog-leave-active {
    transition: transform 0.2s ease, opacity 0.15s ease;
}
.dialog-enter-from {
    transform: translateY(30px);
    opacity: 0;
}
.dialog-leave-to {
    transform: translateY(20px);
    opacity: 0;
}

@media (min-width: 640px) {
    .dialog-enter-from {
        transform: scale(0.95) translateY(8px);
    }
    .dialog-leave-to {
        transform: scale(0.97) translateY(4px);
    }
}
</style>
