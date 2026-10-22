# Build stage with musl for static linking
FROM rust:1.98.1 AS builder

WORKDIR /build

# musl-tools provides musl-gcc; perl and make are needed to build the vendored
# OpenSSL pulled in by web-push (see kb/adr/ADR-2026-10-13-013-vendored-openssl-musl-image.md).
RUN apt-get update \
    && apt-get install -y --no-install-recommends musl-tools perl make \
    && rm -rf /var/lib/apt/lists/*

# Add musl target for static linking
RUN rustup target add x86_64-unknown-linux-musl

# Copy the project files
COPY Cargo.* ./
COPY migrations ./migrations
COPY schema ./schema
COPY src ./src

# Build the application with static linking
# Uses musl libc for truly static binary (lower memory footprint)
RUN cargo build --release --target x86_64-unknown-linux-musl --locked

# Runtime stage - scratch image for minimal footprint
FROM scratch

WORKDIR /app

# CA certificates for outbound TLS (push services, Keycloak); see the ADR above
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt

# Copy binary from builder (fully static, no runtime dependencies needed)
COPY --from=builder /build/target/x86_64-unknown-linux-musl/release/chronos_date_api /app/chronos_date_api

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["/app/chronos_date_api"]
