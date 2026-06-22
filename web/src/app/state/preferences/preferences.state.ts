import { State, Action, StateContext } from '@ngxs/store';

import { Injectable } from '@angular/core';

import { Preferences } from '@app/models';

import { FixMenuAction } from './preferences.actions';

@State<Preferences>({
  name: 'preferences',
  defaults: {
    isMenuFixed: false,
  },
})
@Injectable()
export class PreferencesState {
  @Action(FixMenuAction)
  fixMenu(ctx: StateContext<Preferences>, { isMenuFixed }: FixMenuAction) {
    let state = ctx.getState();
    state = { ...state, isMenuFixed };
    ctx.setState(state);
  }
}
