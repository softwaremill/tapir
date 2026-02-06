FROM mcr.microsoft.com/devcontainers/java:25

# Install essential tools
RUN apt-get update && \
    apt-get install -y vim curl wget git jq && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install sbt
RUN curl -fL "https://github.com/sbt/sbt/releases/download/v1.12.0/sbt-1.12.0.tgz" | tar xz -C /tmp && \
    mv /tmp/sbt/bin/sbt /usr/local/bin/ && \
    mv /tmp/sbt/bin/sbt-launch.jar /usr/local/bin/ && \
    chmod +x /usr/local/bin/sbt && \
    rm -rf /tmp/sbt