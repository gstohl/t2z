# Linux build and test environment for t2z
# Includes all requirements: Node.js, Rust, Go, Java/Kotlin
# Supports both amd64 and arm64

FROM ubuntu:22.04

# Avoid interactive prompts
ENV DEBIAN_FRONTEND=noninteractive

# Install base dependencies
RUN apt-get update && apt-get install -y \
    curl \
    git \
    build-essential \
    pkg-config \
    libssl-dev \
    wget \
    unzip \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 20.x
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Install Rust (stable)
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
ENV PATH="/root/.cargo/bin:${PATH}"

# Install Go 1.22 (detect architecture)
RUN ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "amd64" ]; then GO_ARCH="amd64"; \
    elif [ "$ARCH" = "arm64" ]; then GO_ARCH="arm64"; \
    else echo "Unsupported arch: $ARCH" && exit 1; fi && \
    wget -q "https://go.dev/dl/go1.22.0.linux-${GO_ARCH}.tar.gz" && \
    tar -C /usr/local -xzf "go1.22.0.linux-${GO_ARCH}.tar.gz" && \
    rm "go1.22.0.linux-${GO_ARCH}.tar.gz"
ENV PATH="/usr/local/go/bin:${PATH}"
ENV GOPATH="/root/go"

# Install Java 17 (OpenJDK) - detect correct path for architecture
RUN apt-get update && apt-get install -y openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*
# Set JAVA_HOME dynamically based on architecture
RUN ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "amd64" ]; then \
        echo "export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64" >> /etc/profile.d/java.sh; \
    elif [ "$ARCH" = "arm64" ]; then \
        echo "export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64" >> /etc/profile.d/java.sh; \
    fi
ENV JAVA_HOME=""
# Will be set at runtime
RUN echo 'export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))' >> ~/.bashrc

# Set working directory
WORKDIR /app

# Copy the entire project
COPY . .

# Make gradle wrapper executable
RUN chmod +x bindings/kotlin/gradlew bindings/java/gradlew 2>/dev/null || true

# Set JAVA_HOME at runtime and run tests
CMD export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java)))) && \
    node scripts/clean.js && \
    node scripts/build-dev.js && \
    node scripts/test-all.js
