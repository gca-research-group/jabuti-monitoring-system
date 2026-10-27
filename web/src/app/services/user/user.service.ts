import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
  CreateUserRequest,
  FindAllResponse,
  UpdateUserRequest,
  UserResponse,
} from '@app/models';

import { environment } from '../../../environments/environment';
import { BaseService } from '../base-service';

@Injectable({
  providedIn: 'root',
})
export class UserService extends BaseService<UserResponse> {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/user`;

  findAll(params?: object) {
    return this.http.get<FindAllResponse<UserResponse>>(this.url, {
      params: { ...params },
    });
  }

  findById(id: number) {
    return this.http.get<UserResponse>(`${this.url}/${id}`);
  }

  delete(id: number) {
    return this.http.delete(`${this.url}/${id}`);
  }

  create(request: CreateUserRequest) {
    return this.http.post<UserResponse>(this.url, request);
  }

  update(id: number, request: UpdateUserRequest) {
    return this.http.put<UserResponse>(`${this.url}/${id}`, request);
  }

  // Retain the shared CRUD caller while keeping response-only fields off the wire.
  save(item: (CreateUserRequest | UpdateUserRequest) & { id?: number }) {
    const { name, email, password, photo, status, remarks } = item;
    const request = Object.fromEntries(
      Object.entries({ name, email, password, photo, status, remarks }).filter(
        ([, value]) => value !== undefined,
      ),
    );
    if (item.id) {
      return this.update(item.id, request);
    }
    return this.create(request as unknown as CreateUserRequest);
  }
}
