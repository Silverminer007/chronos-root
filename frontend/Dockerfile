# Build stage
FROM node:26-alpine AS builder

WORKDIR /app

# Copy package files
COPY package.json package-lock.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Production stage
FROM node:26-alpine AS production

WORKDIR /app

# Create non-root user for security
RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nuxt

# Copy built application from builder stage
COPY --from=builder --chown=nuxt:nodejs /app/.output /app/.output

USER nuxt

# Expose port
EXPOSE 3000

# Set environment variables
ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=3000

# Runtime config can be set via environment variables:
# NUXT_AUTH_ISSUER
# NUXT_AUTH_CLIENT_ID
# NUXT_AUTH_REDIRECT_URI
# NUXT_QUARKUS_URL

CMD ["node", ".output/server/index.mjs"]
