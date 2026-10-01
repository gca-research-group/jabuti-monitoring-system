package exporter

import (
	"context"
	"database/sql/driver"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
	"github.com/parquet-go/parquet-go"
)

func TestExportUsesBoundIdentifiersAndWritesTypedParquet(t *testing.T) {
	db, mock, err := sqlmock.New(sqlmock.QueryMatcherOption(sqlmock.QueryMatcherRegexp))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	scenario := runner.Scenario{ExecutionID: "execution", ScenarioID: "scenario", Repetition: 3, WarmupDuration: 30000, Window: runner.NewRunWindow(time.Now(), 30000, 300000)}
	created := time.Date(2026, 7, 30, 10, 0, 0, 0, time.FixedZone("test", -3*60*60))
	columns := []string{
		"event_id", "status", "execution_id", "scenario_id", "consumers", "duration", "events",
		"integration_processes", "repetition", "lambda", "max_start_delay",
		"inbound_queue_published", "inbound_queue_consumed", "inbound_queue_processing", "inbound_queue_processed",
		"execution_queue_published", "execution_queue_consumed", "execution_queue_processing", "execution_queue_processed",
		"outbound_queue_published", "outbound_queue_consumed", "outbound_queue_processing", "outbound_queue_processed",
		"created_at",
	}
	values := []driver.Value{
		"event", "PROCESSED", "execution", "scenario", 2, 300, 10, 4, 3, 0.5, 0,
		created, nil, nil, nil, nil, nil, nil, nil, nil, nil, nil, nil, created,
	}
	mock.ExpectQuery(regexp.QuoteMeta(executionQuery)).
		WithArgs("execution", "scenario", 3).
		WillReturnRows(sqlmock.NewRows(columns).AddRow(values...))

	destination := filepath.Join(t.TempDir(), "execution", "scenario", "0003.parquet")
	exporter := ParquetExporter{DB: db}
	err = exporter.Export(context.Background(), scenario, destination)
	if err != nil {
		t.Fatalf("Export() error = %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}

	file, err := os.Open(destination)
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	info, _ := file.Stat()
	parquetFile, err := parquet.OpenFile(file, info.Size())
	if err != nil {
		t.Fatal(err)
	}
	reader := parquet.NewGenericReader[Event](parquetFile)
	events := make([]Event, 1)
	if _, err := reader.Read(events); err != nil && err != io.EOF {
		t.Fatal(err)
	}
	if events[0].EventID != "event" || events[0].InboundQueueConsumed != nil {
		t.Fatalf("event = %#v", events[0])
	}
	if !events[0].WorkloadStartedAt.Equal(scenario.Window.WorkloadStartedAt) || !events[0].MeasurementStartedAt.Equal(scenario.Window.MeasurementStartedAt) || !events[0].MeasurementEndedAt.Equal(scenario.Window.MeasurementEndedAt) || events[0].WarmupDuration != 30000 {
		t.Fatal("lost event boundaries")
	}
	for key, value := range scenario.Window.Metadata() {
		if got, ok := parquetFile.Lookup(key); !ok || got != value {
			t.Fatalf("metadata %s = %q", key, got)
		}
	}
	if events[0].CreatedAt.Location() != time.UTC || !events[0].CreatedAt.Equal(created) {
		t.Fatalf("created_at = %v, want UTC %v", events[0].CreatedAt, created.UTC())
	}
}

func TestWriteAtomicRemovesTemporaryFileOnPublishFailure(t *testing.T) {
	parent := t.TempDir()
	destination := filepath.Join(parent, "events.parquet")
	if err := os.Mkdir(destination, 0o755); err != nil {
		t.Fatal(err)
	}

	err := writeAtomic(destination, []Event{{EventID: "event", CreatedAt: time.Now()}}, runner.NewRunWindow(time.Now(), 0, 1000))
	if err == nil {
		t.Fatal("writeAtomic() error = nil")
	}
	matches, globErr := filepath.Glob(filepath.Join(parent, ".events-*.parquet.tmp"))
	if globErr != nil {
		t.Fatal(globErr)
	}
	if len(matches) != 0 {
		t.Fatalf("temporary files remain: %v", matches)
	}
}

func TestValidateRejectsMissingDatabase(t *testing.T) {
	err := (&ParquetExporter{}).Validate(context.Background())
	if err == nil {
		t.Fatal("Validate() error = nil")
	}
}

func TestEmptyEventFilePreservesWindow(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	window := runner.NewRunWindow(time.Date(2026, 10, 1, 10, 0, 0, 999999999, time.UTC), 30000, 120000)
	scenario := runner.Scenario{ExecutionID: "e", ScenarioID: "s", Repetition: 1, Window: window}
	mock.ExpectQuery(regexp.QuoteMeta(executionQuery)).WithArgs("e", "s", 1).WillReturnRows(sqlmock.NewRows([]string{"event_id"}))
	path := filepath.Join(t.TempDir(), "empty.parquet")
	if err := (&ParquetExporter{DB: db}).Export(context.Background(), scenario, path); err != nil {
		t.Fatal(err)
	}
	f, err := os.Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	info, _ := f.Stat()
	pf, err := parquet.OpenFile(f, info.Size())
	if err != nil {
		t.Fatal(err)
	}
	if pf.NumRows() != 0 {
		t.Fatal("expected empty file")
	}
	for key, value := range window.Metadata() {
		if got, ok := pf.Lookup(key); !ok || got != value {
			t.Fatalf("%s = %q", key, got)
		}
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}
