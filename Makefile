APP_URL ?= http://localhost:8080
ARCH ?= all
SCENARIO ?= pingpong
TASKS ?= 128
RANKS ?= 2
MESSAGE_SIZE ?= 1024

PYTHON ?= python3
MAVEN ?= mvn
JAR ?= target/mpi-virtual-threads-research-0.1.0.jar
MPIEXEC ?= mpirun
MPIEXEC_ARGS ?= --oversubscribe
JAVA_HOME ?= $(shell dirname "$$(dirname "$$(readlink -f "$$(command -v javac)")")")
NATIVE_LIBRARY_DIR ?= target/native

ARCHITECTURES ?= A,B,C,D
SCENARIOS ?= pingpong,allreduce,montecarlo,heat
RANK_VALUES ?= 2,4
TASK_VALUES ?= 128,512,2048
MESSAGE_SIZES ?= 64,1024,65536
REPETITIONS ?= 10
WARMUPS ?= 1
RESULTS_DIR ?= experiments/results
RUN_ID ?= $(shell date -u +%Y%m%dT%H%M%SZ)
ORDER_SEED ?= $(shell date -u +%s)
ALLOW_PLATFORM_FALLBACK ?= 0
CONTAINER_NAME ?= mpi-vt-research
PROMETHEUS_SAMPLE_DELAY ?= 16

.PHONY: help up down restart status logs health run pingpong allreduce montecarlo heat all-scenarios \
	pure-mpi platform-threads virtual-threads dedicated-mpi-thread unit-test package \
	mpi-native mpi-run mpi-experiment-matrix mpi-smoke-matrix \
	experiment-matrix smoke-matrix container-matrix container-smoke-matrix container-example \
	analysis-install dashboard run-analysis analysis-summary

help:
	@echo "Docker: up down restart status logs health"
	@echo "Docker benchmark: make run [ARCH=A|B|C|D|all] [SCENARIO=pingpong|allreduce|montecarlo|heat|all]"
	@echo "Real MPI: make mpi-run [ARCH=A|B|C|D] [SCENARIO=pingpong|allreduce|montecarlo|heat] [RANKS=2]"
	@echo "Build JNI bridge: make mpi-native (run inside WSL with Open MPI installed)"
	@echo "Real MPI matrix: make mpi-experiment-matrix or quick check: make mpi-smoke-matrix"
	@echo "Local WSL matrix: make experiment-matrix [REPETITIONS=10] [WARMUPS=1]"
	@echo "Container/Grafana matrix: make container-matrix [PROMETHEUS_SAMPLE_DELAY=16]"
	@echo "Single Grafana example: make container-example"
	@echo "Matrix defaults: ARCHITECTURES=$(ARCHITECTURES) SCENARIOS=$(SCENARIOS)"
	@echo "  RANK_VALUES=$(RANK_VALUES) TASK_VALUES=$(TASK_VALUES) MESSAGE_SIZES=$(MESSAGE_SIZES)"
	@echo "Quick check: make smoke-matrix"
	@echo "Analysis dashboard: make analysis-install, then make dashboard"
	@echo "Tests: make unit-test"

up:
	docker compose up --build -d

down:
	docker compose down

restart:
	docker compose restart

status:
	docker compose ps

logs:
	docker compose logs -f --tail=100

health:
	curl -fsS "$(APP_URL)/health"

run:
	curl -fsS -X POST "$(APP_URL)/run" \
		--data-urlencode "architecture=$(ARCH)" \
		--data-urlencode "scenario=$(SCENARIO)" \
		--data-urlencode "tasks=$(TASKS)" \
		--data-urlencode "ranks=$(RANKS)" \
		--data-urlencode "message_size=$(MESSAGE_SIZE)"

pingpong: SCENARIO = pingpong
pingpong: run

allreduce: SCENARIO = allreduce
allreduce: run

montecarlo: SCENARIO = montecarlo
montecarlo: run

heat: SCENARIO = heat
heat: run

all-scenarios: SCENARIO = all
all-scenarios: run

pure-mpi: ARCH = A
pure-mpi: run

platform-threads: ARCH = B
platform-threads: run

virtual-threads: ARCH = C
virtual-threads: run

dedicated-mpi-thread: ARCH = D
dedicated-mpi-thread: run

unit-test:
	$(MAVEN) test

package:
	$(MAVEN) -DskipTests package

mpi-native: package
	command -v mpicc >/dev/null
	test -x "$(JAVA_HOME)/bin/javac"
	mkdir -p "$(NATIVE_LIBRARY_DIR)"
	mpicc -O2 -fPIC -shared \
		-I"$(JAVA_HOME)/include" \
		-I"$(JAVA_HOME)/include/linux" \
		-o "$(NATIVE_LIBRARY_DIR)/libmpi_bridge.so" \
		src/main/c/mpi_bridge.c

mpi-run: ARCH = A
mpi-run: mpi-native
	$(MPIEXEC) $(MPIEXEC_ARGS) -np "$(RANKS)" java \
		-Djava.library.path="$(abspath $(NATIVE_LIBRARY_DIR))" \
		-jar "$(abspath $(JAR))" --mpi \
		-a "$(ARCH)" -s "$(SCENARIO)" -t "$(TASKS)" -r "$(RANKS)" -m "$(MESSAGE_SIZE)"

