import os

import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq
from dotenv import load_dotenv
from sqlalchemy import create_engine
from pathlib import Path

load_dotenv()

engine = create_engine(
    (
        f"postgresql+psycopg://"
        f"{os.environ['DB_USER']}:{os.environ['DB_PASSWORD']}"
        f"@{os.environ['DB_HOST']}:{os.environ['DB_PORT']}"
        f"/{os.environ['DB_NAME']}"
    ),
    pool_pre_ping=True,
)

METDATA = "data/202607061606/metadata.csv"
OUTPUT_FILE = "data/202607061606/data/{}/{}.parquet"
CHUNK_SIZE = 100_000

schema = pa.schema([
    ("event_id", pa.uuid()),
    ("scenario_id", pa.uuid()),
    ("status", pa.large_string()),
    ("consumers", pa.int64()),
    ("duration", pa.int64()),
    ("events", pa.int64()),
    ("integration_processes", pa.int64()),
    ("repetition", pa.int64()),
    ("inbound_queue_published", pa.timestamp("us", tz="UTC")),
    ("inbound_queue_consumed", pa.timestamp("us", tz="UTC")),
    ("inbound_queue_processing", pa.timestamp("us", tz="UTC")),
    ("inbound_queue_processed", pa.timestamp("us", tz="UTC")),
    ("execution_queue_published", pa.timestamp("us", tz="UTC")),
    ("execution_queue_consumed", pa.timestamp("us", tz="UTC")),
    ("execution_queue_processing", pa.timestamp("us", tz="UTC")),
    ("execution_queue_processed", pa.timestamp("us", tz="UTC")),
    ("outbound_queue_published", pa.timestamp("us", tz="UTC")),
    ("outbound_queue_consumed", pa.timestamp("us", tz="UTC")),
    ("outbound_queue_processing", pa.timestamp("us", tz="UTC")),
    ("outbound_queue_processed", pa.timestamp("us", tz="UTC")),
])

def export_to_parquet(scenario_id: str, repetition: int):
    query = f"""
        SELECT
            id as event_id,
            status,

            -- Metadata
            (metadata->>'ScenarioId')::uuid AS scenario_id,
            (metadata->>'Consumers')::int AS consumers,
            (metadata->>'Duration')::int AS duration,
            (metadata->>'Events')::int AS events,
            (metadata->>'IntegrationProcesses')::int AS integration_processes,
            (metadata->>'Repetition')::int AS repetition,

            -- Timestamps
            (timestamps->>'INBOUND_QUEUE_PUBLISHED')::timestamptz AS inbound_queue_published,
            (timestamps->>'INBOUND_QUEUE_CONSUMED')::timestamptz AS inbound_queue_consumed,
            (timestamps->>'INBOUND_QUEUE_PROCESSING')::timestamptz AS inbound_queue_processing,
            (timestamps->>'INBOUND_QUEUE_PROCESSED')::timestamptz AS inbound_queue_processed,

            (timestamps->>'EXECUTION_QUEUE_PUBLISHED')::timestamptz AS execution_queue_published,
            (timestamps->>'EXECUTION_QUEUE_CONSUMED')::timestamptz AS execution_queue_consumed,
            (timestamps->>'EXECUTION_QUEUE_PROCESSING')::timestamptz AS execution_queue_processing,
            (timestamps->>'EXECUTION_QUEUE_PROCESSED')::timestamptz AS execution_queue_processed,

            (timestamps->>'OUTBOUND_QUEUE_PUBLISHED')::timestamptz AS outbound_queue_published,
            (timestamps->>'OUTBOUND_QUEUE_CONSUMED')::timestamptz AS outbound_queue_consumed,
            (timestamps->>'OUTBOUND_QUEUE_PROCESSING')::timestamptz AS outbound_queue_processing,
            (timestamps->>'OUTBOUND_QUEUE_PROCESSED')::timestamptz AS outbound_queue_processed
        FROM
            smart_contract_executions
        WHERE
            (metadata->>'ScenarioId')::uuid = '{scenario_id}' and (metadata->>'Repetition')::int = {repetition}
        ORDER BY created_at ASC;
    """

    writer = None
    total_rows = 0

    output = OUTPUT_FILE.format(scenario_id, repetition)
    folder = Path(output)
    folder.parent.mkdir(parents=True, exist_ok=True)

    try:
        with engine.connect().execution_options(stream_results=True) as conn:
            for chunk in pd.read_sql_query(
                query,
                conn,
                chunksize=CHUNK_SIZE,
            ):
                table = table = pa.Table.from_pandas(
                    chunk,
                    schema=schema,
                    preserve_index=False,
                )

                if writer is None:
                    writer = pq.ParquetWriter(
                        output,
                        table.schema,
                        compression="snappy",
                    )

                writer.write_table(table)
                total_rows += len(chunk)

                print(f"Exported {total_rows:,} rows...")

    finally:
        if writer is not None:
            writer.close()

        engine.dispose()

    print(f"Finished. {total_rows:,} rows written to {OUTPUT_FILE}")


def scenarios():
    df = pd.read_csv(METDATA)
    for row in df.itertuples(index=False):
        export_to_parquet(row.ScenarioId, row.Repetition)

if __name__ == "__main__":
    # export_to_parquet()
    scenarios()