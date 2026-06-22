import { TestBed } from '@angular/core/testing';

import { appConfig } from '@app/__tests__/app.config';

import { PreferencesService } from './preferences.service';

describe('PreferencesService', () => {
  let service: PreferencesService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers],
    });
    service = TestBed.inject(PreferencesService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
