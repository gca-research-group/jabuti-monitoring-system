import { HydratedDocument, Schema as MongooseSchema } from 'mongoose';

import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';

@Schema({ timestamps: true, _id: false })
class Argument {
  @Prop()
  id: string;

  @Prop()
  name: string;

  @Prop()
  value: string;
}

const ArgumentSchema = SchemaFactory.createForClass(Argument);
type ArgumentDocument = HydratedDocument<Argument>;

@Schema({ timestamps: true, _id: false })
class Clause {
  @Prop()
  id: string;

  @Prop()
  name: string;
}

const ClauseSchema = SchemaFactory.createForClass(Clause);
type ClauseDocument = HydratedDocument<Clause>;

@Schema({ timestamps: true, _id: false })
class SmartContract {
  @Prop()
  id: string;

  @Prop()
  name: string;
}

const SmartContractSchema = SchemaFactory.createForClass(SmartContract);
type SmartContractDocument = HydratedDocument<SmartContract>;

@Schema({ timestamps: true, _id: false })
class Blockchain {
  @Prop()
  id: string;

  @Prop()
  platform: string;
}

const BlockchainSchema = SchemaFactory.createForClass(Blockchain);
type BlockchainDocument = HydratedDocument<Blockchain>;

@Schema({ timestamps: true, _id: false })
class ExecutionPayload {
  @Prop({ type: BlockchainSchema })
  blockchain: BlockchainDocument;

  @Prop({ type: SmartContractSchema })
  smartContract: SmartContractDocument;

  @Prop({ type: ClauseSchema })
  clause: ClauseDocument;

  @Prop({ type: [ArgumentSchema], default: [] })
  clauseArguments: ArgumentDocument[];
}

const ExecutionPayloadSchema = SchemaFactory.createForClass(ExecutionPayload);
type ExecutionPayloadDocument = HydratedDocument<ExecutionPayload>;

@Schema({ timestamps: true })
export class SmartContractExecution {
  @Prop({ type: ExecutionPayloadSchema })
  payload: ExecutionPayloadDocument;

  @Prop({ type: MongooseSchema.Types.Mixed })
  result: unknown;

  @Prop({ type: MongooseSchema.Types.Mixed })
  metadata: unknown;

  @Prop({ default: 'PENDING' })
  status: string;
}

export const SmartContractExecutionSchema = SchemaFactory.createForClass(
  SmartContractExecution,
);
export type SmartContractExecutionDocument =
  HydratedDocument<SmartContractExecution>;
