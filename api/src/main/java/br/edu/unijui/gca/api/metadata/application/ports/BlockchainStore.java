package br.edu.unijui.gca.api.metadata.application.ports;

import br.edu.unijui.gca.api.dtos.blockchain.BlockchainFilterDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.shared.application.ports.ResourceStore;
import java.util.UUID;

public interface BlockchainStore extends ResourceStore<Blockchain, UUID, BlockchainFilterDto> {
}
