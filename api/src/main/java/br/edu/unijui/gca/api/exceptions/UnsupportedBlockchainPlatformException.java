package br.edu.unijui.gca.api.exceptions;

public class UnsupportedBlockchainPlatformException extends ApplicationException {
    public UnsupportedBlockchainPlatformException() {
        super("UNSUPPORTED_BLOCKCHAIN_PLATFORM");
    }
}