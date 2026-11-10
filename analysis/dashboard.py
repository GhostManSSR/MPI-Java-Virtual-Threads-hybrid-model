from __future__ import annotations

import re
from pathlib import Path

import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
import streamlit as st


# ============================================================
# CONFIGURATION
# ============================================================

APP_DIR = Path(__file__).resolve().parent
PROJECT_DIR = APP_DIR.parent
DEFAULT_RESULTS_DIR = PROJECT_DIR / "experiments" / "results"

st.set_page_config(
    page_title="MPI & Virtual Threads — Benchmark Dashboard",
    page_icon="📊",
    layout="wide",
    initial_sidebar_state="expanded",
)


# ============================================================
# STYLING
# ============================================================

st.markdown(
    """
    <style>
        .block-container {
            padding-top: 1.5rem;
            padding-bottom: 2rem;
        }

        [data-testid="stMetric"] {
            background: rgba(128, 128, 128, 0.08);
            border: 1px solid rgba(128, 128, 128, 0.2);
            padding: 16px;
            border-radius: 12px;
        }

        .section-description {
            color: #888;
            font-size: 0.9rem;
            margin-top: -10px;
            margin-bottom: 15px;
        }

        .status-card {
            padding: 14px;
            border-radius: 10px;
            background: rgba(128, 128, 128, 0.08);
            margin-bottom: 10px;
        }
    </style>
    """,
    unsafe_allow_html=True,
)


# ============================================================
# DATA LOADING
# ============================================================

COLUMN_ALIASES = {
    "architecture": ["architecture", "arch", "implementation"],
    "benchmark": ["benchmark", "scenario", "test", "workload"],
    "ranks": ["ranks", "rank", "mpi_ranks", "processes", "process_count"],
    "taskCount": ["taskcount", "task_count", "tasks", "number_of_tasks"],
    "messageSizeBytes": [
        "messagesizebytes",
        "message_size_bytes",
        "message_size",
    ],
    "durationMs": [
        "durationms",
        "duration_ms",
        "execution_time_ms",
        "time_ms",
    ],
    "throughput": ["throughput", "ops_per_second", "operations_per_second"],
    "communicationRatio": [
        "communicationratio",
        "communication_ratio",
    ],
    "cpuUtilization": [
        "cpuutilization",
        "cpu_utilization",
        "cpu_usage",
    ],
    "queueWaitMs": [
        "queuewaitms",
        "queue_wait_ms",
        "queue_wait",
    ],
    "extra": ["extra", "metadata", "details"],
}

ARCHITECTURE_LABELS = {
    "A": "A. Pure MPI",
    "B": "B. MPI + Platform Threads",
    "C": "C. MPI + Virtual Threads",
    "D": "D. MPI + Virtual Threads + Dedicated MPI Thread",
}

EXECUTION_MODE_LABELS = {
    "pure-mpi": ARCHITECTURE_LABELS["A"],
    "platform-threads": ARCHITECTURE_LABELS["B"],
    "virtual-threads": ARCHITECTURE_LABELS["C"],
    "dedicated-mpi-thread": ARCHITECTURE_LABELS["D"],
}


