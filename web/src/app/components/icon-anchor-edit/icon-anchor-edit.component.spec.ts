import { ComponentFixture, TestBed } from '@angular/core/testing';

import { appConfig } from '@app/__tests__/app.config';

import { IconAnchorEditComponent } from './icon-anchor-edit.component';

describe('IconAnchorEditComponent', () => {
  let component: IconAnchorEditComponent;
  let fixture: ComponentFixture<IconAnchorEditComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IconAnchorEditComponent],
      providers: [...appConfig.providers],
    }).compileComponents();

    fixture = TestBed.createComponent(IconAnchorEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
