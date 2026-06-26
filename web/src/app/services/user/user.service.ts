import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { FindAllResponse, User } from '@app/models';

import { environment } from '../../../environments/environment';
import { BaseService } from '../base-service';

@Injectable({
  providedIn: 'root',
})
export class UserService extends BaseService<User> {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/user`;

  findAll(params?: object) {
    return this.http.get<FindAllResponse<User>>(this.url, {
      params: { ...params },
    });
  }

  findById(id: number) {
    return this.http.get<User>(`${this.url}/${id}`);
  }

  delete(id: number) {
    return this.http.delete(`${this.url}/${id}`);
  }

  save(item: User) {
    if (item.id) {
      return this.http.put<User>(`${this.url}/${item.id}`, item);
    }

    return this.http.post<User>(this.url, item);
  }
}
