# Zebra Testnet Node
# Uses official release (no internal-miner needed for testnet)
FROM rust:1.82-bookworm as builder

# Install build dependencies
RUN apt-get update && apt-get install -y \
    cmake \
    clang \
    libclang-dev \
    protobuf-compiler \
    && rm -rf /var/lib/apt/lists/*

# Clone and build Zebra
WORKDIR /build
RUN git clone --depth 1 --branch v3.1.0 https://github.com/ZcashFoundation/zebra.git
WORKDIR /build/zebra

# Build zebrad (standard build for testnet)
RUN cargo build --release -p zebrad

# Runtime image
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y \
    ca-certificates \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/zebra/target/release/zebrad /usr/local/bin/

# Create zebra user and directories
RUN useradd -m -s /bin/bash zebra \
    && mkdir -p /etc/zebrad /home/zebra/.cache/zebra \
    && chown -R zebra:zebra /home/zebra

USER zebra
WORKDIR /home/zebra

ENTRYPOINT ["zebrad"]
CMD ["-c", "/etc/zebrad/zebra.toml", "start"]
