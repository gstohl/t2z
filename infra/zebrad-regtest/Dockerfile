# Build Zebra with internal-miner feature for regtest
# Based on https://zebra.zfnd.org/user/regtest.html
FROM rust:1.82-bookworm as builder

# Install build dependencies
RUN apt-get update && apt-get install -y \
    cmake \
    clang \
    libclang-dev \
    protobuf-compiler \
    && rm -rf /var/lib/apt/lists/*

# Clone and build Zebra with internal-miner feature
WORKDIR /build
RUN git clone --depth 1 --branch v2.2.0 https://github.com/ZcashFoundation/zebra.git
WORKDIR /build/zebra

# Build zebrad with internal-miner feature (required for regtest auto-mining)
RUN cargo build --release -p zebrad --features "internal-miner"

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
