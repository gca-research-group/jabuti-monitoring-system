import { Module } from '@nestjs/common';

import { DummyConnectionService } from './services/dummy.service';

@Module({
  providers: [DummyConnectionService],
  exports: [DummyConnectionService],
})
export class HyperledgerFabricModule {}
