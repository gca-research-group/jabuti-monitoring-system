import { Test, TestingModule } from '@nestjs/testing';

import { DummyConnectionService } from './dummy.service';

describe('DummyConnectionService', () => {
  let service: DummyConnectionService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [DummyConnectionService],
    }).compile();

    service = module.get(DummyConnectionService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
