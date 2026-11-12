# syntax=docker/dockerfile:1
# Agenten-Image von chronos-root für die Pod-Plattform des Agent Orchestrators: Basis-Image der Plattform (Claude Code CLI, gh, git, kubectl, jq,
# Python, Shell-Werkzeuge, Benutzer agent) plus die Werkzeuge des Projekts: Rust (rust-backend/), JDK 21 (backend/, über ./mvnw), Node 26 (frontend/),
# Build-Werkzeuge und psql. Versionen und Prüfsummen nur aus build-args.env; Bau: build/k8s-build-image.sh der Plattform.
ARG BASE_IMAGE
FROM ${BASE_IMAGE}

ARG TEMURIN_VERSION
ARG TEMURIN_SHA256
ARG NODE_PROJECT_VERSION
ARG NODE_PROJECT_SHA256
ARG RUSTUP_VERSION
ARG RUSTUP_SHA256
ARG RUST_VERSION

USER root
SHELL ["/bin/bash", "-euo", "pipefail", "-c"]
RUN for v in TEMURIN_VERSION TEMURIN_SHA256 NODE_PROJECT_VERSION NODE_PROJECT_SHA256 RUSTUP_VERSION RUSTUP_SHA256 RUST_VERSION; do \
        [ -n "${!v}" ] || { echo "Build-Argument $v fehlt (build-args.env)" >&2; exit 1; }; \
    done

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential pkg-config libssl-dev postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# JDK 21 (Temurin, Archiv mit Prüfsumme); Maven kommt über ./mvnw des Repos.
RUN curl -fsSL -o /tmp/jdk.tar.gz "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-${TEMURIN_VERSION/+/%2B}/OpenJDK21U-jdk_x64_linux_hotspot_${TEMURIN_VERSION/+/_}.tar.gz" \
    && echo "${TEMURIN_SHA256}  /tmp/jdk.tar.gz" | sha256sum -c - \
    && mkdir /opt/java && tar -xzf /tmp/jdk.tar.gz --strip-components=1 -C /opt/java && rm /tmp/jdk.tar.gz

# Node des Projekts (CI: node-version 26); die Basis-Node bleibt nur Laufzeit der Claude-CLI.
RUN curl -fsSL -o /tmp/node.tar.xz "https://nodejs.org/dist/v${NODE_PROJECT_VERSION}/node-v${NODE_PROJECT_VERSION}-linux-x64.tar.xz" \
    && echo "${NODE_PROJECT_SHA256}  /tmp/node.tar.xz" | sha256sum -c - \
    && mkdir /opt/node-project && tar -xJf /tmp/node.tar.xz --strip-components=1 -C /opt/node-project && rm /tmp/node.tar.xz

# Rust (rustup-init mit Prüfsumme, feste Toolchain mit clippy und rustfmt); CARGO_HOME gehört agent (Registry-Cache zur Laufzeit).
ENV RUSTUP_HOME=/opt/rustup CARGO_HOME=/opt/cargo
RUN curl -fsSL -o /tmp/rustup-init "https://static.rust-lang.org/rustup/archive/${RUSTUP_VERSION}/x86_64-unknown-linux-gnu/rustup-init" \
    && echo "${RUSTUP_SHA256}  /tmp/rustup-init" | sha256sum -c - \
    && chmod +x /tmp/rustup-init \
    && /tmp/rustup-init -y --no-modify-path --profile minimal --default-toolchain "${RUST_VERSION}" -c clippy -c rustfmt \
    && rm /tmp/rustup-init && chown -R agent:agent /opt/cargo /opt/rustup

ENV JAVA_HOME=/opt/java \
    PATH=/opt/cargo/bin:/opt/node-project/bin:/opt/java/bin:/opt/node/bin:/home/agent/.npm-global/bin:/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin

RUN java -version && node --version && cargo --version && cargo clippy --version && psql --version && claude --version

COPY --chmod=0644 .agent-orchestrator/image/tools.list /etc/agent-orchestrator/tools.list

USER agent
WORKDIR /work
