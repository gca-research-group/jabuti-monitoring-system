package br.edu.unijui.gca.api.exceptions;

public class BlockchainNotFoundException extends ApplicationException {
    public BlockchainNotFoundException() {
        super("BLOCKCHAIN_NOT_FOUND");
    }
}