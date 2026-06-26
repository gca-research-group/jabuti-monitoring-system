import { Observable } from 'rxjs';

import { ResourceRef, Signal } from '@angular/core';

import { FindAllResponse } from './response.model';

export interface CrudService<T> {
  findAll(params?: object): Observable<FindAllResponse<T>>;
  findById(id: number | string): Observable<T>;
  delete(id: number | string): Observable<void>;
  save(obj: T): Observable<T>;

  findByIdResource(
    id: Signal<number | string | undefined>,
  ): ResourceRef<T | undefined>;
}
