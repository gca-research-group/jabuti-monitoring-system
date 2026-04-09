export interface Blockchain {
  id: string;
  name: string;
  parameters: object;
  createdAt: Date;
  updatedAt: Date;
}

export interface BlockchainPlatform {
  id: string;
  name: string;
  image: string;
}
