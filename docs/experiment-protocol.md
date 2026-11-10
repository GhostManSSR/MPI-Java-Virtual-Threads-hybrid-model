# Experiment protocol

## Real MPI runs in WSL

Run all MPI experiments from the project directory in WSL/Ubuntu with Maven,
Java 21+, Open MPI (`mpicc` and `mpirun`), and GNU Make installed.

```bash
make mpi-native
make mpi-smoke-matrix
```

For a single configuration:

```bash
make mpi-run ARCH=D SCENARIO=pingpong RANKS=2 TASKS=128 MESSAGE_SIZE=1024
```

For a full matrix:

```bash
make mpi-experiment-matrix RUN_ID=mpi-$(date -u +%Y%m%dT%H%M%SZ)
```

The matrix defaults to:

| Factor | Values |
|---|---|
| Architecture | A, B, C, D |
| Scenario | pingpong, allreduce, montecarlo, heat |
| MPI ranks | 2, 4 |
| Tasks per rank | 128, 512, 2048 |
| Message size | 64, 1024, 65536 bytes |
| Measured repetitions | 10 |
| Warmups | 1 |

This is 288 configurations and 2880 measured runs, in addition to the warmups.
Each MPI configuration is launched with `mpirun -np ranks`; the CLI `ranks`
value is checked against `MPI_Comm_size`. Every rank runs the same Java command,
and only rank 0 writes the CSV row. The run-order TSV, one CSV and one log per
run are stored under `experiments/results`.

The JNI bridge is built as `target/native/libmpi_bridge.so` with `mpicc`. The
bridge calls `MPI_Init_thread`, checks `MPI_Query_thread`, and calls
`MPI_Finalize`. A barrier synchronizes all ranks before each scenario. A
requires `MPI_THREAD_SINGLE`; B/C require
`MPI_THREAD_FUNNELED`; D requires `MPI_THREAD_MULTIPLE`, because the dedicated
communication thread performs MPI calls after initialization on the main
thread. Insufficient thread support fails the run rather than falling back.

The MPI scenarios issue the following operations:

- `pingpong`: one message-sized ring exchange per rank using `MPI_Sendrecv`.
- `allreduce`: one `MPI_Allreduce` over an array whose size matches
  `message_size` (must be a multiple of 8 bytes).
- `montecarlo`: deterministic local samples followed by an `MPI_Allreduce` of
  hit counts.
- `heat`: two halo exchanges per step using `MPI_Sendrecv`; the number of steps
  equals `tasks` per rank.

Every rank records its elapsed interval, and `MPI_MAX` selects the slowest
rank's duration for `durationMs` and the throughput denominator. The rank-0
CSV's `extra` field also records rank 0's local interval as `rankElapsedNanos`.
`communicationRatio` is rank 0's measured MPI-call time divided by the
maximum measured duration.
`communicationCalls` and `communicationBytesPerRank` in the CSV `extra` field
describe application-level calls and payload per rank, not physical network
traffic generated internally by a collective implementation. Duration is
measured in nanoseconds and reported with fractional-millisecond precision;
throughput is total completed tasks across ranks per second divided by the
maximum rank duration. `cpuUtilization` is process CPU time summed across MPI
ranks, divided by the maximum rank duration and the available logical
processor count, bounded to [0, 1] to account for CPU-time counter granularity;
for these single-host runs this reports utilization as a fraction of host
capacity. Very short runs may have coarse CPU utilization estimates.
`queueWaitMs` is the average submission-to-start
delay per rank, with the maximum rank average reported for MPI runs. A has no
task queue, so queue wait is zero. Historical CSV files may retain `-1` for
these fields; the dashboard treats those old values as missing.

Use unique `RUN_ID` values. Record the WSL/host version, CPU allocation, JVM
vendor and version, MPI implementation/version, and background load alongside
the result set. A quick smoke run confirms the JNI path and each operation; it
is not a statistically useful performance study. For example, a smaller
repeatable pilot is:

```bash
make mpi-experiment-matrix \
  ARCHITECTURES=A,B,C,D SCENARIOS=pingpong,allreduce \
  RANK_VALUES=2 TASK_VALUES=128,512 MESSAGE_SIZES=64,1024 \
  REPETITIONS=10 WARMUPS=1 ORDER_SEED=20261110 RUN_ID=mpi-pilot-20261110
```

## Local and container harnesses

`make experiment-matrix` and `make smoke-matrix` run the Java harness without
MPI initialization or MPI communication. `make container-matrix` invokes the
HTTP service, which also does not initialize MPI. Their output is useful for
testing the non-MPI harness and monitoring path only; do not combine these
files with real MPI results or describe them as MPI measurements.

## Interpretation limits

The real MPI targets launch ranks and execute communication, but current
benchmarks are a single-host research prototype. Each architecture includes
task creation and coordination in its timed interval. The results should be
compared only for identical configurations and treated as exploratory until
the workload, data correctness, and host-level resource measurements have been
independently validated. Report distributions (at least median and spread),
not only the best run.
