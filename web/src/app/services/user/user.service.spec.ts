import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the URL identity and sends only writable fields, preserving null', () => {
    const item = {
      id: 7,
      name: 'Changed',
      photo: null,
      accessToken: 'response-only',
      isAuthenticated: true,
    };
    service.save(item).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/user/7`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Changed', photo: null });
    request.flush({ id: 7, name: 'Changed', photo: null });
  });
});
