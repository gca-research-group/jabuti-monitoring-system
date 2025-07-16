import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  Put,
  Query,
  UseGuards,
} from '@nestjs/common';

import {
  CreateBlockchainDto,
  CreateBlockchainSchema,
  ListBlockchainDto,
  UpdateBlockchainDto,
  UpdateBlockchainSchema,
} from '@app/dtos/blockchain';
import { AuthGuard } from '@app/guards';
import { BLOCKCHAIN_CONFIG } from '@app/models/schemas/blockchain';
import { ZodValidationPipe } from '@app/pipes/zod';

import { BlockchainService } from './services';

@UseGuards(AuthGuard)
@Controller('blockchain')
export class BlockchainController {
  constructor(private service: BlockchainService) {}

  @Get('config')
  config(@Query('platform') platform: keyof typeof BLOCKCHAIN_CONFIG) {
    return this.service.config(platform);
  }

  @Get('platforms')
  platforms() {
    return this.service.platforms();
  }

  @Get()
  index(@Query() query: ListBlockchainDto) {
    return this.service.findAll(query);
  }

  @Get(':id')
  show(@Param('id') id: string) {
    return this.service.findOne(id);
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  create(
    @Body(new ZodValidationPipe(CreateBlockchainSchema))
    createBlockchainDto: CreateBlockchainDto,
  ) {
    return this.service.create(createBlockchainDto);
  }

  @Put(':id')
  update(
    @Param('id') id: string,
    @Body(new ZodValidationPipe(UpdateBlockchainSchema))
    updateBlockchainDto: UpdateBlockchainDto,
  ) {
    return this.service.update(id, updateBlockchainDto);
  }

  @Delete(':id')
  remove(@Param('id') id: string) {
    return this.service.remove(id);
  }

  @Get(':id/test-connection')
  testConnection(@Param('id') id: string) {
    return this.service.testConnection(id);
  }
}
