export interface SmartContractClauseArgument {
  name: string;
  type: string;
}

export interface SmartContractPostExecutionAction {
  type: 'WEBHOOK' | 'EVENT';
  url: string;
}

export interface SmartContractClause {
  name: string;
  clauseArguments?: SmartContractClauseArgument[];
  postExecutionActions?: SmartContractPostExecutionAction[];
}

export interface SmartContract {
  id: string;
  name: string;
  clauses?: SmartContractClause[];
  createdAt: Date;
  updatedAt: Date;
}
