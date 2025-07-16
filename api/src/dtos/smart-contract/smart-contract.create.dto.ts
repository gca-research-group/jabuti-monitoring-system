import { z } from 'zod';

import { BlockchainPlatform } from '@app/models/enums';

export const CreateSmartContractClauseArgumentSchema = z.object({
  name: z.string({ message: 'NAME_IS_REQUIRED' }),
  type: z.string({ message: 'TYPE_IS_REQUIRED' }),
});

export const CreateSmartContractClauseSchema = z.object({
  name: z.string({ message: 'NAME_IS_REQUIRED' }),
  clauseArguments: z.array(CreateSmartContractClauseArgumentSchema).optional(),
});

export const CreateSmartContractSchema = z.object({
  name: z.string({ message: 'NAME_IS_REQUIRED' }),
  blockchainPlatform: z.enum(
    [
      BlockchainPlatform.DUMMY,
      BlockchainPlatform.ETHEREUM,
      BlockchainPlatform.HYPERLEDGER_FABRIC,
    ],
    {
      message: 'INVALID_BLOCKCHAIN_PLATFORM',
    },
  ),
  content: z.string().optional(),
  clauses: z.array(CreateSmartContractClauseSchema).optional(),
});

export type CreateSmartContractDto = z.infer<typeof CreateSmartContractSchema>;
