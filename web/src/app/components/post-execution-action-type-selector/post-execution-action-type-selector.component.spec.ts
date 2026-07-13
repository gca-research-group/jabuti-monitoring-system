import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { By } from '@angular/platform-browser';

import { appConfig } from '@app/app.config';

import { PostExecutionActionSelectorComponent } from './post-execution-action-type-selector.component';

export function getNgSelectElement(
  fixture: ComponentFixture<unknown>,
): DebugElement {
  return fixture.debugElement.query(By.css('ng-select'));
}

export function triggerKeyDownEvent(
  element: DebugElement,
  which: number,
  key = '',
): void {
  element.triggerEventHandler('keydown', {
    which: which,
    key: key,
    preventDefault: () => {},
  });
}

describe('PostExecutionActionSelectorComponent', () => {
  let component: PostExecutionActionSelectorComponent;
  let fixture: ComponentFixture<PostExecutionActionSelectorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [appConfig.providers],
      imports: [PostExecutionActionSelectorComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(
      PostExecutionActionSelectorComponent,
    );
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should select the status', () => {
    component.items.forEach((value, index) => {
      triggerKeyDownEvent(getNgSelectElement(fixture), 32);
      fixture.detectChanges();
      fixture.debugElement
        .query(By.css(`.ng-option:nth-of-type(${index + 1}`))
        .triggerEventHandler('click', {});
      fixture.detectChanges();

      expect(component.formControl.value).toEqual(value.id);
    });
  });

  it('should set the status', () => {
    const selected = component.items[0];
    component.formControl = new FormControl(selected.id);

    fixture.detectChanges();

    const element = fixture.debugElement.query(By.css('.ng-value-label'));
    const content = (element.nativeElement as HTMLElement).textContent;
    expect(content).toContain(selected.name);
  });
});
