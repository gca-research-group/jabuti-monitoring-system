package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.*;
import br.edu.unijui.gca.api.dtos.filter.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.mappers.SmartContractMapper;
import br.edu.unijui.gca.api.repositories.SmartContractRepository;
import br.edu.unijui.gca.api.specifications.SmartContractSpecification;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SmartContractService extends BaseService<
        SmartContract,
        UUID,
        SmartContractDto,
        SmartContractFilterDto,
        SmartContractRepository,
        SmartContractSpecification,
        SmartContractMapper> {

 }
