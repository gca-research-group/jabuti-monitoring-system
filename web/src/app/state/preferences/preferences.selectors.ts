import { createSelector } from '@ngxs/store';

import { Preferences } from '@app/models';

import { PreferencesState } from './preferences.state';

export const preferencesSelector = createSelector(
  [PreferencesState],
  (state: Preferences) => {
    return state;
  },
);
