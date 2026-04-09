package br.edu.unijui.gca.api.exceptions;

public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException() {
        super("ITEM_NOT_FOUND");
    }
}
