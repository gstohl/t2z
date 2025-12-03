# Zebra Mainnet Node
FROM rust:1.82-bookworm AS builder

RUN apt-get update && apt-get install -y \
    cmake \
    clang \
    libclang-dev \
    protobuf-compiler \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build
RUN git clone --depth 1 --branch v3.1.0 https://github.com/ZcashFoundation/zebra.git .
RUN cargo build --release --package zebrad --bin zebrad

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y \
    ca-certificates \
    curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m -s /bin/bash zebra
COPY --from=builder /build/target/release/zebrad /usr/local/bin/

RUN mkdir -p /home/zebra/.cache/zebra && chown -R zebra:zebra /home/zebra/.cache

USER zebra
WORKDIR /home/zebra
EXPOSE 8232 8233

CMD ["zebrad", "-c", "/etc/zebra/zebrad.toml", "start"]
