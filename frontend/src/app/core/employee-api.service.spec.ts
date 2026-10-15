import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from './api-base-url';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { EmployeeApiService, buildEmployeeSearchParams } from './employee-api.service';
import { EmployeeSearchQuery, PageResponse, Employee, EmployeeCreate, EmployeeProfileUpdate } from './models';

describe('EmployeeApiService', () => {
  let api: EmployeeApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        EmployeeApiService,
        AuthService,
        { provide: API_BASE_URL, useValue: '/hr-api/' },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(EmployeeApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('builds combined filters, server pagination, and allowlisted sort parameters', () => {
    const query: EmployeeSearchQuery = {
      page: 2,
      size: 25,
      name: ' Jane Smith ',
      employeeNumber: ' SYN-00042 ',
      countryCode: 'IN',
      department: ' Engineering ',
      sortBy: 'employeeNumber',
      sortDirection: 'desc',
    };
    const params = buildEmployeeSearchParams(query);

    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('25');
    expect(params.get('sort')).toBe('employeeNumber,desc');
    expect(params.get('name')).toBe('Jane Smith');
    expect(params.get('employeeNumber')).toBe('SYN-00042');
    expect(params.get('countryCode')).toBe('IN');
    expect(params.get('department')).toBe('Engineering');
  });

  it('omits blank filters and keeps the API base URL configurable', () => {
    const params = buildEmployeeSearchParams({
      page: 0,
      size: 25,
      name: '  ',
      sortBy: 'lastName',
      sortDirection: 'asc',
    });
    expect(params.has('name')).toBe(false);

    let page: PageResponse<Employee> | undefined;
    api.search({ page: 0, size: 25, sortBy: 'lastName', sortDirection: 'asc' }).subscribe((response) => page = response);
    const request = http.expectOne((candidate) => candidate.url === '/hr-api/employees'
      && candidate.params.get('page') === '0'
      && candidate.params.get('size') === '25'
      && candidate.params.get('sort') === 'lastName,asc');
    request.flush({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    expect(page?.totalElements).toBe(0);
  });

  it('attaches Basic authorization only to the configured API and sends decimal text with update fields', () => {
    const auth = TestBed.inject(AuthService);
    auth.setCredentials('hr-user', 'local-test-password');
    api.updateCompensation('employee-1', {
      amount: '75000.1250',
      currencyCode: 'USD',
      salaryPeriod: 'ANNUAL',
      version: 4,
    }).subscribe();

    const request = http.expectOne('/hr-api/employees/employee-1/compensation');
    expect(request.request.headers.get('Authorization')).toBe(auth.authorizationHeader());
    expect(request.request.body).toEqual({
      amount: '75000.1250',
      currencyCode: 'USD',
      salaryPeriod: 'ANNUAL',
      version: 4,
    });
    request.flush({ employeeId: 'employee-1', amount: '75000.1250', currencyCode: 'USD', salaryPeriod: 'ANNUAL', version: 5 });

    api.getHistory('employee-1').subscribe();
    const history = http.expectOne('/hr-api/employees/employee-1/compensation/history?page=0&size=10');
    expect(history.request.headers.get('Authorization')).toBe(auth.authorizationHeader());
    history.flush({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('does not send credentials to a URL outside the configured API path', () => {
    const auth = TestBed.inject(AuthService);
    auth.setCredentials('hr-user', 'local-test-password');
    let responseReceived = false;
    TestBed.inject(HttpClient).get('/hr-api-elsewhere').subscribe(() => responseReceived = true);

    const request = http.expectOne('/hr-api-elsewhere');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
    expect(responseReceived).toBe(true);
  });

  it('posts the complete employee profile and initial decimal-string compensation', () => {
    const payload: EmployeeCreate = { employeeNumber: 'EMP-NEW', firstName: 'Pat', lastName: 'Lee',
      email: 'pat.lee@example.test', countryCode: 'US', department: 'People', jobTitle: 'Partner',
      amount: '999999999999999.9999', currencyCode: 'USD', salaryPeriod: 'MONTHLY' };
    api.createEmployee(payload).subscribe();
    const request = http.expectOne('/hr-api/employees');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ id: 'new-id', ...payload, version: 0 });
  });

  it('updates only profile fields and carries the optimistic version', () => {
    const payload: EmployeeProfileUpdate = { employeeNumber: 'EMP-NEW', firstName: 'Pat', lastName: 'Lee',
      email: 'pat.lee@example.test', countryCode: 'US', department: 'People', jobTitle: 'Partner', version: 3 };
    api.updateEmployeeProfile('employee-1', payload).subscribe();
    const request = http.expectOne('/hr-api/employees/employee-1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    expect(request.request.body).not.toHaveProperty('amount');
    request.flush({ id: 'employee-1', ...payload, amount: '1000.0000', currencyCode: 'USD', salaryPeriod: 'ANNUAL' });
  });
});