mpi-experiment-matrix: mpi-native
	ARCHITECTURES="$(ARCHITECTURES)" \
	SCENARIOS="$(SCENARIOS)" \
	RANK_VALUES="$(RANK_VALUES)" \
	TASK_VALUES="$(TASK_VALUES)" \
	MESSAGE_SIZES="$(MESSAGE_SIZES)" \
	REPETITIONS="$(REPETITIONS)" \
	WARMUPS="$(WARMUPS)" \
	RESULTS_DIR="$(RESULTS_DIR)" \
	RUN_ID="$(RUN_ID)" \
	ORDER_SEED="$(ORDER_SEED)" \
	MPIEXEC="$(MPIEXEC)" \
	MPIEXEC_ARGS="$(MPIEXEC_ARGS)" \
	NATIVE_LIBRARY_DIR="$(abspath $(NATIVE_LIBRARY_DIR))" \
	JAR="$(abspath $(JAR))" \
	RUN_MODE=mpi \
	bash experiments/scripts/run_matrix.sh

mpi-smoke-matrix: ARCHITECTURES = A,B,C,D
mpi-smoke-matrix: SCENARIOS = pingpong,allreduce,montecarlo,heat
mpi-smoke-matrix: RANK_VALUES = 2
mpi-smoke-matrix: TASK_VALUES = 16
mpi-smoke-matrix: MESSAGE_SIZES = 64
mpi-smoke-matrix: REPETITIONS = 1
mpi-smoke-matrix: WARMUPS = 0
mpi-smoke-matrix: mpi-experiment-matrix

experiment-matrix: package
	ARCHITECTURES="$(ARCHITECTURES)" \
	SCENARIOS="$(SCENARIOS)" \
	RANK_VALUES="$(RANK_VALUES)" \
	TASK_VALUES="$(TASK_VALUES)" \
	MESSAGE_SIZES="$(MESSAGE_SIZES)" \
	REPETITIONS="$(REPETITIONS)" \
	WARMUPS="$(WARMUPS)" \
	RESULTS_DIR="$(RESULTS_DIR)" \
	RUN_ID="$(RUN_ID)" \
	ORDER_SEED="$(ORDER_SEED)" \
	ALLOW_PLATFORM_FALLBACK="$(ALLOW_PLATFORM_FALLBACK)" \
	JAR="$(JAR)" \
	bash experiments/scripts/run_matrix.sh

smoke-matrix: ARCHITECTURES = A,B,C,D
smoke-matrix: SCENARIOS = pingpong,allreduce,montecarlo,heat
smoke-matrix: RANK_VALUES = 2
smoke-matrix: TASK_VALUES = 128
smoke-matrix: MESSAGE_SIZES = 1024
smoke-matrix: REPETITIONS = 1
smoke-matrix: WARMUPS = 0
smoke-matrix: ALLOW_PLATFORM_FALLBACK = 1
smoke-matrix: experiment-matrix

container-matrix: up
	RUN_MODE=container \
	APP_URL="$(APP_URL)" \
	CONTAINER_NAME="$(CONTAINER_NAME)" \
	SAMPLE_DELAY="$(PROMETHEUS_SAMPLE_DELAY)" \
	ARCHITECTURES="$(ARCHITECTURES)" \
	SCENARIOS="$(SCENARIOS)" \
	RANK_VALUES="$(RANK_VALUES)" \
	TASK_VALUES="$(TASK_VALUES)" \
	MESSAGE_SIZES="$(MESSAGE_SIZES)" \
	REPETITIONS="$(REPETITIONS)" \
	WARMUPS="$(WARMUPS)" \
	RESULTS_DIR="$(RESULTS_DIR)" \
	RUN_ID="$(RUN_ID)" \
	ORDER_SEED="$(ORDER_SEED)" \
	bash experiments/scripts/run_matrix.sh

container-smoke-matrix: ARCHITECTURES = A,B,C,D
container-smoke-matrix: SCENARIOS = pingpong,allreduce,montecarlo,heat
container-smoke-matrix: RANK_VALUES = 2
container-smoke-matrix: TASK_VALUES = 128
container-smoke-matrix: MESSAGE_SIZES = 1024
container-smoke-matrix: REPETITIONS = 1
container-smoke-matrix: WARMUPS = 0
container-smoke-matrix: PROMETHEUS_SAMPLE_DELAY = 0
container-smoke-matrix: container-matrix

container-example: ARCHITECTURES = all
container-example: SCENARIOS = pingpong
container-example: RANK_VALUES = 2
container-example: TASK_VALUES = 128
container-example: MESSAGE_SIZES = 1024
container-example: REPETITIONS = 1
container-example: WARMUPS = 0
container-example: PROMETHEUS_SAMPLE_DELAY = 16
container-example: container-matrix

analysis-install:
	$(PYTHON) -m venv .venv
	.venv/bin/python -m pip install -r analysis/requirements.txt

dashboard:
	@test -x .venv/bin/streamlit || { echo "Install dashboard dependencies first: make analysis-install"; exit 1; }
	.venv/bin/python -m streamlit run analysis/dashboard.py

run-analysis: dashboard

analysis-summary:
	$(PYTHON) analysis/analyze.py
