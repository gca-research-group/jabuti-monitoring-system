interface Blockchain {
  id: string;
  platform: string;
}

interface SmartContract {
  id: string;
  name: string;
}

interface Argument {
  name: string;
  value: string;
}

interface SmartContractExecutionPayload {
  blockchain: Blockchain;
  smartContract: SmartContract;
  clauseName: string;
  clauseArguments: Argument[];
}

export interface SmartContractExecution {
  id: string;
  payload: SmartContractExecutionPayload;
  result: unknown;
  createdAt: Date;
  updatedAt: Date;
}
