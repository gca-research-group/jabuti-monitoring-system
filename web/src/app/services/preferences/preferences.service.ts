import { Store } from '@ngxs/store';

import { inject, Injectable, Signal } from '@angular/core';

import { Preferences } from '@app/models';
import { FixMenuAction, preferencesSelector } from '@app/state/preferences';

@Injectable({
  providedIn: 'root',
})
export class PreferencesService {
  private store = inject(Store);

  preferences$: Signal<Preferences | undefined> =
    this.store.selectSignal(preferencesSelector);

  toggleFixedMenu() {
    const isMenuFixed = this.preferences$()?.isMenuFixed;
    this.store.dispatch(new FixMenuAction(!isMenuFixed));
  }
}
