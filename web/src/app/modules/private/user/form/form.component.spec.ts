import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { environment } from '../../../../../environments/environment';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
  flush,
} from '@angular/core/testing';

import { appConfig } from '@app/__tests__/app.config';

import { UserFormComponent } from './form.component';

describe('UserFormComponent', () => {
  let component: UserFormComponent;
  let fixture: ComponentFixture<UserFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserFormComponent],
      providers: [...appConfig.providers, provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(UserFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('sends only dirty fields and preserves a null photo when editing', fakeAsync(() => {
    const http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', 7);
    fixture.detectChanges();
    tick();
    http.expectOne(`${environment.apiUrl}/user/7`).flush({
      id: 7,
      name: 'Researcher',
      email: 'r@example.test',
      photo: 'x'.repeat(256),
      status: true,
      remarks: 'keep',
    });
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(component.form.valid).toBeTrue();
    component.form.controls.photo.markAsDirty();
    component.form.controls.photo.setValue('');
    component.save();
    const request = http.expectOne(`${environment.apiUrl}/user/7`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ photo: null });
    request.flush({
      id: 7,
      name: 'Researcher',
      email: 'r@example.test',
      photo: null,
      status: true,
      remarks: 'keep',
    });
    flush();
  }));

  it('requires a password for creation and defaults status to enabled', () => {
    component.form.controls.name.setValue('Researcher');
    component.form.controls.email.setValue('r@example.test');
    expect(component.form.controls.password.invalid).toBeTrue();
    expect(component.form.controls.status.value).toBeTrue();
    component.form.controls.password.setValue(' exact ');
    component.save();
    const request = TestBed.inject(HttpTestingController).expectOne(
      `${environment.apiUrl}/user`,
    );
    expect(request.request.body.password).toBe(' exact ');
    expect(request.request.body.status).toBeTrue();
    expect(request.request.body.id).toBeUndefined();
    request.flush({
      id: 8,
      name: 'Researcher',
      email: 'r@example.test',
      photo: null,
      status: true,
      remarks: null,
    });
  });
  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
