package br.edu.unijui.gca.api.exceptions;

public class BlockchainConnectionException  extends ApplicationException {
    public BlockchainConnectionException() {
        super("BLOCKCHAIN_CONNECTION_ERROR");
    }
}
