import { ComponentFixture, TestBed } from '@angular/core/testing';

import { appConfig } from '@app/__tests__/app.config';

import { IconButtonDeleteComponent } from './icon-button-delete.component';

describe('IconButtonDeleteComponent', () => {
  let component: IconButtonDeleteComponent;
  let fixture: ComponentFixture<IconButtonDeleteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IconButtonDeleteComponent],
      providers: [...appConfig.providers],
    }).compileComponents();

    fixture = TestBed.createComponent(IconButtonDeleteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
