# Build stage with musl for static linking
FROM rust:1.98.1 AS builder

WORKDIR /build

# musl C toolchain (needed by crates with C code such as ring) and musl target
RUN apt-get update \
    && apt-get install -y --no-install-recommends musl-tools \
    && rm -rf /var/lib/apt/lists/* \
    && rustup target add x86_64-unknown-linux-musl
ENV CC_x86_64_unknown_linux_musl=musl-gcc

# Copy the project files
COPY Cargo.* ./
COPY src ./src

# Build the application with static linking
# Uses musl libc for truly static binary (lower memory footprint)
RUN cargo build --release --target x86_64-unknown-linux-musl --locked

# Runtime stage - scratch image for minimal footprint
FROM scratch

WORKDIR /app

# Copy binary from builder (fully static, no runtime dependencies needed)
COPY --from=builder /build/target/x86_64-unknown-linux-musl/release/chronos_date_api /app/chronos_date_api

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["/app/chronos_date_api"]
