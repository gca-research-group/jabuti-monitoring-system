package runner

import (
	"math/rand"
	"slices"
	"testing"
)

func TestGenerateExponentialEventsIsSortedAndDeterministic(t *testing.T) {
	first := GenerateExponentialEvents(20, 0.5, rand.New(rand.NewSource(42)))
	second := GenerateExponentialEvents(20, 0.5, rand.New(rand.NewSource(42)))

	if len(first) != 20 {
		t.Fatalf("event count = %d, want 20", len(first))
	}
	if !slices.IsSorted(first) {
		t.Fatal("events are not sorted")
	}
	if !slices.Equal(first, second) {
		t.Fatal("events generated with the same seed differ")
	}
}

func TestGenerateExponentialEventsReturnsNilForNonPositiveCount(t *testing.T) {
	if got := GenerateExponentialEvents(0, 0.5, rand.New(rand.NewSource(1))); got != nil {
		t.Fatalf("events = %v, want nil", got)
	}
}
