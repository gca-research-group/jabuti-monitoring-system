import { ComponentFixture, TestBed } from '@angular/core/testing';

import { appConfig } from '@app/__tests__/app.config';

import { StatusSelectorComponent } from './status-selector.component';

describe('StatusSelectorComponent', () => {
  let component: StatusSelectorComponent;
  let fixture: ComponentFixture<StatusSelectorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusSelectorComponent],
      providers: [...appConfig.providers],
    }).compileComponents();

    fixture = TestBed.createComponent(StatusSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
