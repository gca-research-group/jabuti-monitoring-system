import { Observable } from 'rxjs';

import { Injectable, Signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

@Injectable({
  providedIn: 'root',
})
export abstract class BaseService<T> {
  abstract findById(id: number): Observable<T>;

  findByIdResource(id: Signal<number>) {
    return rxResource({
      params: () => {
        if (id()) {
          return id();
        }

        return undefined;
      },
      stream: request => this.findById(request?.params),
    });
  }
}
