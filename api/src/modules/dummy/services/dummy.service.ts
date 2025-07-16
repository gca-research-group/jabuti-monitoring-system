import { Injectable } from '@nestjs/common';

import { IBlockchainConnectionService } from '@app/models/interfaces';

@Injectable()
export class DummyConnectionService implements IBlockchainConnectionService {
  connect(_parameters: unknown): Promise<unknown> {
    return Promise.resolve({});
  }
  invoke(
    _connection: unknown,
    _smartContractName: string,
    _clauseName: string,
    _args?: { name: string; value: string }[],
  ): Promise<unknown> {
    const _time = Math.floor(Math.random() * (300 - 100 + 1)) + 100;
    return new Promise((resolve) => setTimeout(resolve, _time));
  }
}
