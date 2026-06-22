import { TestBed } from '@angular/core/testing';

import { appConfig } from '@app/__tests__/app.config';

import { SidebarService } from './sidebar.service';

describe('SidebarService', () => {
  let service: SidebarService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...appConfig.providers] });
    service = TestBed.inject(SidebarService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
