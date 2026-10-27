package br.edu.unijui.gca.api.pipeline.preparation.application;

public class PreparationRejectedException extends RuntimeException {
    public PreparationRejectedException(Exception cause) {
        super(cause);
    }
}
