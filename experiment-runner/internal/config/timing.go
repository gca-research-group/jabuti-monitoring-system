package config

import (
	"fmt"
	"math"
	"time"
)

func (p Parameters) ValidateTiming() error {
	limit := int64(math.MaxInt64) / int64(time.Second)
	if p.WarmupDuration < 0 || p.Duration <= 0 {
		return fmt.Errorf("warmupDuration must be nonnegative and duration must be positive")
	}
	if int64(p.Duration) > limit || int64(p.WarmupDuration) > limit-int64(p.Duration) {
		return fmt.Errorf("combined workload duration exceeds Go time duration range")
	}
	return nil
}
