package monitoring

import (
	"strings"
	"testing"
)

func TestSelectDockerVersion(t *testing.T) {
	tests := []struct {
		name            string
		version         dockerVersion
		want, wantError string
	}{
		{"baseline", dockerVersion{"1.41", "", "linux"}, "/v1.41", ""},
		{"newer server", dockerVersion{"1.50", "1.24", "linux"}, "/v1.41", ""},
		{"raised minimum", dockerVersion{"1.50", "1.44", "linux"}, "/v1.44", ""},
		{"equal minimum", dockerVersion{"1.50", "1.50", "linux"}, "/v1.50", ""},
		{"old server", dockerVersion{"1.40", "", "linux"}, "", "Linux Docker API >=1.41 required"},
		{"malformed server", dockerVersion{"bad", "", "linux"}, "", "Linux Docker API >=1.41 required"},
		{"wrong major", dockerVersion{"2.50", "", "linux"}, "", "Linux Docker API >=1.41 required"},
		{"unsupported OS", dockerVersion{"1.50", "", "windows"}, "", "Linux Docker API >=1.41 required"},
		{"malformed minimum", dockerVersion{"1.50", "bad", "linux"}, "", "invalid Docker minimum API version"},
		{"minimum exceeds server", dockerVersion{"1.50", "1.51", "linux"}, "", "invalid Docker minimum API version"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := selectDockerVersion(tt.version)
			if got != tt.want {
				t.Fatalf("version = %q, want %q", got, tt.want)
			}
			if tt.wantError == "" {
				if err != nil {
					t.Fatal(err)
				}
			} else if err == nil || !strings.Contains(err.Error(), tt.wantError) {
				t.Fatalf("error = %v, want %q", err, tt.wantError)
			}
		})
	}
}
