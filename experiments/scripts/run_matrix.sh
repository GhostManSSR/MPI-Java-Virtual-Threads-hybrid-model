#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

IFS=',' read -r -a architectures <<< "${ARCHITECTURES:-A,B,C,D}"
IFS=',' read -r -a scenarios <<< "${SCENARIOS:-pingpong,allreduce,montecarlo,heat}"
IFS=',' read -r -a rank_values <<< "${RANK_VALUES:-2,4}"
IFS=',' read -r -a task_values <<< "${TASK_VALUES:-128,512,2048}"
IFS=',' read -r -a message_sizes <<< "${MESSAGE_SIZES:-64,1024,65536}"

repetitions="${REPETITIONS:-10}"
warmups="${WARMUPS:-1}"
results_dir="${RESULTS_DIR:-experiments/results}"
run_id="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
order_seed="${ORDER_SEED:-$(date -u +%s)}"
allow_platform_fallback="${ALLOW_PLATFORM_FALLBACK:-0}"
run_mode="${RUN_MODE:-local}"
app_url="${APP_URL:-http://localhost:8080}"
container_name="${CONTAINER_NAME:-mpi-vt-research}"
sample_delay="${SAMPLE_DELAY:-16}"
jar="${JAR:-target/mpi-virtual-threads-research-0.1.0.jar}"
native_library_dir="${NATIVE_LIBRARY_DIR:-$ROOT_DIR/target/native}"
mpi_exec_args_value="${MPIEXEC_ARGS:---oversubscribe}"
output_dir="$results_dir"
manifest="$output_dir/${run_id}_run-order.tsv"

if [[ "$run_mode" != "local" && "$run_mode" != "container" && "$run_mode" != "mpi" ]]; then
    echo "RUN_MODE must be 'local', 'container', or 'mpi'." >&2
    exit 1
fi
if [[ ( "$run_mode" == "local" || "$run_mode" == "mpi" ) && ! -f "$jar" ]]; then
    echo "Missing benchmark JAR: $jar (build it with 'make package')" >&2
    exit 1
