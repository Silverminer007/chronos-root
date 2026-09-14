# Build stage
FROM rust:latest as builder

WORKDIR /build

# Copy the project files
COPY Cargo.* ./
COPY src ./src

# Build the application
RUN cargo build --release --features web

# Runtime stage
FROM debian:bookworm-slim

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy binary from builder
COPY --from=builder /build/target/release/chronos_date_api /app/

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/q/health/live || exit 1

# Expose port
EXPOSE 8080

# Run the application
CMD ["/app/chronos_date_api"]