def normalize_column_name(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", str(name).lower())


def normalize_columns(df: pd.DataFrame) -> pd.DataFrame:
    """
    Приводит названия столбцов к единому формату.
    Поддерживает несколько вариантов названий одной метрики.
    """
    normalized = {
        normalize_column_name(col): col
        for col in df.columns
    }

    rename = {}

    for canonical, aliases in COLUMN_ALIASES.items():
        for alias in aliases:
            key = normalize_column_name(alias)

            if key in normalized:
                rename[normalized[key]] = canonical
                break

    return df.rename(columns=rename)


def read_csv_file(path: Path) -> pd.DataFrame:
    """
    Читает CSV с автоматическим определением разделителя.
    """
    try:
        df = pd.read_csv(
            path,
            sep=None,
            engine="python",
            encoding="utf-8-sig",
        )
    except UnicodeDecodeError:
        df = pd.read_csv(
            path,
            sep=None,
            engine="python",
            encoding="cp1251",
        )

    df = normalize_columns(df)
    df["source_file"] = path.name

    return df


@st.cache_data(show_spinner=False)
def load_results(files: tuple[tuple[str, int], ...]) -> pd.DataFrame:
    """
    Загружает и объединяет результаты всех CSV-файлов.
    files содержит путь и время изменения каждого файла.
    """
    frames = []

    for file_path, _ in files:
        path = Path(file_path)

        try:
            df = read_csv_file(path)

            if not df.empty:
                frames.append(df)

        except Exception as exc:
            st.warning(f"Не удалось прочитать {path.name}: {exc}")

    if not frames:
        return pd.DataFrame()

    return pd.concat(frames, ignore_index=True, sort=False)


def parse_extra(value) -> dict:
    """
    Разбирает метаданные формата:
    {checksum=123; mode=virtual-threads}
    """
    if pd.isna(value):
        return {}

    result = {}

    text = str(value).strip()
    text = text.strip("{}")

    for part in text.split(";"):
        if "=" not in part:
            continue

        key, val = part.split("=", 1)
        result[key.strip().lower()] = val.strip()

    return result


def prepare_data(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty:
        return df

    df = df.copy()

    # Преобразование числовых метрик.
    numeric_columns = [
        "ranks",
        "taskCount",
        "messageSizeBytes",
        "durationMs",
        "throughput",
        "communicationRatio",
        "cpuUtilization",
        "queueWaitMs",
    ]

    for col in numeric_columns:
        if col in df.columns:
            df[col] = pd.to_numeric(
                df[col].astype(str).str.replace(",", ".", regex=False),
                errors="coerce",
            )
            if col in {"cpuUtilization", "queueWaitMs"}:
                df.loc[df[col] < 0, col] = pd.NA

    # Разбор extra.
    if "extra" in df.columns:
        metadata = df["extra"].apply(parse_extra)

        df["execution_mode"] = metadata.apply(
            lambda x: x.get("mode", "не указан")
        )

        df["checksum"] = metadata.apply(
            lambda x: x.get("checksum")
        )
    else:
        df["execution_mode"] = "не указан"

    # Заполнение категориальных столбцов.
    for col in ["architecture", "benchmark", "execution_mode"]:
        if col not in df.columns:
            df[col] = "не указано"

        df[col] = (
            df[col]
            .fillna("не указано")
            .astype(str)
        )

    df["architecture"] = df["architecture"].replace(ARCHITECTURE_LABELS)
    df["execution_mode"] = df["execution_mode"].replace(EXECUTION_MODE_LABELS)

    return df


def discover_files(results_dir: Path) -> tuple:
    if not results_dir.exists():
        return tuple()

    files = []

    for path in sorted(results_dir.rglob("*.csv")):
        try:
            files.append(
                (str(path), path.stat().st_mtime_ns)
            )
        except OSError:
            continue

    return tuple(files)


def format_number(value, digits=2):
    if pd.isna(value):
        return "—"

    return f"{value:,.{digits}f}".replace(",", " ")


def has_metric(df: pd.DataFrame, column: str) -> bool:
    return (
        column in df.columns
        and df[column].notna().any()
    )


# ============================================================
# HEADER
# ============================================================

st.title("📊 MPI & Virtual Threads")
st.caption(
    "Интерактивная аналитическая панель экспериментального "
    "исследования производительности многопоточных архитектур."
)

st.divider()


# ============================================================
# SIDEBAR
# ============================================================

st.sidebar.title("⚙️ Настройки")

results_dir_input = st.sidebar.text_input(
    "Папка с результатами",
    value=str(DEFAULT_RESULTS_DIR),
)

results_dir = Path(results_dir_input).expanduser()

files = discover_files(results_dir)

st.sidebar.caption(
    f"Найдено CSV-файлов: **{len(files)}**"
)

if st.sidebar.button("🔄 Обновить данные", use_container_width=True):
    st.cache_data.clear()
    st.rerun()

uploaded_files = st.sidebar.file_uploader(
    "Дополнительно загрузить CSV",
    type=["csv"],
    accept_multiple_files=True,
)

# Загрузка из папки.
data = load_results(files) if files else pd.DataFrame()

# Загрузка файлов вручную.
if uploaded_files:
    uploaded_frames = []

    for uploaded in uploaded_files:
        try:
            frame = pd.read_csv(
                uploaded,
                sep=None,
                engine="python",
            )
            frame = normalize_columns(frame)
            frame["source_file"] = uploaded.name
            uploaded_frames.append(frame)

        except Exception as exc:
            st.sidebar.error(
                f"{uploaded.name}: {exc}"
            )

    if uploaded_frames:
        data = pd.concat(
            [data, *uploaded_frames],
            ignore_index=True,
            sort=False,
        )

data = prepare_data(data)


# ============================================================
# EMPTY STATE
# ============================================================

if data.empty:
    st.warning(
        "Результаты экспериментов не найдены."
    )

    st.markdown(
        f"""
        Ожидаемая папка:

        `{DEFAULT_RESULTS_DIR}`

        Помести CSV-файлы с результатами экспериментов "
        "в эту папку или загрузи их через боковую панель.
        """
    )

    st.stop()


# ============================================================
# FILTERS
# ============================================================

st.sidebar.divider()
st.sidebar.subheader("Фильтрация")

architectures = sorted(
    data["architecture"].dropna().unique().tolist()
)

benchmarks = sorted(
    data["benchmark"].dropna().unique().tolist()
)

modes = sorted(
    data["execution_mode"].dropna().unique().tolist()
)

selected_architectures = st.sidebar.multiselect(
    "Архитектуры",
    architectures,
    default=architectures,
)

selected_benchmarks = st.sidebar.multiselect(
    "Сценарии",
    benchmarks,
    default=benchmarks,
)

selected_modes = st.sidebar.multiselect(
    "Режим выполнения",
    modes,
    default=modes,
)

filtered = data[
    data["architecture"].isin(selected_architectures)
    & data["benchmark"].isin(selected_benchmarks)
    & data["execution_mode"].isin(selected_modes)
].copy()

st.sidebar.caption(
    f"Выбрано измерений: **{len(filtered)}**"
)

if filtered.empty:
    st.warning(
        "Нет данных, соответствующих выбранным фильтрам."
    )
    st.stop()


# ============================================================
# KPI
# ============================================================

st.subheader("Общая статистика")

metric_columns = st.columns(4)

metric_columns[0].metric(
    "Измерений",
    len(filtered),
)

metric_columns[1].metric(
    "Архитектур",
    filtered["architecture"].nunique(),
)

metric_columns[2].metric(
    "Сценариев",
    filtered["benchmark"].nunique(),
)

metric_columns[3].metric(
    "MPI-процессов",
    (
        int(filtered["ranks"].max())
        if has_metric(filtered, "ranks")
        else "—"
    ),
)

if has_metric(filtered, "throughput"):
    st.caption(
        f"Средний Throughput: "
        f"**{format_number(filtered['throughput'].mean())}**"
    )

st.divider()


# ============================================================
# PERFORMANCE
# ============================================================

st.header("1. Производительность")

st.markdown(
    '<p class="section-description">'
    'Сравнение пропускной способности и времени выполнения '
    'экспериментальных сценариев.</p>',
    unsafe_allow_html=True,
)

col1, col2 = st.columns(2)

with col1:
    st.subheader("Throughput по архитектурам")

    if has_metric(filtered, "throughput"):
        fig = px.box(
            filtered,
            x="architecture",
            y="throughput",
            color="architecture",
            points="all",
            hover_data=[
                c for c in [
                    "benchmark",
                    "execution_mode",
                    "ranks",
                    "taskCount",
                ]
                if c in filtered.columns
            ],
            labels={
                "architecture": "Архитектура",
                "throughput": "Throughput",
            },
            title="Распределение пропускной способности",
        )

        fig.update_layout(
            showlegend=False,
            height=420,
        )

        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("В CSV отсутствует метрика throughput.")

with col2:
    st.subheader("Время выполнения")

    if has_metric(filtered, "durationMs"):
        fig = px.box(
            filtered,
            x="architecture",
            y="durationMs",
            color="architecture",
            points="all",
            hover_data=[
                c for c in [
                    "benchmark",
                    "execution_mode",
                    "ranks",
                    "taskCount",
                ]
                if c in filtered.columns
            ],
            labels={
                "architecture": "Архитектура",
                "durationMs": "Время, мс",
            },
            title="Распределение времени выполнения",
        )

        fig.update_layout(
            showlegend=False,
            height=420,
        )

        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("В CSV отсутствует durationMs.")


# ============================================================
# SCALABILITY
# ============================================================

st.divider()
st.header("2. Масштабируемость")

st.markdown(
    '<p class="section-description">'
    'Зависимость производительности от количества MPI-процессов. '
    'Для достоверного сравнения нужны измерения при разных значениях ranks.</p>',
    unsafe_allow_html=True,
)

if (
    has_metric(filtered, "ranks")
    and has_metric(filtered, "throughput")
):
    scaling_data = (
        filtered
        .dropna(subset=["ranks", "throughput"])
        .groupby(
            ["architecture", "execution_mode", "ranks"],
            as_index=False,
        )
        .agg(
            throughput=("throughput", "median"),
            measurements=("throughput", "count"),
        )
        .sort_values("ranks")
    )

    if scaling_data["ranks"].nunique() > 1:
        fig = px.line(
            scaling_data,
            x="ranks",
            y="throughput",
            color="architecture",
            line_dash="execution_mode",
            markers=True,
            hover_data=["measurements"],
            labels={
                "ranks": "Количество MPI-процессов",
                "throughput": "Медианный Throughput",
                "architecture": "Архитектура",
                "execution_mode": "Режим",
                "measurements": "Измерений",
            },
            title="Масштабирование пропускной способности",
        )

        fig.update_layout(height=460)
        st.plotly_chart(fig, use_container_width=True)

    else:
        st.info(
            "Для построения кривой масштабируемости нужны "
            "измерения как минимум при двух разных значениях ranks. "
            f"Сейчас доступно: {scaling_data['ranks'].iloc[0]:g} MPI-процессов."
        )

    with st.expander("Данные для графика масштабируемости"):
        st.dataframe(
            scaling_data,
            use_container_width=True,
            hide_index=True,
        )

else:
    st.info(
        "Для анализа масштабируемости необходимы "
        "столбцы ranks и throughput."
    )


# ============================================================
# RESOURCE UTILIZATION
# ============================================================

st.divider()
st.header("3. Использование ресурсов")

resource_columns = st.columns(2)

with resource_columns[0]:
    st.subheader("Загрузка CPU")

    if has_metric(filtered, "cpuUtilization"):
        cpu_data = (
            filtered
            .groupby(
                ["architecture", "execution_mode"],
                as_index=False,
            )
            .agg(
                cpu=("cpuUtilization", "mean"),
            )
        )

        fig = px.bar(
            cpu_data,
            x="architecture",
            y="cpu",
            color="execution_mode",
            barmode="group",
            text_auto=".3f",
            labels={
                "architecture": "Архитектура",
                "cpu": "CPU Utilization",
                "execution_mode": "Режим",
            },
            title="Средняя загрузка CPU",
        )

        fig.update_layout(height=400)
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("Нет данных о загрузке CPU.")

with resource_columns[1]:
    st.subheader("Communication Ratio")

    if has_metric(filtered, "communicationRatio"):
        communication_data = (
            filtered
            .groupby(
                ["architecture", "execution_mode"],
                as_index=False,
            )
            .agg(
                ratio=("communicationRatio", "mean"),
            )
        )

        fig = px.bar(
            communication_data,
            x="architecture",
            y="ratio",
            color="execution_mode",
            barmode="group",
            text_auto=".3f",
            labels={
                "architecture": "Архитектура",
                "ratio": "Communication Ratio",
                "execution_mode": "Режим",
            },
            title="Доля коммуникационных операций",
        )

        fig.update_layout(height=400)
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("Нет данных о Communication Ratio.")


# ============================================================
# QUEUE WAIT
# ============================================================

st.divider()
st.header("4. Ожидание в очереди")

if has_metric(filtered, "queueWaitMs"):
    queue_data = (
        filtered
        .groupby(
            ["architecture", "execution_mode"],
            as_index=False,
        )
        .agg(
            mean_wait=("queueWaitMs", "mean"),
            median_wait=("queueWaitMs", "median"),
        )
    )

    fig = go.Figure()

    for mode in queue_data["execution_mode"].unique():
        part = queue_data[
            queue_data["execution_mode"] == mode
        ]

        fig.add_trace(
            go.Bar(
                x=part["architecture"],
                y=part["mean_wait"],
                name=f"{mode} — среднее",
            )
        )

    fig.update_layout(
        title="Среднее время ожидания в очереди",
        xaxis_title="Архитектура",
        yaxis_title="Queue Wait, мс",
        barmode="group",
        height=400,
    )

    st.plotly_chart(fig, use_container_width=True)

    st.dataframe(
        queue_data,
        use_container_width=True,
        hide_index=True,
    )
else:
    st.info("Нет данных о Queue Wait.")


# ============================================================
# LCP-LIKE DISTRIBUTION / THROUGHPUT HISTOGRAM
# ============================================================

st.divider()
st.header("5. Распределение результатов")

if has_metric(filtered, "throughput"):
    fig = px.histogram(
        filtered.dropna(subset=["throughput"]),
        x="throughput",
        color="architecture",
        marginal="box",
        opacity=0.75,
        barmode="overlay",
        labels={
            "throughput": "Throughput",
            "architecture": "Архитектура",
        },
        title="Распределение значений Throughput",
    )

    fig.update_layout(height=450)
    st.plotly_chart(fig, use_container_width=True)


# ============================================================
# STATISTICAL SUMMARY
# ============================================================

st.divider()
st.header("6. Статистический анализ")

st.markdown(
    '<p class="section-description">'
    'Сводные статистики по каждой архитектуре и сценарию. '
    'Стандартное отклонение рассчитано по выборке.</p>',
    unsafe_allow_html=True,
)

stat_metrics = [
    col for col in [
        "throughput",
        "durationMs",
        "cpuUtilization",
        "communicationRatio",
        "queueWaitMs",
    ]
    if has_metric(filtered, col)
]

if stat_metrics:
    grouping = [
        col for col in [
            "architecture",
            "execution_mode",
            "benchmark",
        ]
        if col in filtered.columns
    ]

    summary = (
        filtered
        .groupby(grouping, dropna=False)[stat_metrics]
        .agg(["count", "mean", "median", "std", "min", "max"])
        .round(4)
    )

    st.dataframe(
        summary,
        use_container_width=True,
    )

    # Отдельная компактная таблица повторяемости.
    repeatability = (
        filtered
        .groupby(grouping, dropna=False)
        .size()
        .reset_index(name="measurements")
    )

    st.subheader("Количество повторений")

    st.dataframe(
        repeatability,
        use_container_width=True,
        hide_index=True,
    )

else:
    st.info("Нет числовых метрик для статистического анализа.")


# ============================================================
# RAW DATA
# ============================================================

st.divider()
st.header("7. Исходные данные")

st.caption(
    "Все строки, прошедшие фильтрацию. "
    "Можно скачать результат для дальнейшего анализа."
)

st.dataframe(
    filtered,
    use_container_width=True,
    hide_index=True,
)

csv_data = filtered.to_csv(
    index=False,
).encode("utf-8-sig")

st.download_button(
    label="⬇️ Скачать отфильтрованные данные CSV",
    data=csv_data,
    file_name="benchmark-filtered.csv",
    mime="text/csv",
    use_container_width=True,
)


# ============================================================
# FOOTER
# ============================================================

st.divider()

st.caption(
    f"MPI & Virtual Threads Research Dashboard | "
    f"Файлов: {len(files)} | "
    f"Измерений после фильтрации: {len(filtered)}"
)