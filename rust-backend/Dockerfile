# Build stage with musl for static linking
FROM rust:1.98.1 as builder

WORKDIR /build

# Add musl target for static linking
RUN rustup target add x86_64-unknown-linux-musl

# Copy the project files
COPY Cargo.* ./
COPY src ./src

# Build the application with static linking
# Uses musl libc for truly static binary (lower memory footprint)
RUN cargo build --release --target x86_64-unknown-linux-musl --features web

# Runtime stage - scratch image for minimal footprint
FROM scratch

WORKDIR /app

# Copy binary from builder (fully static, no runtime dependencies needed)
COPY --from=builder /build/target/x86_64-unknown-linux-musl/release/chronos_date_api /app/chronos_date_api

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["/app/chronos_date_api"]
