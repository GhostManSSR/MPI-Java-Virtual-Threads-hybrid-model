# Methodology

The project compares four execution strategies using one MPI process per rank:

- A. Pure MPI: sequential local task execution; MPI calls are made by the process's main thread.
- B. MPI + Platform Threads: local tasks run in a bounded platform-thread pool; MPI calls are made by the main thread.
- C. MPI + Virtual Threads: local tasks run as virtual threads; MPI calls are made by the main thread.
- D. MPI + Virtual Threads + Dedicated MPI Thread: local work runs as virtual threads, while MPI calls are submitted to a dedicated platform thread.

The MPI JNI bridge initializes each process with the least thread-support level
required by its architecture: `MPI_THREAD_SINGLE` for A, `MPI_THREAD_FUNNELED`
for B/C, and `MPI_THREAD_MULTIPLE` for D. It checks the returned level and
`MPI_Query_thread`; the run aborts if the implementation provides insufficient
support. The MPI world size must equal the `ranks` argument and be at least two.
`tasks` is the number of tasks executed by each rank.

## Scenarios and communication

- `pingpong`: one fixed-size ring exchange through `MPI_Sendrecv`.
- `allreduce`: sum-reduction of a message-sized array through `MPI_Allreduce`.
- `montecarlo`: deterministic point samples are generated locally and hit counts are reduced with `MPI_Allreduce`.
- `heat`: each iteration exchanges left/right halo values with neighboring ranks through two `MPI_Sendrecv` calls.

All ranks use the same command line and participate in the same collective and
point-to-point calls. A barrier precedes each scenario. The root rank alone
writes the CSV output.

## Running and recorded metrics

Use `make mpi-native`, then run `make mpi-run` or `make mpi-experiment-matrix`
inside WSL/Ubuntu with Open MPI, Maven, and Java 21+. The latter runs one
`mpirun -np ranks` job for each matrix configuration and records a manifest,
per-run CSV, and log under `experiments/results`. `make experiment-matrix`
remains a local, non-MPI harness and must not be used as evidence of MPI
communication.

Each rank measures its local elapsed interval in nanoseconds; an `MPI_MAX`
reduction selects the slowest rank's duration, which is reported as fractional
milliseconds. Throughput is total tasks across ranks per second, divided by
that maximum duration. `communicationRatio` in the rank-0 CSV is rank 0's
measured MPI-call time divided by the maximum duration. Rank 0's elapsed time,
communication call count, and application-level payload bytes are included in
the extra CSV field; payload bytes are not estimates of transport-level
network traffic. `cpuUtilization` is process CPU time summed across ranks,
divided by the maximum rank duration and the number of available logical
processors, bounded to the range 0–1 to account for CPU-time counter
granularity; this is a host-normalized utilization ratio for this single-host
setup. Very short runs may therefore have coarse CPU utilization estimates.
`queueWaitMs` is the mean time from task submission to task
start on each rank; the reported MPI value is the maximum of those per-rank
means. Architecture A has no task queue, so its queue wait is zero. The
dashboard omits `-1` values found in older result files.

## Interpretation limits

The MPI runs use real MPI ranks and communication, but they are still a
single-host research prototype. The scenario kernels are intentionally small;
results include task creation and coordination costs, and should not be
generalized to cluster-scale performance without further validation. Record
the MPI implementation, JVM, host/WSL configuration, CPU allocation, and
background load. Compare identical configurations and report distributions,
not only a single best run.