fi
if [[ ( "$run_mode" == "local" || "$run_mode" == "mpi" ) && "$jar" != /* ]]; then
    jar="$ROOT_DIR/$jar"
fi
if [[ "$run_mode" == "mpi" ]]; then
    mpiexec="${MPIEXEC:-mpirun}"
    if ! command -v "$mpiexec" >/dev/null 2>&1; then
        echo "MPI launcher '$mpiexec' is unavailable; run this target inside WSL with Open MPI installed." >&2
        exit 1
    fi
    if [[ ! -f "$native_library_dir/libmpi_bridge.so" ]]; then
        echo "Missing JNI bridge: $native_library_dir/libmpi_bridge.so (build with 'make mpi-native')." >&2
        exit 1
    fi
    read -r -a mpi_exec_args <<< "$mpi_exec_args_value"
    mpi_version="$("$mpiexec" --version | head -n 1)"
fi

if [[ ! "$repetitions" =~ ^[1-9][0-9]*$ || ! "$warmups" =~ ^[0-9]+$ ]]; then
    echo "REPETITIONS must be positive and WARMUPS must be zero or positive." >&2
    exit 1
fi
if [[ ! "$sample_delay" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "SAMPLE_DELAY must be a non-negative number of seconds." >&2
    exit 1
fi
if [[ "$allow_platform_fallback" != "0" && "$allow_platform_fallback" != "1" ]]; then
    echo "ALLOW_PLATFORM_FALLBACK must be 0 or 1." >&2
    exit 1
fi

mkdir -p "$output_dir"
if [[ -e "$manifest" ]]; then
    echo "Run ID already exists: $run_id (choose a unique RUN_ID)" >&2
    exit 1
fi

configs=()
for architecture in "${architectures[@]}"; do
    for scenario in "${scenarios[@]}"; do
        for ranks in "${rank_values[@]}"; do
            for tasks in "${task_values[@]}"; do
                for message_size in "${message_sizes[@]}"; do
                    configs+=("$architecture,$scenario,$ranks,$tasks,$message_size")
                done
            done
        done
    done
done

if (( ${#configs[@]} == 0 )); then
    echo "The experiment matrix is empty; check the matrix variables." >&2
    exit 1
fi

if [[ "$run_mode" == "container" ]]; then
    if ! command -v docker >/dev/null 2>&1; then
        echo "Docker CLI is unavailable in WSL; enable Docker Desktop WSL integration." >&2
        exit 1
    fi
    ready=0
    for attempt in $(seq 1 30); do
        if curl -fsS "$app_url/health" >/dev/null 2>&1; then
            ready=1
            break
        fi
        sleep 1
    done
    if (( ! ready )); then
        echo "Container service is not healthy at $app_url; start it with 'make up'." >&2
        exit 1
    fi
    java_output="$(docker exec "$container_name" java -version 2>&1)" || {
        echo "$java_output" >&2
        echo "Could not read the Java runtime in container $container_name." >&2
        exit 1
    }
    java_version="$(sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p' <<< "$java_output")"
else
    java_version="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
fi
if [[ ! "$java_version" =~ ^[0-9]+$ ]]; then
    echo "Could not determine the Java runtime version." >&2
    exit 1
fi
if [[ "$run_mode" == "mpi" ]] && (( java_version < 21 )); then
    echo "Real MPI architecture comparisons require Java 21+; found Java $java_version." >&2
    exit 1
fi

requires_virtual_threads=0
for architecture in "${architectures[@]}"; do
    if [[ "$architecture" == "C" || "$architecture" == "D" || "$architecture" == "all" ]]; then
        requires_virtual_threads=1
        break
    fi
done
if (( requires_virtual_threads && java_version < 21 )) && (( allow_platform_fallback == 0 )); then
    echo "Architectures C/D require Java 21+ for virtual threads; found Java $java_version." >&2
    if [[ "$run_mode" == "container" ]]; then
        echo "Rebuild mpi-vt-research with the Java 21 Dockerfile, or set ALLOW_PLATFORM_FALLBACK=1 for smoke tests only." >&2
    else
        echo "Install a Java 21+ JDK in WSL, or set ALLOW_PLATFORM_FALLBACK=1 for non-scientific smoke tests only." >&2
    fi
    exit 1
fi
if (( requires_virtual_threads && java_version < 21 )); then
    echo "WARNING: Java $java_version falls back to platform threads for C/D; do not use these results as virtual-thread evidence." >&2
fi

if [[ "$run_mode" == "mpi" ]]; then
    printf '# run_id=%s\n# runner=%s\n# mpi_launcher=%s\n# mpi_version=%s\n# order_seed=%s\n# java_version=%s\n# native_library=%s\nrep\torder\tphase\tarchitecture\tscenario\tranks\ttasks\tmessage_size_bytes\n' \
        "$run_id" "$run_mode" "$mpiexec" "$mpi_version" "$order_seed" "$java_version" "$native_library_dir" > "$manifest"
else
    printf '# run_id=%s\n# runner=%s\n# app_url=%s\n# order_seed=%s\n# java_version=%s\n# sample_delay_seconds=%s\n# allow_platform_fallback=%s\nrep\torder\tphase\tarchitecture\tscenario\tranks\ttasks\tmessage_size_bytes\n' \
        "$run_id" "$run_mode" "$app_url" "$order_seed" "$java_version" "$sample_delay" "$allow_platform_fallback" > "$manifest"
fi
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
printf 'Run ID: %s\nRunner: %s\nJava version: %s\nOrder seed: %s\nConfigurations per repetition: %s\nWarmups per configuration: %s\nMeasured repetitions: %s\n' \
    "$run_id" "$run_mode" "$java_version" "$order_seed" "${#configs[@]}" "$warmups" "$repetitions"

run_benchmark() {
    local phase="$1"
    local repetition="$2"
    local order="$3"
    local config="$4"
    local architecture scenario ranks tasks message_size
    IFS=',' read -r architecture scenario ranks tasks message_size <<< "$config"

    local base="${run_id}_${architecture}_${scenario}_r${ranks}_t${tasks}_m${message_size}_rep${repetition}"
    local log_file="$output_dir/${base}_${phase}.log"
    local response_file="$temporary_dir/${base}_${phase}.json"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$repetition" "$order" "$phase" "$architecture" "$scenario" "$ranks" "$tasks" "$message_size" \
        >> "$manifest"

    if [[ "$run_mode" == "container" ]]; then
        if ! curl -fsS -X POST "$app_url/run" \
            --data-urlencode "architecture=$architecture" \
            --data-urlencode "scenario=$scenario" \
            --data-urlencode "tasks=$tasks" \
            --data-urlencode "ranks=$ranks" \
            --data-urlencode "message_size=$message_size" \
            -o "$response_file" 2> "$log_file"; then
            cat "$log_file" >&2
            echo "Container benchmark request failed; see $log_file" >&2
            return 1
        fi
        if ! python3 -c 'import json, sys; data = json.load(open(sys.argv[1], encoding="utf-8")); sys.exit(0 if data.get("status") == "ok" else 1)' "$response_file"; then
            cat "$response_file" >&2
            echo "Container returned an invalid benchmark response." >&2
            return 1
        fi
    elif [[ "$run_mode" == "mpi" ]]; then
        if ! (cd "$temporary_dir" && "$mpiexec" "${mpi_exec_args[@]}" -np "$ranks" \
            java -Djava.library.path="$native_library_dir" -jar "$jar" --mpi \
            -a "$architecture" -s "$scenario" -t "$tasks" -r "$ranks" -m "$message_size") \
            > "$log_file" 2>&1; then
            cat "$log_file" >&2
            echo "MPI benchmark failed; see $log_file" >&2
            return 1
        fi
    elif ! (cd "$temporary_dir" && java -jar "$jar" -a "$architecture" -s "$scenario" \
        -t "$tasks" -r "$ranks" -m "$message_size") > "$log_file" 2>&1; then
        cat "$log_file" >&2
        echo "Benchmark failed; see $log_file" >&2
        return 1
    fi

    if [[ "$phase" == "measure" ]]; then
        if [[ "$run_mode" == "container" ]]; then
            local temporary_csv="$results_dir/benchmark-results.csv"
            if [[ ! -s "$temporary_csv" ]]; then
                echo "Container did not write the expected CSV: $temporary_csv" >&2
                return 1
            fi
            cp "$temporary_csv" "$output_dir/${base}.csv"
            cp "$response_file" "$output_dir/${base}.json"
            if [[ "$sample_delay" != "0" && "$sample_delay" != "0.0" ]]; then
                sleep "$sample_delay"
            fi
        else
            local temporary_csv="$temporary_dir/experiments/results/benchmark-results.csv"
            if [[ ! -s "$temporary_csv" ]]; then
                echo "Benchmark did not produce the expected CSV: $temporary_csv" >&2
                return 1
            fi
            mv "$temporary_csv" "$output_dir/${base}.csv"
        fi
    fi
}

for (( repetition=1; repetition<=repetitions; repetition++ )); do
    seed=$((order_seed + repetition - 1))
    mapfile -t shuffled_configs < <(
        printf '%s\n' "${configs[@]}" |
            python3 -c 'import random, sys; rows = sys.stdin.read().splitlines(); random.Random(int(sys.argv[1])).shuffle(rows); print("\n".join(rows))' "$seed"
    )
    printf 'Repetition %s/%s (order seed %s)\n' "$repetition" "$repetitions" "$seed"

    for warmup in $(seq 1 "$warmups"); do
        order=0
        for config in "${shuffled_configs[@]}"; do
            order=$((order + 1))
            run_benchmark "warmup${warmup}" "$repetition" "$order" "$config"
        done
    done

    order=0
    for config in "${shuffled_configs[@]}"; do
        order=$((order + 1))
        IFS=',' read -r architecture scenario ranks tasks message_size <<< "$config"
        printf '  [%s/%s] %s %s ranks=%s tasks=%s message=%s\n' \
            "$order" "${#shuffled_configs[@]}" "$architecture" "$scenario" "$ranks" "$tasks" "$message_size"
        run_benchmark "measure" "$repetition" "$order" "$config"
    done
done

printf 'Completed %s measurements. CSVs and run-order.tsv: %s\n' \
    "$((${#configs[@]} * repetitions))" "$output_dir"
