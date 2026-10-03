package exporter

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
	"time"

	"github.com/parquet-go/parquet-go"
)

func TestQueueSnapshotRoundTrip(t *testing.T) {
	destination := filepath.Join(t.TempDir(), "scenario", "0002.queues.parquet")
	rows := []QueueSnapshot{
		{ExecutionID: "execution", ScenarioID: "scenario", Repetition: 2, CapturedAt: time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC), VirtualHost: "/", QueueName: "inbound", MessagesReady: 3, MessagesUnacknowledged: 1, Messages: 4},
		{ExecutionID: "execution", ScenarioID: "scenario", Repetition: 2, CapturedAt: time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC), VirtualHost: "other", QueueName: "dead-letter"},
	}
	if err := WriteQueues(destination, rows); err != nil {
		t.Fatal(err)
	}
	got, err := parquet.ReadFile[QueueSnapshot](destination)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(got, rows) {
		t.Fatalf("%+v", got)
	}
	entries, err := os.ReadDir(filepath.Dir(destination))
	if err != nil || len(entries) != 1 {
		t.Fatalf("temporary artifacts: %v %v", entries, err)
	}
}

func TestQueueSnapshotFailurePreservesExistingFile(t *testing.T) {
	destination := filepath.Join(t.TempDir(), "existing")
	if err := os.Mkdir(destination, 0755); err != nil {
		t.Fatal(err)
	}
	marker := filepath.Join(destination, "marker")
	if err := os.WriteFile(marker, []byte("preserved"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := WriteQueues(destination, nil); err == nil {
		t.Fatal("published over directory")
	}
	if content, err := os.ReadFile(marker); err != nil || string(content) != "preserved" {
		t.Fatal("existing data lost")
	}
	entries, _ := os.ReadDir(filepath.Dir(destination))
	if len(entries) != 1 {
		t.Fatal("temporary file leaked")
	}
}
