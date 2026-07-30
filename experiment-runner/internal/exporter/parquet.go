package exporter

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
	_ "github.com/jackc/pgx/v5/stdlib"
	"github.com/parquet-go/parquet-go"
	"github.com/parquet-go/parquet-go/compress/zstd"
)

const executionQuery = `
SELECT
    id::text AS event_id,
    status::text AS status,
    metadata->>'ExecutionId' AS execution_id,
    metadata->>'ScenarioId' AS scenario_id,
    (metadata->>'Consumers')::int AS consumers,
    (metadata->>'Duration')::int AS duration,
    (metadata->>'Events')::int AS events,
    (metadata->>'IntegrationProcesses')::int AS integration_processes,
    (metadata->>'Repetition')::int AS repetition,
    (metadata->>'Lambda')::double precision AS lambda,
    (metadata->>'MaxStartDelay')::int AS max_start_delay,
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
    (timestamps->>'OUTBOUND_QUEUE_PROCESSED')::timestamptz AS outbound_queue_processed,
    created_at
FROM smart_contract_executions
WHERE metadata->>'ExecutionId' = $1
  AND metadata->>'ScenarioId' = $2
  AND (metadata->>'Repetition')::int = $3
ORDER BY created_at ASC`

type Event struct {
	EventID                  string     `parquet:"event_id"`
	Status                   string     `parquet:"status"`
	ExecutionID              string     `parquet:"execution_id"`
	ScenarioID               string     `parquet:"scenario_id"`
	Consumers                int32      `parquet:"consumers"`
	Duration                 int32      `parquet:"duration"`
	Events                   int32      `parquet:"events"`
	IntegrationProcesses     int32      `parquet:"integration_processes"`
	Repetition               int32      `parquet:"repetition"`
	Lambda                   float64    `parquet:"lambda"`
	MaxStartDelay            int32      `parquet:"max_start_delay"`
	InboundQueuePublished    *time.Time `parquet:"inbound_queue_published"`
	InboundQueueConsumed     *time.Time `parquet:"inbound_queue_consumed"`
	InboundQueueProcessing   *time.Time `parquet:"inbound_queue_processing"`
	InboundQueueProcessed    *time.Time `parquet:"inbound_queue_processed"`
	ExecutionQueuePublished  *time.Time `parquet:"execution_queue_published"`
	ExecutionQueueConsumed   *time.Time `parquet:"execution_queue_consumed"`
	ExecutionQueueProcessing *time.Time `parquet:"execution_queue_processing"`
	ExecutionQueueProcessed  *time.Time `parquet:"execution_queue_processed"`
	OutboundQueuePublished   *time.Time `parquet:"outbound_queue_published"`
	OutboundQueueConsumed    *time.Time `parquet:"outbound_queue_consumed"`
	OutboundQueueProcessing  *time.Time `parquet:"outbound_queue_processing"`
	OutboundQueueProcessed   *time.Time `parquet:"outbound_queue_processed"`
	CreatedAt                time.Time  `parquet:"created_at"`
}

type ParquetExporter struct {
	DB          *sql.DB
	DatabaseURL string
}

func (e *ParquetExporter) Validate(ctx context.Context) error {
	if e.DB == nil {
		if e.DatabaseURL == "" {
			return fmt.Errorf("DATABASE_URL is required")
		}
		db, err := sql.Open("pgx", e.DatabaseURL)
		if err != nil {
			return fmt.Errorf("configure experiment database: %w", err)
		}
		e.DB = db
	}
	if err := e.DB.PingContext(ctx); err != nil {
		return fmt.Errorf("connect to experiment database: %w", err)
	}
	return nil
}

func (e *ParquetExporter) Close() error {
	if e.DB == nil {
		return nil
	}
	return e.DB.Close()
}

