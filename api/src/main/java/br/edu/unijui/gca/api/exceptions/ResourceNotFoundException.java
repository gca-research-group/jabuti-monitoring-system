package br.edu.unijui.gca.api.exceptions;

public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException() {
        super("RESOURCE_NOT_FOUND");
    }
}
