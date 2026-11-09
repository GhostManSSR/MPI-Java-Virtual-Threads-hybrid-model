# MPI + Java Virtual Threads research

Этот проект нужен для сравнения четырёх вариантов архитектур:

- A. Pure MPI
- B. MPI + Platform Threads
- C. MPI + Virtual Threads
- D. MPI + Virtual Threads + Dedicated MPI Thread

## Как запускать в Docker

Сначала в WSL/Ubuntu:

```bash
cd /mnt/d/My\ self/Vyz/confirene/mpi-virtual-threads-research
ls
```

Поднять стенд целиком:

```bash
make up
```

Если нужно запустить в фоне:

```bash
docker compose up --build -d
```

Проверить состояние контейнеров:

```bash
docker compose ps
```

Посмотреть логи приложения:

```bash
docker compose logs -f app
```

Остановить:

```bash
docker compose down -v
```

## Где смотреть аналитику

После запуска:

- Grafana: http://localhost:3000
  - логин: admin
  - пароль: admin
- Prometheus: http://localhost:9090

Grafana автоматически загружает datasource Prometheus и dashboard `MPI + Virtual Threads`.
Dashboard показывает длительность и throughput последнего запуска, а также сравнение
duration, throughput, CPU utilization, communication ratio и queue wait по архитектурам.
До первого теста графики сравнений будут пустыми; запустите любой benchmark через Make.

## Запуск тестов через Make (WSL)

Из корня проекта:

```bash
make help
make up
make pingpong
make allreduce TASKS=256
make montecarlo ARCH=C TASKS=500
make heat RANKS=4 MESSAGE_SIZE=4096
make all-scenarios
```

Архитектуры: `A` (Pure MPI), `B` (Platform Threads), `C` (Virtual Threads),
`D` (Dedicated MPI Thread) или `all` для сравнения всех четырёх.
Общие параметры можно передавать каждой цели, например:
`make run ARCH=all SCENARIO=heat TASKS=256 RANKS=4 MESSAGE_SIZE=2048`.
Для контейнеров доступны `make status`, `make logs`, `make restart` и `make down`.
`make down` сохраняет данные Grafana; для полной очистки named volumes используйте
`docker compose down -v`.

## Как запустить локальный benchmark вручную

```bash
mvn test
mvn exec:java -Dexec.mainClass=research.Main -- -a all -s pingpong -t 128 -r 2
```

## Как собирать CSV из результатов

```bash
python3 analysis/analyze.py
python3 analysis/statistics.py
```

## Структура проекта

```text
mpi-virtual-threads-research/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── src/
│   ├── main/java/research/...
│   ├── main/c/mpi_bridge.c
│   └── test/java/research/...
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/
│       ├── dashboards/
│       └── provisioning/
├── experiments/
├── analysis/
├── docs/
└── README.md
```

## Что будет в итоговой аналитике

- latency / p50 / p95 / p99
- throughput
- CPU utilization
- queue wait time
- ratio communication vs compute
- сравнительный график A/B/C/D
- CSV/JSON и Grafana dashboard

## Примечание

Это исследовательский scaffold на одном узле, а не полноценный кластерный MPI benchmark. Для реальной масштабируемости нужно запускать MPI ранги на нескольких машинах/узлах.
