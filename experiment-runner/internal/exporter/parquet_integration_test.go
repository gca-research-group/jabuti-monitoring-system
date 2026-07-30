package exporter

import (
	"context"
	"database/sql"
	"os"
	"path/filepath"
	"testing"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
	_ "github.com/jackc/pgx/v5/stdlib"
)

func TestParquetExporterAgainstPostgreSQL(t *testing.T) {
	databaseURL := os.Getenv("TEST_DATABASE_URL")
	if databaseURL == "" {
		t.Skip("TEST_DATABASE_URL is not configured")
	}

	db, err := sql.Open("pgx", databaseURL)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	db.SetMaxOpenConns(1)

	ctx := context.Background()
	if _, err := db.ExecContext(ctx, `
CREATE TEMPORARY TABLE smart_contract_executions (
	id text NOT NULL,
	status text NOT NULL,
	metadata jsonb NOT NULL,
	timestamps jsonb NOT NULL,
	created_at timestamptz NOT NULL
)`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.ExecContext(ctx, `
INSERT INTO smart_contract_executions (id, status, metadata, timestamps, created_at)
VALUES (
	'event-1',
	'PROCESSED',
	'{"ExecutionId":"execution","ScenarioId":"scenario","Consumers":2,"Duration":1,"Events":1,"IntegrationProcesses":1,"Repetition":1,"Lambda":0.5,"MaxStartDelay":0}',
	'{"INBOUND_QUEUE_PUBLISHED":"2026-07-30T12:00:00-03:00"}',
	'2026-07-30T12:00:01-03:00'
)`); err != nil {
		t.Fatal(err)
	}

	destination := filepath.Join(t.TempDir(), "events.parquet")
	count, err := (&ParquetExporter{DB: db}).Export(ctx, runner.Scenario{
		ExecutionID: "execution",
		ScenarioID:  "scenario",
		Repetition:  1,
	}, destination)
	if err != nil {
		t.Fatal(err)
	}
	if count != 1 {
		t.Fatalf("row count = %d, want 1", count)
	}
	if info, err := os.Stat(destination); err != nil || info.Size() == 0 {
		t.Fatalf("parquet output is invalid: info=%v err=%v", info, err)
	}
}
