import amqp, { ChannelWrapper } from 'amqp-connection-manager';
import { Channel, ConfirmChannel } from 'amqplib';

import {
  Injectable,
  InternalServerErrorException,
  Logger,
} from '@nestjs/common';

import { UpdateSmartContractExecutionDto } from '@app/dtos/smart-contract-execution';
import { jsonDateReviver } from '@app/utils/json-date-reviver';

import { SmartContractExecutionService } from './smart-contract-execution.service';

export const SMART_CONTRACT_OUTBOUND_QUEUE = 'smart-contract-outbound-queue';
export const SMART_CONTRACT_OUTBOUND_DEAD_QUEUE =
  'smart-contract-outbound-dead-queue';

@Injectable()
export class SmartContractOutboundQueueService<T = unknown> {
  private readonly logger = new Logger(SmartContractOutboundQueueService.name);
  private channelWrapper: ChannelWrapper;

  constructor(
    private smartContractExecutionService: SmartContractExecutionService,
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

        await channel.assertQueue(SMART_CONTRACT_OUTBOUND_DEAD_QUEUE, {
          durable: true,
        });

        await channel.assertQueue(SMART_CONTRACT_OUTBOUND_QUEUE, {
          durable: true,
          arguments: {
            'x-dead-letter-exchange': '',
            'x-dead-letter-routing-key': SMART_CONTRACT_OUTBOUND_DEAD_QUEUE,
          },
        });

        await channel.consume(SMART_CONTRACT_OUTBOUND_QUEUE, (message) => {
          if (message) {
            const data = JSON.parse(
              message.content.toString(),
              jsonDateReviver,
            ) as {
              id: string;
              payload: unknown;
              metadata?: unknown;
              result: unknown;
              status: string;
            };

            this.smartContractExecutionService
              .update(data.id, {
                payload: data.payload,
                metadata: data.metadata,
                result: data.result,
                status: data.status,
              } as UpdateSmartContractExecutionDto)
              .then(() => {
                try {
                  channel.ack(message);
                } catch (error) {
                  this.logger.error('Error ack message:', error);
                }
              })
              .catch((err) => {
                this.logger.error('Error processing message:', err);
                channel.nack(message, false, false);
              });
          }
        });
      });
    } catch (error) {
      this.logger.error('Error starting the consumer:', error);
    }
  }

  async send(data: T) {
    try {
      await this.channelWrapper.sendToQueue(
        SMART_CONTRACT_OUTBOUND_QUEUE,
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