func (e *ParquetExporter) Export(ctx context.Context, scenario runner.Scenario, destination string) (int64, error) {
	rows, err := e.DB.QueryContext(ctx, executionQuery, scenario.ExecutionID, scenario.ScenarioID, scenario.Repetition)
	if err != nil {
		return 0, fmt.Errorf("query experiment results: %w", err)
	}
	defer rows.Close()

	events := make([]Event, 0, scenario.Events*scenario.Duration*scenario.IntegrationProcesses)
	for rows.Next() {
		event, scanErr := scanEvent(rows)
		if scanErr != nil {
			return int64(len(events)), fmt.Errorf("scan experiment result: %w", scanErr)
		}
		events = append(events, event)
	}
	if err := rows.Err(); err != nil {
		return int64(len(events)), fmt.Errorf("read experiment results: %w", err)
	}
	if err := writeAtomic(destination, events); err != nil {
		return int64(len(events)), err
	}
	return int64(len(events)), nil
}

func scanEvent(rows *sql.Rows) (Event, error) {
	var event Event
	var timestamps [12]sql.NullTime
	err := rows.Scan(
		&event.EventID, &event.Status, &event.ExecutionID, &event.ScenarioID,
		&event.Consumers, &event.Duration, &event.Events, &event.IntegrationProcesses,
		&event.Repetition, &event.Lambda, &event.MaxStartDelay,
		&timestamps[0], &timestamps[1], &timestamps[2], &timestamps[3],
		&timestamps[4], &timestamps[5], &timestamps[6], &timestamps[7],
		&timestamps[8], &timestamps[9], &timestamps[10], &timestamps[11],
		&event.CreatedAt,
	)
	if err != nil {
		return Event{}, err
	}
	event.InboundQueuePublished = utcTime(timestamps[0])
	event.InboundQueueConsumed = utcTime(timestamps[1])
	event.InboundQueueProcessing = utcTime(timestamps[2])
	event.InboundQueueProcessed = utcTime(timestamps[3])
	event.ExecutionQueuePublished = utcTime(timestamps[4])
	event.ExecutionQueueConsumed = utcTime(timestamps[5])
	event.ExecutionQueueProcessing = utcTime(timestamps[6])
	event.ExecutionQueueProcessed = utcTime(timestamps[7])
	event.OutboundQueuePublished = utcTime(timestamps[8])
	event.OutboundQueueConsumed = utcTime(timestamps[9])
	event.OutboundQueueProcessing = utcTime(timestamps[10])
	event.OutboundQueueProcessed = utcTime(timestamps[11])
	event.CreatedAt = event.CreatedAt.UTC()
	return event, nil
}

func utcTime(value sql.NullTime) *time.Time {
	if !value.Valid {
		return nil
	}
	utc := value.Time.UTC()
	return &utc
}

func writeAtomic(destination string, events []Event) (err error) {
	if err := os.MkdirAll(filepath.Dir(destination), 0o755); err != nil {
		return fmt.Errorf("create parquet output directory: %w", err)
	}
	temp, err := os.CreateTemp(filepath.Dir(destination), ".events-*.parquet.tmp")
	if err != nil {
		return fmt.Errorf("create temporary parquet file: %w", err)
	}
	tempPath := temp.Name()
	defer func() {
		_ = temp.Close()
		if err != nil {
			_ = os.Remove(tempPath)
		}
	}()

	writer := parquet.NewGenericWriter[Event](temp, parquet.Compression(&zstd.Codec{}))
	if _, err = writer.Write(events); err != nil {
		return fmt.Errorf("write parquet rows: %w", err)
	}
	if err = writer.Close(); err != nil {
		return fmt.Errorf("close parquet writer: %w", err)
	}
	if err = temp.Sync(); err != nil {
		return fmt.Errorf("sync parquet file: %w", err)
	}
	if err = temp.Close(); err != nil {
		return fmt.Errorf("close parquet file: %w", err)
	}

	file, openErr := os.Open(tempPath)
	if openErr != nil {
		return fmt.Errorf("open parquet file for validation: %w", openErr)
	}
	info, statErr := file.Stat()
	if statErr == nil {
		_, err = parquet.OpenFile(file, info.Size())
	}
	_ = file.Close()
	if statErr != nil {
		return fmt.Errorf("stat parquet file: %w", statErr)
	}
	if err != nil {
		return fmt.Errorf("validate parquet file: %w", err)
	}
	if err = os.Rename(tempPath, destination); err != nil {
		return fmt.Errorf("publish parquet file: %w", err)
	}
	return nil
}
