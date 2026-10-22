# Build stage: Angular-Produktions-Build nach dist/browser
FROM node:26-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Dependencies stage: nur Laufzeit-Abhängigkeiten des BFF (express)
FROM node:26-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# Production stage: Node-BFF liefert dist/browser aus und proxyt /api/v2/*
FROM node:26-alpine AS production

WORKDIR /app

RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 chronos

COPY --from=deps --chown=chronos:nodejs /app/node_modules ./node_modules
COPY --from=builder --chown=chronos:nodejs /app/dist/browser ./dist/browser
COPY --chown=chronos:nodejs package.json ./
COPY --chown=chronos:nodejs server/package.json ./server/package.json
COPY --chown=chronos:nodejs server/index.ts server/app.ts server/config.ts server/jwt.ts ./server/

USER chronos

EXPOSE 3000

ENV NODE_ENV=production
ENV CHRONOS_PORT=3000

# Pflicht (alternativ mit den alten NUXT_*-Namen, siehe kb/architecture/ARC42-angular-cutover.md):
# CHRONOS_AUTH_ISSUER, CHRONOS_AUTH_CLIENT_ID, CHRONOS_AUTH_REDIRECT_URI, CHRONOS_QUARKUS_URL
# Optional: CHRONOS_PUBLIC_IMPRESSUM_*, CHRONOS_COOKIE_SECURE

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
  CMD wget -qO- "http://127.0.0.1:${CHRONOS_PORT}/api/config" > /dev/null || exit 1

CMD ["node", "server/index.ts"]
