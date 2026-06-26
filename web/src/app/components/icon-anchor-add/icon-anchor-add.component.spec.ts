import { ComponentFixture, TestBed } from '@angular/core/testing';

import { appConfig } from '@app/__tests__/app.config';

import { IconAnchorAddComponent } from './icon-anchor-add.component';

describe('IconAnchorAddComponent', () => {
  let component: IconAnchorAddComponent;
  let fixture: ComponentFixture<IconAnchorAddComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IconAnchorAddComponent],
      providers: [...appConfig.providers],
    }).compileComponents();

    fixture = TestBed.createComponent(IconAnchorAddComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
