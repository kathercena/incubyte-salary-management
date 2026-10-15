import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
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
    getEmployee: ReturnType<typeof vi.fn>;
    getCompensation: ReturnType<typeof vi.fn>;
    getHistory: ReturnType<typeof vi.fn>;
    updateCompensation: ReturnType<typeof vi.fn>;
  };
  let auth: AuthService;

  beforeEach(async () => {
    api = {
      search: vi.fn().mockReturnValue(of({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 })),
      getEmployee: vi.fn().mockReturnValue(of(EMPLOYEE)),
      getCompensation: vi.fn().mockReturnValue(of(COMPENSATION)),
      getHistory: vi.fn().mockReturnValue(of(EMPTY_HISTORY)),
      updateCompensation: vi.fn().mockReturnValue(of(COMPENSATION)),
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
    component.signOut();

    expect(auth.authenticated()).toBe(false);
    expect(auth.authorizationHeader()).toBeNull();
  });

  it('includes backend field validation messages for invalid requests', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { message: 'Request validation failed.', fieldErrors: [{ field: 'currencyCode', message: 'Invalid code.' }] },
    });
    expect(apiErrorMessage(error, 'save')).toBe('currencyCode: Invalid code.');
  });
});
