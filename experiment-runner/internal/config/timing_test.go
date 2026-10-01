package config

import (
	"encoding/json"
	"math"
	"testing"
	"time"
)

func TestTimingConfiguration(t *testing.T) {
	var p Parameters
	if err := json.Unmarshal([]byte(`{"duration":120}`), &p); err != nil {
		t.Fatal(err)
	}
	if p.WarmupDuration != 0 || p.ValidateTiming() != nil {
		t.Fatal(p)
	}
	limit := int(math.MaxInt64 / int64(time.Second))
	for _, tt := range []struct {
		warmup, duration int
		valid            bool
	}{
		{30, 120, true}, {0, 1, true}, {-1, 120, false}, {0, 0, false}, {0, -1, false}, {1, limit, false}, {0, limit, true},
	} {
		p.WarmupDuration, p.Duration = tt.warmup, tt.duration
		if (p.ValidateTiming() == nil) != tt.valid {
			t.Fatalf("%+v: %v", tt, p.ValidateTiming())
		}
	}
}
