package br.edu.unijui.gca.api.execution.application;

public class ExecutionRejectedException extends RuntimeException {
    public ExecutionRejectedException(Exception cause) {
        super(cause);
    }
}
