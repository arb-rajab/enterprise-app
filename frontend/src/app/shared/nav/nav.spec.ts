import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Nav } from './nav';
import { asAuthInternals } from '../../core/testing/auth-test-utils';

describe('Nav', () => {
  let component: Nav;
  let fixture: ComponentFixture<Nav>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Nav],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Nav);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('hides the nav bar when no one is logged in', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.nav')).toBeNull();
  });

  it('shows the nav bar once authenticated', () => {
    const internals = asAuthInternals(component.auth);
    internals.tokenSignal.set('a-token');
    internals.userSignal.set({
      id: 1,
      email: 'a@b.com',
      firstName: 'Ada',
      lastName: 'Admin',
      departmentId: null,
      roles: ['ROLE_ADMIN'],
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.nav')).toBeTruthy();
    expect(compiled.textContent).toContain('Ada Admin');
  });
});
