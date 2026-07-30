package api

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestStopProcessingRejectsNonSuccessResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Header.Get("X-API-Key") != "token" {
			t.Errorf("X-API-Key = %q", request.Header.Get("X-API-Key"))
		}
		http.Error(writer, "could not stop", http.StatusInternalServerError)
	}))
	defer server.Close()

	err := NewClient(server.URL).StopProcessing("token")
	if err == nil || !strings.Contains(err.Error(), "status 500") {
		t.Fatalf("StopProcessing() error = %v", err)
	}
}
