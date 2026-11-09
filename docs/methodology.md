# Methodology

The project follows a four-architecture comparison model to evaluate the scalability of hybrid MPI + Java thread execution:

- A. Pure MPI
- B. MPI + Platform Threads
- C. MPI + Virtual Threads
- D. MPI + Virtual Threads + Dedicated MPI Thread

Each architecture uses the same benchmark logic and only changes the execution strategy and communication model. The design explicitly separates the benchmark workload, the runtime abstraction, and the metric export layer to avoid conflating algorithmic differences with orchestration differences.

## Experimental design

The harness runs a small matrix of scenarios covering communication-heavy and compute-heavy workloads. Every configuration is repeated to estimate variance and to calculate mean, median, and standard deviation. The results are stored as CSV to support downstream statistical analysis.

## Scientific caveat

The system is designed for reproducibility on a single machine using Docker Compose. It is not a cluster-scale MPI benchmark and should be described as a single-node experimental environment with explicit limitations.
