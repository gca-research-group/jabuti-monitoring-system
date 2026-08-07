package logger

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

func TestSetupCopiesCrashOutputToLogFile(t *testing.T) {
	if os.Getenv("LOGGER_CRASH_HELPER") == "1" {
		if err := Setup(); err != nil {
			panic(err)
		}
		panic("test crash marker")
	}

	workingDirectory := t.TempDir()
	command := exec.Command(os.Args[0], "-test.run=TestSetupCopiesCrashOutputToLogFile")
	command.Dir = workingDirectory
	command.Env = append(os.Environ(), "LOGGER_CRASH_HELPER=1")
	if err := command.Run(); err == nil {
		t.Fatal("crash helper exited successfully")
	}

	contents, err := os.ReadFile(filepath.Join(workingDirectory, "logs", "app.log"))
	if err != nil {
		t.Fatalf("read crash log: %v", err)
	}
	if !strings.Contains(string(contents), "panic: test crash marker") {
		t.Fatalf("crash log does not contain panic output:\n%s", contents)
	}
}
