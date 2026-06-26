import { createSelector } from '@ngxs/store';

import { User } from '@app/models';

import { CurrentUserState } from './current-user.state';

export const currentUserSelector = createSelector(
  [CurrentUserState],
  (state: User) => {
    return state;
  },
);
