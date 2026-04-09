import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
  Blockchain,
  BlockchainPlatform,
  CrudService,
  FindAllResponse,
} from '@app/models';

import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class BlockchainService implements CrudService<Blockchain> {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/blockchain`;

  config(platform: string) {
    return this.http.get<
      { field: string; type: string; description: string }[]
    >(`${this.url}/config`, {
      params: { platform },
    });
  }

  platforms(params?: object) {
    return this.http.get<BlockchainPlatform[]>(`${this.url}/platforms`, {
      params: { ...params },
    });
  }

  findAll(params?: object) {
    return this.http.get<FindAllResponse<Blockchain>>(this.url, {
      params: { ...params },
    });
  }

  findById(id: string) {
    return this.http.get<Blockchain>(`${this.url}/${id}`);
  }

  delete(id: string) {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  save(item: Blockchain) {
    if (item.id) {
      return this.http.put<Blockchain>(`${this.url}/${item.id}`, item);
    }

    return this.http.post<Blockchain>(this.url, item);
  }

  testConnection(id: string) {
    return this.http.get<void>(`${this.url}/${id}/test-connection`);
  }
}
