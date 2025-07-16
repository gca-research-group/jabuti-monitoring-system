import amqp, { ChannelWrapper } from 'amqp-connection-manager';
import { Channel, ConfirmChannel } from 'amqplib';

import {
  Injectable,
  InternalServerErrorException,
  Logger,
} from '@nestjs/common';

import { ContractInvokerDto } from '@app/dtos';

import { SmartContractInvokerService } from './smart-contract-invoker.service';
import { SmartContractOutboundQueueService } from './smart-contract-outbound-queue.service';

export const SMART_CONTRACT_EXECUTION_QUEUE = 'smart-contract-execution-queue';
export const SMART_CONTRACT_EXECUTION_DEAD_QUEUE =
  'smart-contract-execution-dead-queue';

@Injectable()
export class SmartContractExecutionQueueService {
  private readonly logger = new Logger(SmartContractExecutionQueueService.name);
  private channelWrapper: ChannelWrapper;

  constructor(
    private smartContractInvokerService: SmartContractInvokerService,
    private smartContractOutboundQueueService: SmartContractOutboundQueueService,
  ) {
    this.connect();
  }

  private connect() {
    const connection = amqp.connect([process.env.RABBITMQ_URI!]);
    this.channelWrapper = connection.createChannel({
      setup: async (_channel: Channel) => {},
    });
  }

  async onModuleInit() {
    try {
      await this.channelWrapper.addSetup(async (channel: ConfirmChannel) => {
        await channel.prefetch(500);

        await channel.assertQueue(SMART_CONTRACT_EXECUTION_DEAD_QUEUE, {
          durable: true,
        });

        await channel.assertQueue(SMART_CONTRACT_EXECUTION_QUEUE, {
          durable: true,
          arguments: {
            'x-dead-letter-exchange': '',
            'x-dead-letter-routing-key': SMART_CONTRACT_EXECUTION_DEAD_QUEUE,
          },
        });

        await channel.consume(SMART_CONTRACT_EXECUTION_QUEUE, (message) => {
          if (message) {
            const data = JSON.parse(message.content.toString()) as {
              id: string;
              payload: ContractInvokerDto;
            };

            this.smartContractInvokerService
              .invoke(data)
              .then(() => {
                try {
                  channel.ack(message);
                } catch (error) {
                  this.logger.error('Error ack message:', error);
                }
              })
              .catch(async (error) => {
                this.logger.error('Error processing message:', error);
                channel.nack(message, false, false);
                await this.smartContractOutboundQueueService.send({
                  id: data.id,
                  payload: data.payload,
                  result: error as unknown,
                  status: 'FAIL',
                });
              });
          }
        });
      });
    } catch (error) {
      this.logger.error('Error starting the consumer:', error);
    }
  }

  async send(data: { id: string; payload: ContractInvokerDto }) {
    try {
      await this.channelWrapper.sendToQueue(
        SMART_CONTRACT_EXECUTION_QUEUE,
        Buffer.from(JSON.stringify(data)),
        {
          persistent: true,
        },
      );
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException(error);
    }
  }
}
