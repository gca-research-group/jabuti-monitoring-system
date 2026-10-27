package br.edu.unijui.gca.api.metadata.application;

import br.edu.unijui.gca.api.shared.application.BaseService;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;

import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractDto;
import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.enums.PostExecutionActionType;
import br.edu.unijui.gca.api.mappers.SmartContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Profile("metadata-management")
@Service
public class SmartContractService extends BaseService<
        SmartContract,
        UUID,
        SmartContractFilterDto,
        SmartContractDto> {

    private final SmartContractStore store;

    private final SmartContractMapper mapper;

    @Override
    protected SmartContractStore store() {
        return store;
    }

    @Override
    protected SmartContractMapper mapper() {
        return mapper;
    }

    public SmartContract create(SmartContractDto dto) {
        validatePostActions(dto.getClauses());
        return super.create(dto);
    }

    public SmartContract update(SmartContractDto dto) {
        validatePostActions(dto.getClauses());
        return super.create(dto);
    }

    private void validatePostActions(List<SmartContractClauseDto> clauses) {
        clauses.forEach(clause -> {
            if (Objects.isNull(clause.getPostExecutionActions())){
                return;
            }

            clause.getPostExecutionActions().forEach(action -> {
                if (action.getType() == PostExecutionActionType.WEBHOOK && Objects.isNull(action.getUrl())) {
                    throw new RuntimeException("");
                }

                if (action.getType() == PostExecutionActionType.EVENT && Objects.isNull(action.getSmartContractId())) {
                    throw new RuntimeException("");
                }
            });
        });
    }
}
