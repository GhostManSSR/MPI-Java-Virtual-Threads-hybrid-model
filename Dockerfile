FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-21-jdk-headless \
    maven \
    build-essential \
    openmpi-bin \
    libopenmpi-dev \
    curl \
    python3 \
    python3-pip \
    ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . /workspace

RUN mvn -q -DskipTests package

CMD ["bash", "-lc", "java -jar target/mpi-virtual-threads-research-0.1.0.jar --help"]
