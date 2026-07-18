# Build stage
FROM node:26-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# Production stage
FROM node:26-alpine AS production

WORKDIR /app

RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 webpush

# Only production deps
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY --from=builder --chown=webpush:nodejs /app/dist ./dist

USER webpush

EXPOSE 3001

ENV NODE_ENV=production

CMD ["node", "dist/server.js"]
