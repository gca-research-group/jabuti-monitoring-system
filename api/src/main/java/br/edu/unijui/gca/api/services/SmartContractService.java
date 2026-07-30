package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractDto;
import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.enums.PostExecutionActionType;
import br.edu.unijui.gca.api.mappers.SmartContractMapper;
import br.edu.unijui.gca.api.repositories.SmartContractRepository;
import br.edu.unijui.gca.api.specifications.SmartContractSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class SmartContractService extends BaseService<
        SmartContract,
        UUID,
        SmartContractFilterDto,
        SmartContractDto> {

    private final SmartContractRepository repository;

    private final SmartContractSpecification specification;

    private final SmartContractMapper mapper;

    @Override
    protected SmartContractRepository repository() {
        return repository;
    }

    @Override
    protected SmartContractSpecification specification() {
        return specification;
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
