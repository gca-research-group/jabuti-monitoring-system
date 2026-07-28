package runner

import (
	"math"
	"math/rand"
	"sort"
	"time"
)

func GenerateExponentialEvents(n int, lambda float64, random *rand.Rand) []time.Duration {
	if n <= 0 {
		return nil
	}

	const (
		min = 0.0
		max = 1.0
	)

	events := make([]time.Duration, 0, n)
	a := math.Exp(-lambda * min)
	b := math.Exp(-lambda * max)

	for i := 0; i < n; i++ {
		u := random.Float64()
		x := -math.Log(a-u*(a-b)) / lambda
		events = append(events, time.Duration(x*float64(time.Second)))
	}

	sort.Slice(events, func(i, j int) bool {
		return events[i] < events[j]
	})

	return events
}
