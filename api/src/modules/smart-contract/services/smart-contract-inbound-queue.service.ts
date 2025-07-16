import amqp, { ChannelWrapper } from 'amqp-connection-manager';
import { Channel, ConfirmChannel } from 'amqplib';

import {
  Injectable,
  InternalServerErrorException,
  Logger,
} from '@nestjs/common';

import { EventHandlerDto } from '@app/dtos';

import { SmartContractEventHandlerService } from './smart-contract-event-handler.service';

export const SMART_CONTRACT_INBOUND_QUEUE = 'smart-contract-inbound-queue';
export const SMART_CONTRACT_INBOUND_DEAD_QUEUE =
  'smart-contract-inbound-dead-queue';

@Injectable()
export class SmartContractInboundQueueService<T = unknown> {
  private readonly logger = new Logger(SmartContractInboundQueueService.name);
  private channelWrapper: ChannelWrapper;

  constructor(
    private smartContractEventHandlerService: SmartContractEventHandlerService,
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

        await channel.assertQueue(SMART_CONTRACT_INBOUND_DEAD_QUEUE, {
          durable: true,
        });

        await channel.assertQueue(SMART_CONTRACT_INBOUND_QUEUE, {
          durable: true,
          arguments: {
            'x-dead-letter-exchange': '',
            'x-dead-letter-routing-key': SMART_CONTRACT_INBOUND_DEAD_QUEUE,
          },
        });

        await channel.consume(SMART_CONTRACT_INBOUND_QUEUE, (message) => {
          if (message) {
            const data = JSON.parse(
              message.content.toString(),
            ) as EventHandlerDto;

            this.smartContractEventHandlerService
              .handle(data)
              .then(() => {
                try {
                  channel.ack(message);
                } catch (error) {
                  this.logger.error('Error ack message:', error);
                }
              })
              .catch((err) => {
                channel.nack(message, false, false);
                this.logger.error('Error processing message:', err);
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
        SMART_CONTRACT_INBOUND_QUEUE,
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
