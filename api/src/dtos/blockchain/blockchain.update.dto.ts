import { z } from 'zod';

import { BlockchainPlatform } from '@app/models/enums';

export const UpdateBlockchainSchema = z.object({
  name: z.string({ message: 'NAME_IS_REQUIRED' }),
  platform: z.enum(
    [
      BlockchainPlatform.DUMMY,
      BlockchainPlatform.ETHEREUM,
      BlockchainPlatform.HYPERLEDGER_FABRIC,
    ],
    {
      message: 'INVALID_PLATFORM',
    },
  ),
  parameters: z.record(z.any()).optional(),
  status: z.boolean().optional(),
  remarks: z.string().optional(),
});

export type UpdateBlockchainDto = z.infer<typeof UpdateBlockchainSchema>;
