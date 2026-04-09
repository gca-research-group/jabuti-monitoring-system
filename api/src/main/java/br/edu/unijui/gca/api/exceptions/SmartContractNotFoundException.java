package br.edu.unijui.gca.api.exceptions;

public class SmartContractNotFoundException extends ApplicationException {
    public SmartContractNotFoundException() {
        super("SMART_CONTRACT_NOT_FOUND");
    }
}