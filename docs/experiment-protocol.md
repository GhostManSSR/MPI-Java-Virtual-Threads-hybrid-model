# Experiment protocol

1. Warm up the JVM and benchmarked process.
2. Run each scenario at least 10 times with randomized ordering.
3. Capture per-run latency, throughput, CPU utilization, and queue wait metrics.
4. Export results as CSV and JSON.
5. Compare architecture A-D and report speedup and parallel efficiency.
6. Record thread support level (`MPI_THREAD_*`) before starting the benchmark matrix.

The benchmark matrix begins with a small pilot run to discard invalid or excessively expensive combinations. The results are then normalized to allow side-by-side comparison across architectures.
