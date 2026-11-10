FROM eclipse-temurin:25-jdk-noble

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
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

RUN java -version && mvn -version && \
    mvn -q -DskipTests package

CMD ["bash", "-lc", "java -jar target/mpi-virtual-threads-research-0.1.0.jar --help"]