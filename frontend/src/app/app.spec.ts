import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { App, apiErrorMessage } from './app';
import { AuthService } from './core/auth.service';
import { EmployeeApiService } from './core/employee-api.service';
import { Compensation, Employee, PageResponse, SalaryChange } from './core/models';

const EMPLOYEE: Employee = {
  id: 'employee-1',
  employeeNumber: 'SYN-00001',
  firstName: 'Alex',
  lastName: 'Taylor',
  email: 'employee00001@seed.example.test',
  countryCode: 'US',
  department: 'Engineering',
  jobTitle: 'Engineer',
  amount: '75000.1250',
  currencyCode: 'USD',
  salaryPeriod: 'ANNUAL',
  version: 0,
  createdAt: '2025-01-01T00:01:00Z',
  updatedAt: '2025-01-01T00:01:00Z',
};

const COMPENSATION: Compensation = {
  employeeId: EMPLOYEE.id,
  amount: '75000.1250',
  currencyCode: 'USD',
  salaryPeriod: 'ANNUAL',
  version: 0,
};

const EMPTY_HISTORY: PageResponse<SalaryChange> = {
  items: [], page: 0, size: 10, totalElements: 0, totalPages: 0,
};

describe('Employee directory and compensation workspace', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<App>>;
  let component: App;
  let api: {
    search: ReturnType<typeof vi.fn>;
    createEmployee: ReturnType<typeof vi.fn>;
    getEmployee: ReturnType<typeof vi.fn>;
    getCompensation: ReturnType<typeof vi.fn>;
    getHistory: ReturnType<typeof vi.fn>;
    updateCompensation: ReturnType<typeof vi.fn>;
    updateEmployeeProfile: ReturnType<typeof vi.fn>;
  };
  let auth: AuthService;

  beforeEach(async () => {
    api = {
      search: vi.fn().mockReturnValue(of({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 })),
      createEmployee: vi.fn().mockReturnValue(of(EMPLOYEE)),
      getEmployee: vi.fn().mockReturnValue(of(EMPLOYEE)),
      getCompensation: vi.fn().mockReturnValue(of(COMPENSATION)),
      getHistory: vi.fn().mockReturnValue(of(EMPTY_HISTORY)),
      updateCompensation: vi.fn().mockReturnValue(of(COMPENSATION)),
      updateEmployeeProfile: vi.fn().mockReturnValue(of(EMPLOYEE)),
    };
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [{ provide: EmployeeApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance;
    auth = TestBed.inject(AuthService);
    fixture.detectChanges();
  });

  it('shows loading, recoverable error, and empty-result states', () => {
    auth.markAuthenticated();
    component.loading.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading employees');

    component.loading.set(false);
    component.directoryError.set('Service temporarily unavailable.');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Service temporarily unavailable.');

    component.directoryError.set(null);
    component.loaded.set(true);
    component.employees.set([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No employees found');
  });

  it('submits combined filters and requests the selected server page and sort', () => {
    auth.markAuthenticated();
    api.search.mockReturnValue(of({ items: [], page: 0, size: 25, totalElements: 80, totalPages: 4 }));
    component.filtersForm.setValue({
      name: ' Jane Smith ',
      employeeNumber: ' SYN-00042 ',
      countryCode: 'IN',
      department: ' Engineering ',
    });
    component.page.set(2);

    component.search();
    expect(api.search).toHaveBeenNthCalledWith(1, {
      page: 0, size: 25, name: 'Jane Smith', employeeNumber: 'SYN-00042', countryCode: 'IN',
      department: 'Engineering', sortBy: 'lastName', sortDirection: 'asc',
    });

    component.toggleSort('employeeNumber');
    expect(api.search).toHaveBeenNthCalledWith(2, {
      page: 0, size: 25, name: 'Jane Smith', employeeNumber: 'SYN-00042', countryCode: 'IN',
      department: 'Engineering', sortBy: 'employeeNumber', sortDirection: 'asc',
    });
    component.goToPage(1);
    expect(api.search).toHaveBeenNthCalledWith(3, {
      page: 1, size: 25, name: 'Jane Smith', employeeNumber: 'SYN-00042', countryCode: 'IN',
      department: 'Engineering', sortBy: 'employeeNumber', sortDirection: 'asc',
    });
  });

  it('displays compensation with its explicit currency and salary period', () => {
    auth.markAuthenticated();
    component.loaded.set(true);
    component.employees.set([EMPLOYEE]);
    component.totalElements.set(1);
    component.totalPages.set(1);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('USD');
    expect(fixture.nativeElement.textContent).toContain('per year');
    expect(fixture.nativeElement.textContent).toContain('SYN-00001');
  });

  it('preserves the maximum supported decimal amount through display and edit submission', () => {
    const maximumAmount = '999999999999999.9999';
    const employee = { ...EMPLOYEE, amount: maximumAmount };
    const compensation = { ...COMPENSATION, amount: maximumAmount };
    component.selectedEmployee.set(employee);
    component.compensation.set(compensation);

    const integer = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(999999999999999n);
    const decimal = new Intl.NumberFormat(undefined).formatToParts(1.1)
      .find((part) => part.type === 'decimal')?.value ?? '.';
    expect(component.formatAmount(maximumAmount, 'USD')).toBe(`USD ${integer}${decimal}9999`);
    component.startCompensationEdit();
    expect(component.compensationForm.controls.amount.value).toBe(maximumAmount);
    component.saveCompensation();

    expect(api.updateCompensation).toHaveBeenCalledWith(employee.id, {
      amount: maximumAmount,
      currencyCode: 'USD',
      salaryPeriod: 'ANNUAL',
      version: compensation.version,
    });
  });

  it('validates compensation amount and currency before calling the API', () => {
    auth.markAuthenticated();
    component.selectedEmployee.set(EMPLOYEE);
    component.compensation.set(COMPENSATION);
    component.editingCompensation.set(true);
    component.compensationForm.controls.amount.setValue('-1.0000');
    component.compensationForm.controls.amount.markAsTouched();
    component.compensationForm.controls.currencyCode.setValue('US');
    component.compensationForm.controls.currencyCode.markAsTouched();

    component.saveCompensation();
    fixture.detectChanges();

    expect(api.updateCompensation).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('non-negative amount');
    expect(fixture.nativeElement.textContent).toContain('three-letter ISO currency code');
  });

  it('requires a refresh after a stale-version conflict and prevents silent retry', () => {
    component.selectedEmployee.set(EMPLOYEE);
    component.compensation.set(COMPENSATION);
    component.editingCompensation.set(true);
    component.compensationForm.reset({ amount: '81000.5000', currencyCode: 'USD', salaryPeriod: 'ANNUAL' });
    api.updateCompensation.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

    component.saveCompensation();
    fixture.detectChanges();

    expect(component.staleCompensation()).toBe(true);
    expect(component.compensationError()).toContain('changed after you opened it');
    expect(api.updateCompensation).toHaveBeenCalledTimes(1);
    component.saveCompensation();
    expect(api.updateCompensation).toHaveBeenCalledTimes(1);
  });

  it('updates with the loaded version and refreshes current compensation and history', () => {
    const updated: Compensation = { ...COMPENSATION, amount: '81000.5000', version: 1 };
    component.selectedEmployee.set(EMPLOYEE);
    component.compensation.set(COMPENSATION);
    component.editingCompensation.set(true);
    component.compensationForm.reset({ amount: '81000.5000', currencyCode: 'USD', salaryPeriod: 'MONTHLY' });
    api.updateCompensation.mockReturnValue(of(updated));
    api.getCompensation.mockReturnValue(of({ ...updated, salaryPeriod: 'MONTHLY' }));
    api.getHistory.mockReturnValue(of({
      items: [{
        id: 'change-1', employeeId: EMPLOYEE.id, previousAmount: COMPENSATION.amount,
        previousCurrencyCode: 'USD', previousSalaryPeriod: 'ANNUAL', newAmount: '81000.5000',
        newCurrencyCode: 'USD', newSalaryPeriod: 'MONTHLY', changedBy: 'hr-user',
        changedAt: '2025-01-02T00:00:00Z',
      }], page: 0, size: 10, totalElements: 1, totalPages: 1,
    }));

    component.saveCompensation();

    expect(api.updateCompensation).toHaveBeenCalledWith(EMPLOYEE.id, {
      amount: '81000.5000', currencyCode: 'USD', salaryPeriod: 'MONTHLY', version: 0,
    });
    expect(api.getCompensation).toHaveBeenCalledWith(EMPLOYEE.id);
    expect(component.compensation()?.version).toBe(1);
    expect(component.compensation()?.salaryPeriod).toBe('MONTHLY');
    expect(component.history()).toHaveLength(1);
    expect(component.compensationSaved()).toBe(true);
  });

  it('converts authentication and authorization failures into clear sign-in messages', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 401 }), 'login'))
      .toContain('credentials were not accepted');
    expect(apiErrorMessage(new HttpErrorResponse({ status: 403 }), 'directory'))
      .toContain('not permitted');
    expect(apiErrorMessage(new HttpErrorResponse({ status: 404 }), 'detail'))
      .toContain('could not be found');
  });

  it('clears in-memory credentials when the HR user signs out', () => {
    auth.setCredentials('hr-user', 'local-test-password');
    auth.markAuthenticated();
    component.loginForm.setValue({ username: 'hr-user', password: 'local-test-password' });
    component.signOut();

    expect(auth.authenticated()).toBe(false);
    expect(auth.authorizationHeader()).toBeNull();
    expect(component.loginForm.getRawValue()).toEqual({ username: '', password: '' });
  });

  it('includes backend field validation messages for invalid requests', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { message: 'Request validation failed.', fieldErrors: [{ field: 'currencyCode', message: 'Invalid code.' }] },
    });
    expect(apiErrorMessage(error, 'save')).toBe('currencyCode: Invalid code.');
  });

  it('validates all required employee and initial compensation fields before creating', () => {
    component.openCreateEmployee();
    component.createEmployee();
    expect(api.createEmployee).not.toHaveBeenCalled();
    expect(component.createForm.invalid).toBe(true);
    expect(component.createFieldErrors()).toContain('Employee number is required.');
    expect(component.createFieldErrors()).toContain('Amount is required.');
    expect(component.showCreateEmployee()).toBe(true);
  });

  it('creates with exact profile and decimal-string compensation payload, then refreshes and opens the record', () => {
    const created = { ...EMPLOYEE, employeeNumber: 'EMP-NEW', amount: '999999999999999.9999' };
    api.createEmployee.mockReturnValue(of(created));
    component.openCreateEmployee();
    component.createForm.setValue({ employeeNumber: 'EMP-NEW', firstName: 'Pat', lastName: 'Lee',
      email: 'pat.lee@example.test', countryCode: 'US', department: 'People', jobTitle: 'Partner',
      amount: '999999999999999.9999', currencyCode: 'USD', salaryPeriod: 'MONTHLY' });
    expect(component.createForm.valid).toBe(true);
    component.createEmployee();
    expect(api.createEmployee).toHaveBeenCalledWith({ employeeNumber: 'EMP-NEW', firstName: 'Pat', lastName: 'Lee',
      email: 'pat.lee@example.test', countryCode: 'US', department: 'People', jobTitle: 'Partner',
      amount: '999999999999999.9999', currencyCode: 'USD', salaryPeriod: 'MONTHLY' });
    expect(component.showCreateEmployee()).toBe(false);
    expect(component.createSaved()).toBe(true);
    expect(component.filtersForm.controls.employeeNumber.value).toBe('EMP-NEW');
    expect(component.selectedEmployee()?.id).toBe(created.id);
    expect(api.search).toHaveBeenCalledWith(expect.objectContaining({ page: 0, employeeNumber: 'EMP-NEW' }));
  });

  it('keeps entered values after create validation, duplicate, network, or authentication failure', () => {
    component.openCreateEmployee();
    component.createForm.setValue({ employeeNumber: 'EMP-NEW', firstName: 'Pat', lastName: 'Lee',
      email: 'pat.lee@example.test', countryCode: 'US', department: 'People', jobTitle: 'Partner',
      amount: '100.00', currencyCode: 'USD', salaryPeriod: 'ANNUAL' });
    api.createEmployee.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { message: 'Duplicate employee.' },
    })));
    component.createEmployee();
    expect(component.createError()).toContain('already exists');
    expect(component.createForm.controls.employeeNumber.value).toBe('EMP-NEW');
    expect(component.showCreateEmployee()).toBe(true);
    expect(apiErrorMessage(new HttpErrorResponse({ status: 403 }), 'create')).toContain('not permitted');
    expect(apiErrorMessage(new HttpErrorResponse({ status: 0 }), 'create')).toContain('Something went wrong');
  });

  it('disables repeated employee creation while the request is pending', () => {
    const pending = new Subject<Employee>();
    api.createEmployee.mockReturnValue(pending);
    component.openCreateEmployee();
    component.createForm.setValue({ employeeNumber: 'EMP-NEW', firstName: 'Pat', lastName: 'Lee',
      email: 'pat.lee@example.test', countryCode: 'US', department: 'People', jobTitle: 'Partner',
      amount: '100.00', currencyCode: 'USD', salaryPeriod: 'ANNUAL' });
    component.createEmployee();
    expect(component.creatingEmployee()).toBe(true);
    component.createEmployee();
    expect(api.createEmployee).toHaveBeenCalledTimes(1);
    pending.next(EMPLOYEE);
    pending.complete();
    expect(component.creatingEmployee()).toBe(false);
  });

  it('populates profile values and submits only profile fields with current version', () => {
    component.openEmployee(EMPLOYEE);
    component.startProfileEdit();
    expect(component.profileForm.getRawValue()).toEqual({ employeeNumber: EMPLOYEE.employeeNumber,
      firstName: EMPLOYEE.firstName, lastName: EMPLOYEE.lastName, email: EMPLOYEE.email,
      countryCode: EMPLOYEE.countryCode, department: EMPLOYEE.department, jobTitle: EMPLOYEE.jobTitle });
    component.profileForm.patchValue({ department: 'People' });
    component.saveProfile();
    expect(api.updateEmployeeProfile).toHaveBeenCalledWith(EMPLOYEE.id, {
      employeeNumber: EMPLOYEE.employeeNumber, firstName: EMPLOYEE.firstName, lastName: EMPLOYEE.lastName,
      email: EMPLOYEE.email, countryCode: EMPLOYEE.countryCode, department: 'People',
      jobTitle: EMPLOYEE.jobTitle, version: EMPLOYEE.version,
    });
    expect(api.updateEmployeeProfile.mock.calls[0][1]).not.toHaveProperty('amount');
    expect(api.updateEmployeeProfile.mock.calls[0][1]).not.toHaveProperty('currencyCode');
    expect(component.editingProfile()).toBe(false);
    expect(component.profileSaved()).toBe(true);
    expect(api.search).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }));
    expect(api.getEmployee).toHaveBeenCalledWith(EMPLOYEE.id);
  });

  it('blocks invalid profile edits and preserves profile values after conflict and authentication errors', () => {
    component.openEmployee(EMPLOYEE);
    component.startProfileEdit();
    component.profileForm.controls.email.setValue('not-an-email');
    component.saveProfile();
    expect(api.updateEmployeeProfile).not.toHaveBeenCalled();
    expect(component.profileFieldErrors()).toContain('Email must be a valid email address.');
    component.profileForm.controls.email.setValue(EMPLOYEE.email);
    api.updateEmployeeProfile.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.saveProfile();
    expect(component.profileError()).toContain('already exists');
    expect(component.profileForm.controls.firstName.value).toBe(EMPLOYEE.firstName);
    expect(component.editingProfile()).toBe(true);
    expect(apiErrorMessage(new HttpErrorResponse({ status: 401 }), 'profile')).toContain('session has expired');
  });

  it('handles profile authentication and duplicate conflicts without losing entered data', () => {
    component.openEmployee(EMPLOYEE);
    component.startProfileEdit();
    component.profileForm.controls.department.setValue('People');
    api.updateEmployeeProfile.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    component.saveProfile();
    expect(component.profileForm.controls.department.value).toBe('People');
    expect(component.auth.authenticated()).toBe(false);
    expect(component.profileError()).toContain('session has expired');
  });
});
