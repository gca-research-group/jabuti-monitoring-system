package br.edu.unijui.gca.api.pipeline.application.ports;

import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;

import java.util.UUID;

public interface ExecutionMetadata {
    Blockchain findBlockchain(UUID id);
    SmartContract findSmartContract(UUID id);
}
