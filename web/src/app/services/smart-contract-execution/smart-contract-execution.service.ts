import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
  CrudService,
  FindAllResponse,
  SmartContractExecution,
} from '@app/models';

import { environment } from '../../../environments/environment';
import { BaseService } from '../base-service';

@Injectable({
  providedIn: 'root',
})
export class SmartContractExecutionService
   extends BaseService<SmartContractExecution> implements CrudService<SmartContractExecution>
{
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/smart-contract-execution`;

  findAll(params?: object) {
    return this.http.get<FindAllResponse<SmartContractExecution>>(this.url, {
      params: { ...params },
    });
  }

  findById(id: number) {
    return this.http.get<SmartContractExecution>(`${this.url}/${id}`);
  }

  delete(id: string) {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  deleteAll() {
    return this.http.delete<void>(`${this.url}`);
  }

  save(item: SmartContractExecution) {
    if (item.id) {
      return this.http.put<SmartContractExecution>(
        `${this.url}/${item.id}`,
        item,
      );
    }

    return this.http.post<SmartContractExecution>(this.url, item);
  }

  execute(item: {
    blockchainId: string;
    smartContractId: string;
    clauseName: string;
    arguments?: {
      name: string;
      value: string;
    }[];
  }) {
    return this.http.post<void>(`${this.url}/execute`, item);
  }
}
