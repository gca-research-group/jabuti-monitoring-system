import { ComponentFixture, TestBed } from '@angular/core/testing';

import { appConfig } from '@app/__tests__/app.config';

import { IconButtonAddComponent } from './icon-button-add.component';

describe('IconButtonAddComponent', () => {
  let component: IconButtonAddComponent;
  let fixture: ComponentFixture<IconButtonAddComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IconButtonAddComponent],
      providers: [...appConfig.providers],
    }).compileComponents();

    fixture = TestBed.createComponent(IconButtonAddComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
