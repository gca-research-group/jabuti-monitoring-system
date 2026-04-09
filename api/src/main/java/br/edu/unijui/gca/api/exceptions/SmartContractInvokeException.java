package br.edu.unijui.gca.api.exceptions;

public class SmartContractInvokeException extends ApplicationException {
    public SmartContractInvokeException(String message) {
        super(String.format("SMART_CONTRACT_INVOKE_ERROR: %s", message));
    }
}
