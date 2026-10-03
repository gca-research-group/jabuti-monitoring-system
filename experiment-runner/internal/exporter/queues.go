package exporter

import (
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/parquet-go/parquet-go"
	"github.com/parquet-go/parquet-go/compress/zstd"
)

type QueueSnapshot struct {
	ExecutionID            string    `parquet:"execution_id"`
	ScenarioID             string    `parquet:"scenario_id"`
	Repetition             int64     `parquet:"repetition"`
	CapturedAt             time.Time `parquet:"captured_at,timestamp(microsecond)"`
	VirtualHost            string    `parquet:"virtual_host"`
	QueueName              string    `parquet:"queue_name"`
	MessagesReady          int64     `parquet:"messages_ready"`
	MessagesUnacknowledged int64     `parquet:"messages_unacknowledged"`
	Messages               int64     `parquet:"messages"`
}

func WriteQueues(destination string, rows []QueueSnapshot) (err error) {
	if err = os.MkdirAll(filepath.Dir(destination), 0755); err != nil {
		return fmt.Errorf("create queue output directory: %w", err)
	}
	temp, err := os.CreateTemp(filepath.Dir(destination), ".queues-*.parquet.tmp")
	if err != nil {
		return fmt.Errorf("create queue snapshot: %w", err)
	}
	defer func() { _ = temp.Close(); _ = os.Remove(temp.Name()) }()
	writer := parquet.NewGenericWriter[QueueSnapshot](temp, parquet.Compression(&zstd.Codec{}))
	writer.SetKeyValueMetadata("schema_version", "1")
	writer.SetKeyValueMetadata("dataset_kind", "queues")
	if _, err = writer.Write(rows); err != nil {
		_ = writer.Close()
		return fmt.Errorf("write queue snapshot: %w", err)
	}
	if err = writer.Close(); err != nil {
		return err
	}
	if err = temp.Sync(); err != nil {
		return err
	}
	if err = temp.Close(); err != nil {
		return err
	}
	file, err := os.Open(temp.Name())
	if err != nil {
		return err
	}
	info, err := file.Stat()
	if err == nil {
		_, err = parquet.OpenFile(file, info.Size())
	}
	_ = file.Close()
	if err != nil {
		return fmt.Errorf("validate queue snapshot: %w", err)
	}
	if err = os.Rename(temp.Name(), destination); err != nil {
		return fmt.Errorf("publish queue snapshot: %w", err)
	}
	return nil
}
