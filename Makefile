APP_URL ?= http://localhost:8080
ARCH ?= all
SCENARIO ?= pingpong
TASKS ?= 128
RANKS ?= 2
MESSAGE_SIZE ?= 1024

.PHONY: help up down restart status logs health run pingpong allreduce montecarlo heat all-scenarios pure-mpi platform-threads virtual-threads dedicated-mpi-thread unit-test

help:
	@echo "Docker: up down restart status logs"
	@echo "Run benchmark: make run [ARCH=A|B|C|D|all] [SCENARIO=pingpong|allreduce|montecarlo|heat|all] [TASKS=128] [RANKS=2] [MESSAGE_SIZE=1024]"
	@echo "Scenarios: pingpong allreduce montecarlo heat all-scenarios"
	@echo "Architectures: pure-mpi platform-threads virtual-threads dedicated-mpi-thread"
	@echo "Tests: unit-test"

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
	mvn test
