# Experiment protocol

## Running the matrix in WSL

`make experiment-matrix` builds the JAR, warms up every configuration once, then
executes 10 measured repetitions in a seeded, shuffled configuration order. By
default the factors are:

| Factor | Values |
|---|---|
| Architecture | A, B, C, D |
| Scenario | pingpong, allreduce, montecarlo, heat |
| Ranks | 2, 4 |
| Tasks | 128, 512, 2048 |
| Message size | 64, 1024, 65536 bytes |
| Measured repetitions | 10 |

This is 288 configurations and 2880 measured runs, in addition to the warmups.
Each measured run is saved to its own CSV under `experiments/results`; the
`<RUN_ID>_run-order.tsv` manifest records the seed and actual execution order.
The dashboard loads the resulting CSV files from that directory.

To run the same matrix through the `mpi-vt-research` Docker container and retain
Prometheus/Grafana samples, use `make container-matrix`. It sends one `curl`
request per run and waits 16 seconds after each measured request, matching the
15-second Prometheus scrape interval. The full default matrix therefore takes
at least 12.8 hours. Each HTTP response is also retained as JSON. The quick
`make container-smoke-matrix` target checks the HTTP path without waiting for
scrapes, so it is not intended for Grafana time-series comparisons.
For a single sample containing all four architecture series, run
`make container-example`: it makes one request with `architecture=all`,
`pingpong`, 2 ranks, 128 tasks, and a 1024-byte message, then waits for one
Prometheus scrape. Set Grafana's time range to the last 15 minutes to inspect it.

Grafana metric labels include the full architecture name, scenario, ranks,
task count, and message size so that different matrix configurations are
separate time series. In Streamlit, architecture and execution-mode labels are
normalized to the same A-D names used in the methodology.

Run `make smoke-matrix` first to check the local setup. On Java versions below
21, this target explicitly allows the application's platform-thread fallback
for C/D and warns that these smoke results are not virtual-thread evidence.
The full matrix rejects Java versions below 21 when C or D is selected unless
`ALLOW_PLATFORM_FALLBACK=1` is deliberately passed; do not use fallback runs
for claims about virtual threads. For a smaller pilot or
an exactly replayable ordering, override the factors and pass a fixed
`ORDER_SEED` and unique `RUN_ID`, for example:

```bash
make experiment-matrix ARCHITECTURES=A,C SCENARIOS=pingpong,heat \
  RANK_VALUES=2,4 TASK_VALUES=128,512 MESSAGE_SIZES=64,1024 \
  REPETITIONS=10 WARMUPS=1 ORDER_SEED=20261110 RUN_ID=pilot-20261110
```

Keep the machine, JVM, OS/WSL version, CPU allocation, and background load
constant between runs; record them alongside any reported results. Use the same
configuration for all architectures, retain per-run data, and report
distributions (at least median and spread), not only the best run. The matrix
creates observations; statistical significance and causal claims need a
predefined analysis and adequate independent measurements.

## Current implementation limits

The matrix improves repeatability and coverage but does not, by itself, validate
the scientific hypothesis. The current benchmark scenarios delegate to the
same executor workload; the “ranks” value is a workload parameter, not a launch
of that many MPI processes. Architecture A currently runs a local sequential
loop, and several exported metrics (including CPU utilization and communication
ratio) are constants supplied by the executor rather than measurements from the
OS or MPI runtime. Architecture C/D require a Java 21+ runtime for actual
virtual threads; older runtimes fall back to platform threads.

Treat current results as harness smoke-test or exploratory measurements, not
evidence of MPI cluster scalability or measured MPI communication performance.
Before drawing scientific conclusions, implement and validate real MPI
communication and workload-specific scenarios, collect metrics from the runtime
and operating system, and record the MPI implementation/thread support level
(`MPI_THREAD_*`) and environment for every experiment.
