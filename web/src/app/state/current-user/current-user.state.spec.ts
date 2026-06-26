import { Store } from '@ngxs/store';

import { TestBed } from '@angular/core/testing';

import { appConfig } from '@app/__tests__/app.config';
import { User } from '@app/models';

import { AddCurrentUserAction } from './current-user.actions';

describe('User store', () => {
  let store: Store;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers],
    });

    store = TestBed.inject(Store);
  });

  it('should create an action and add an item', () => {
    const expected: User = {
      name: 'Name',
      email: 'Email',
      photo: '',
      status: true,
      isAuthenticated: false,
      role: '',
    };
    store.dispatch(new AddCurrentUserAction(expected));
  });
});
