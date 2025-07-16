import { isValidObjectId, Model, Types } from 'mongoose';

import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';

import {
  CreateBlockchainDto,
  ListBlockchainDto,
  UpdateBlockchainDto,
} from '@app/dtos/blockchain';
import { BlockchainConnectionFactory } from '@app/factories';
import { BlockchainPlatform } from '@app/models/enums';
import { CrudBase, HyperledgerFabricConfig } from '@app/models/interfaces';
import { Blockchain, BLOCKCHAIN_CONFIG } from '@app/models/schemas/blockchain';
import { HyperledgerFabricConnectionService } from '@app/modules/hyperledger-fabric/services';

@Injectable()
export class BlockchainService
  implements
    CrudBase<
      Blockchain,
      ListBlockchainDto,
      CreateBlockchainDto,
      UpdateBlockchainDto
    >
{
  constructor(
    @InjectModel(Blockchain.name)
    private model: Model<Blockchain>,
  ) {}

  async findAll(options: ListBlockchainDto) {
    const pageSize = +(options.pageSize ?? 20);
    const page = +(options.page ?? 1);

    const offset = (page - 1) * pageSize;

    const query: Record<string, unknown> = {};

    if (options._id) {
      if (!isValidObjectId(options._id)) {
        throw new BadRequestException('INVALID_ID_FORMAT');
      }

      query['_id'] = Types.ObjectId.createFromHexString(options._id);
    }

    if (options.name) {
      query['name'] = { $regex: new RegExp(options.name, 'i') };
    }

    const [data, total] = await Promise.all([
      this.model.find(query).skip(offset).limit(pageSize).exec(),
      this.model.countDocuments(query).exec(),
    ]);

    const totalPages = Math.ceil(total / pageSize);

    return {
      pages: totalPages,
      page,
      hasMore: page < totalPages,
      total,
      data,
    };
  }

  async findOne(id: string) {
    const item = await this.model.findById(id).exec();

    if (!item) {
      throw new BadRequestException('ITEM_NOT_FOUND');
    }

    return item;
  }

  async create(data: CreateBlockchainDto) {
    const model = new this.model(data);
    return await model.save();
  }

  async update(id: string, data: UpdateBlockchainDto) {
    const item = await this.model
      .findByIdAndUpdate(id, data, {
        new: true,
      })
      .exec();

    if (!item) {
      throw new BadRequestException('ITEM_NOT_FOUND');
    }

    return item;
  }

  async remove(id: string) {
    await this.model.deleteOne({ _id: id });
  }

  config(platform: keyof typeof BLOCKCHAIN_CONFIG) {
    return BLOCKCHAIN_CONFIG[platform];
  }

  platforms() {
    return [
      {
        id: BlockchainPlatform.DUMMY,
        name: BlockchainPlatform.DUMMY,
        image: `${process.env.BACKEND_URL}/static/images/blockchain/dummy.png`,
      },
      {
        id: BlockchainPlatform.ETHEREUM,
        name: BlockchainPlatform.ETHEREUM,
        image: `${process.env.BACKEND_URL}/static/images/blockchain/ethereum.png`,
      },
      {
        id: BlockchainPlatform.HYPERLEDGER_FABRIC,
        name: BlockchainPlatform.HYPERLEDGER_FABRIC,
        image: `${process.env.BACKEND_URL}/static/images/blockchain/hyperledger.png`,
      },
    ];
  }

  async testConnection(id: string) {
    const blockchain = await this.findOne(id);
    const service =
      BlockchainConnectionFactory.getService<HyperledgerFabricConnectionService>(
        blockchain.platform,
      );
    return service.connect(blockchain.parameters as HyperledgerFabricConfig);
  }
}
