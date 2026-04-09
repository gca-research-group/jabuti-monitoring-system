package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.SmartContractDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.springframework.stereotype.Component;

@Component
public class SmartContractMapper implements IMapper<SmartContract, SmartContractDto> {
    @Override
    public SmartContract toEntity(SmartContract smartContract, SmartContractDto dto) {
        smartContract.setName(dto.getName());
        smartContract.setClauses(dto.getClauses());
        smartContract.setBlockchainPlatform(dto.getBlockchainPlatform());
        smartContract.setStatus(dto.getStatus());

        return smartContract;
    }

    @Override
    public SmartContract toEntity(SmartContractDto dto) {
        return toEntity(new SmartContract(), dto);
    }

    @Override
    public SmartContractDto toDto(SmartContract smartContract) {
        return SmartContractDto.builder()
                .id(smartContract.getId())
                .name(smartContract.getName())
                .blockchainPlatform(smartContract.getBlockchainPlatform())
                .clauses(smartContract.getClauses())
                .build();
    }
}
