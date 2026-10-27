package br.edu.unijui.gca.api.pipeline.execution.application;

public class ExecutionRejectedException extends RuntimeException {
    public ExecutionRejectedException(Exception cause) {
        super(cause);
    }
}
