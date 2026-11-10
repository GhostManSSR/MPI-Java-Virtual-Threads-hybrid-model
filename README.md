# MPI + Java Virtual Threads research

Этот проект нужен для сравнения четырёх вариантов архитектур:

- A. Pure MPI
- B. MPI + Platform Threads
- C. MPI + Virtual Threads
- D. MPI + Virtual Threads + Dedicated MPI Thread

## Запуск в WSL

Открой WSL/Ubuntu, перейди в каталог проекта (подставь свой путь) и проверь инструменты:

```bash
cd "/mnt/d/My self/Vyz/confirene/MPI-Java-Virtual-Threads-hybrid-model"
make help
java -version
mvn -version
python3 --version
```

Для локальных экспериментов нужны GNU Make, Maven и Python 3. Полную матрицу
с архитектурами C/D запускай под Java 21 или новее, чтобы C/D действительно
использовали virtual threads. Java 17 годится для unit-тестов и сборки, но не
для научного сравнения virtual threads.

### Полная матрица экспериментов

Собрать и выполнить матрицу на всех архитектурах A-D и сценариях:

```bash
make experiment-matrix
```

По умолчанию проверяются 2 значения числа MPI ranks (`2,4`), 3 размера задачи
(`128,512,2048`), 3 размера сообщения (`64,1024,65536` байт), 4 сценария и все
4 архитектуры. Каждый вариант прогревается один раз и измеряется 10 раз. Итого:
288 конфигураций и 2880 измерений (плюс прогревы). Порядок конфигураций
перемешивается отдельно для каждого повтора; seed и фактический порядок
записываются в `experiments/results/<RUN_ID>_run-order.tsv`. Каждое измерение
сохраняется в отдельный CSV в `experiments/results`, который dashboard подхватит
автоматически.

Для проверки установки без долгого полного прогона (на Java 17 C/D используют
platform-thread fallback; этот smoke-тест не является научным измерением):

```bash
make smoke-matrix
```

Можно задавать уровни матрицы и воспроизводить порядок:

```bash
make experiment-matrix ARCHITECTURES=A,C SCENARIOS=pingpong,heat \
  RANK_VALUES=2,4 TASK_VALUES=128,512 MESSAGE_SIZES=64,1024 \
  REPETITIONS=10 WARMUPS=1 ORDER_SEED=20261110 RUN_ID=pilot-20261110
```

Используй новый `RUN_ID` для каждого запуска. Полный матричный запуск может
занять значительное время; начни с `make smoke-matrix` или сократи список
параметров. Таблица результатов сохраняется независимо для каждого запуска.

### Python dashboard

```bash
make analysis-install
make dashboard
```

Открой URL Streamlit, показанный в терминале (по умолчанию `http://localhost:8501`).
Dashboard читает CSV из `experiments/results`. Для текстовой статистической
сводки по этим CSV используй `make analysis-summary`.

## Как запускать в Docker

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

Полный прогон через HTTP API контейнера `mpi-vt-research` с теми же
288 конфигурациями и 10 повторами:

```bash
make container-matrix
```

Для запуска Docker-целей из WSL включи Docker Desktop WSL Integration для Ubuntu
и проверь, что `docker compose ps` выполняется внутри WSL.

Цель поднимает Docker Compose, делает 2880 последовательных `curl`-запросов к
`http://localhost:8080/run` и после каждого измерения ждёт 16 секунд, чтобы
Prometheus (scrape interval 15 секунд) успел сохранить точку в Grafana.
Полный прогон поэтому занимает не менее 12,8 часа. Результаты каждого запроса
сохраняются в отдельный CSV и JSON в `experiments/results`, порядок — в
`<RUN_ID>_run-order.tsv`. Для быстрого теста всех архитектур и сценариев:

```bash
make container-smoke-matrix
```

Smoke-цель не выдерживает интервал для Prometheus и нужна только для проверки
HTTP/контейнера. Для настройки длительности и объёма прогона используй те же
`ARCHITECTURES`, `SCENARIOS`, `RANK_VALUES`, `TASK_VALUES`, `MESSAGE_SIZES`,
`REPETITIONS`, `WARMUPS`, `RUN_ID` и `ORDER_SEED`, что и у локальной матрицы;
интервал можно переопределить через `PROMETHEUS_SAMPLE_DELAY`. Перезапусти
`make up`, чтобы загрузить изменения меток Grafana/Prometheus.

Для одного примера в Grafana используй:

```bash
make container-example
```

Это **один POST-запрос** для `pingpong`, `ranks=2`, `tasks=128`,
`message_size=1024`, с архитектурой `all` (A-D в одном ответе) и паузой
16 секунд для Prometheus. В Grafana выбери диапазон времени **Last 15 minutes**
и обнови dashboard после завершения команды.

В Streamlit названия A–D теперь совпадают с методологией, а на графиках Grafana
легенда включает архитектуру, сценарий, ranks, tasks и размер сообщения. Это
позволяет отличать серии при изменении параметров матрицы.

## Запуск Docker-тестов через Make (WSL)

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

Архитектуры: `A` (Pure MPI), `B` (MPI + Platform Threads),
`C` (MPI + Virtual Threads), `D` (MPI + Virtual Threads + Dedicated MPI Thread)
или `all` для сравнения всех четырёх.
Общие параметры можно передавать каждой цели, например:
`make run ARCH=all SCENARIO=heat TASKS=256 RANKS=4 MESSAGE_SIZE=2048`.
Для контейнеров доступны `make status`, `make logs`, `make restart` и `make down`.
`make down` сохраняет данные Grafana; для полной очистки named volumes используйте
`docker compose down -v`.

## Как запустить один локальный benchmark вручную

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
Полная матрица повышает повторяемость и покрытие параметров, но текущие сценарии
используют общий workload-заглушку: `ranks` не запускает процессы MPI, архитектура A
выполняет локальный последовательный цикл, а часть метрик задана константами.
Поэтому текущие результаты — проверка harness/исследовательский пилот, а не
доказательство ускорения MPI или виртуальных потоков. Подробные ограничения и
условия интерпретации описаны в [протоколе эксперимента](docs/experiment-protocol.md).
