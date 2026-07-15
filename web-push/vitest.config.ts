import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    env: {
      TESTCONTAINERS_RYUK_DISABLED: 'true',
      TESTCONTAINERS_CHECKS_DISABLE: 'true',
    },
    testTimeout: 60_000,
    hookTimeout: 90_000,
  },
})
