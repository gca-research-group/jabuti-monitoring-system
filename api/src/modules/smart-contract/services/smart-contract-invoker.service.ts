import { Network } from '@hyperledger/fabric-gateway';

import { Injectable } from '@nestjs/common';

import { ContractInvokerDto } from '@app/dtos';
import { CreateSmartContractExecutionDto } from '@app/dtos/smart-contract-execution';
import { BlockchainConnectionFactory } from '@app/factories';

import { SmartContractOutboundQueueService } from './smart-contract-outbound-queue.service';

@Injectable()
export class SmartContractInvokerService {
  constructor(
    private smartContractOutboundQueueService: SmartContractOutboundQueueService<CreateSmartContractExecutionDto>,
  ) {}

  async invoke(data: { id: string; payload: ContractInvokerDto }) {
    const startExecution = new Date();
    const { blockchain, smartContract, clause, clauseArguments } = data.payload;

    const service = BlockchainConnectionFactory.getService(blockchain.platform);

    const connection = await BlockchainConnectionFactory.getConnection<
      Network,
      unknown
    >(blockchain.id, blockchain.platform, blockchain.parameters);

    try {
      const result = await service.invoke(
        connection,
        smartContract.name,
        clause.name,
        clauseArguments,
      );

      const endExecution = new Date();
      await this.smartContractOutboundQueueService.send({
        id: data.id,
        payload: data.payload,
        result,
        metadata: {
          execution: {
            start: startExecution,
            end: endExecution,
            duration: endExecution.getTime() - startExecution.getTime(),
          },
        },
        status: 'SUCCESS',
      });
    } catch (error) {
      const endExecution = new Date();
      await this.smartContractOutboundQueueService.send({
        id: data.id,
        payload: data.payload,
        metadata: {
          execution: {
            start: startExecution,
            end: endExecution,
            duration: endExecution.getTime() - startExecution.getTime(),
          },
        },
        result: (error as { message: string }).message,
        status: 'FAIL',
      });
    }
  }
}
