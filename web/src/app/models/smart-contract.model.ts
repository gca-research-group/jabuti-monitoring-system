export interface SmartContractClauseArgument {
  name: string;
  type: string;
}

export interface SmartContractClause {
  name: string;
  clauseArguments?: SmartContractClauseArgument[];
}

export interface SmartContract {
  id: string;
  name: string;
  clauses?: SmartContractClause[];
  createdAt: Date;
  updatedAt: Date;
}
