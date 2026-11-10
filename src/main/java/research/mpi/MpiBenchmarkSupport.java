package research.mpi;

import java.nio.ByteBuffer;

public final class MpiBenchmarkSupport {
    public record ExecutionResult(
            long value,
            long communicationNanos,
            long communicationCalls,
            long communicationBytes) {
    }

    private MpiBenchmarkSupport() {
    }

    public static ExecutionResult execute(
            String benchmarkName,
            long localValue,
            int taskCount,
            int messageSizeBytes) {
        if (!MpiRuntime.isActive()) {
            return new ExecutionResult(localValue, 0L, 0L, 0L);
        }

        int rank = MpiRuntime.rank();
        int size = MpiRuntime.size();
        return switch (benchmarkName) {
            case "pingpong" -> exchangeMessage(localValue, messageSizeBytes, rank, size);
            case "allreduce" -> allreduceMessage(localValue, messageSizeBytes);
            case "montecarlo" -> estimatePi(localValue, taskCount, size);
            case "heat" -> exchangeHeatHalos(localValue, taskCount, rank, size);
            default -> throw new IllegalArgumentException("Unsupported MPI benchmark: " + benchmarkName);
        };
    }

    private static ExecutionResult exchangeMessage(long localValue, int messageSizeBytes, int rank, int size) {
        requireMinimumMessageSize(messageSizeBytes);
        byte[] send = new byte[messageSizeBytes];
        byte[] receive = new byte[messageSizeBytes];
        ByteBuffer.wrap(send).putLong(0, localValue);
        long startNanos = System.nanoTime();
        MpiNativeBridge.mpiSendRecvBytes(
                send, receive, (rank + 1) % size, (rank + size - 1) % size, 0);
        long communicationNanos = System.nanoTime() - startNanos;
        return new ExecutionResult(
                localValue + ByteBuffer.wrap(receive).getLong(0),
                communicationNanos, 1L, messageSizeBytes);
    }

    private static ExecutionResult allreduceMessage(long localValue, int messageSizeBytes) {
        if (messageSizeBytes < Long.BYTES || messageSizeBytes % Long.BYTES != 0) {
            throw new IllegalArgumentException(
                    "allreduce message size must be a positive multiple of 8 bytes");
        }
        long[] values = new long[messageSizeBytes / Long.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = localValue + i;
        }
        long startNanos = System.nanoTime();
        MpiNativeBridge.mpiAllreduceSumLongArray(values);
        long communicationNanos = System.nanoTime() - startNanos;
        return new ExecutionResult(values[0], communicationNanos, 1L, messageSizeBytes);
    }

    private static ExecutionResult estimatePi(long localHits, int samplesPerRank, int size) {
        long[] values = {localHits};
        long startNanos = System.nanoTime();
        MpiNativeBridge.mpiAllreduceSumLongArray(values);
        long communicationNanos = System.nanoTime() - startNanos;
        long totalSamples = (long) samplesPerRank * size;
        long piEstimate = totalSamples == 0 ? 0 : (long) (4.0d * values[0] * 1_000_000_000d / totalSamples);
        return new ExecutionResult(piEstimate, communicationNanos, 1L, Long.BYTES);
    }

    private static ExecutionResult exchangeHeatHalos(long localValue, int steps, int rank, int size) {
        int leftRank = (rank + size - 1) % size;
        int rightRank = (rank + 1) % size;
        long left = localValue;
        long right = localValue + 1L;
        long startNanos = System.nanoTime();
        for (int step = 0; step < steps; step++) {
            long[] neighbors = MpiNativeBridge.mpiExchangeHalos(left, right, leftRank, rightRank);
            left = (left + neighbors[0]) / 2L;
            right = (right + neighbors[1]) / 2L;
        }
        long communicationNanos = System.nanoTime() - startNanos;
        return new ExecutionResult(
                left + right,
                communicationNanos,
                2L * steps,
                2L * steps * Long.BYTES);
    }

    private static void requireMinimumMessageSize(int messageSizeBytes) {
        if (messageSizeBytes < Long.BYTES) {
            throw new IllegalArgumentException("MPI message size must be at least 8 bytes");
        }
    }
}
