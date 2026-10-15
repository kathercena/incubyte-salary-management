import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AuthService } from './core/auth.service';
import { EmployeeApiService } from './core/employee-api.service';
import {
  ApiErrorResponse,
  Compensation,
  Employee,
  EmployeeCreate,
  EmployeeProfileUpdate,
  EmployeeSearchQuery,
  EmployeeSortField,
  PageResponse,
  SalaryChange,
  SalaryPeriod,
} from './core/models';

const COUNTRY_OPTIONS = [
  ['US', 'United States'], ['GB', 'United Kingdom'], ['CA', 'Canada'], ['AU', 'Australia'],
  ['DE', 'Germany'], ['FR', 'France'], ['IN', 'India'], ['JP', 'Japan'], ['BR', 'Brazil'],
  ['SG', 'Singapore'], ['MX', 'Mexico'], ['ZA', 'South Africa'], ['NZ', 'New Zealand'], ['CH', 'Switzerland'],
] as const;

const DEFAULT_QUERY: EmployeeSearchQuery = {
  page: 0,
  size: 25,
  sortBy: 'lastName',
  sortDirection: 'asc',
};

export function apiErrorMessage(error: unknown, context: 'login' | 'directory' | 'detail' | 'save' | 'create' | 'profile'): string {
  const status = error instanceof HttpErrorResponse ? error.status : 0;
  if (status === 400) {
    const body = error instanceof HttpErrorResponse ? error.error as Partial<ApiErrorResponse> : undefined;
    const violations = body?.fieldErrors?.map((item) => `${item.field}: ${item.message}`).join(' · ');
    return violations || body?.message || 'Some values were not accepted. Review the fields and try again.';
  }
  if (status === 401) {
    return context === 'login'
      ? 'Those credentials were not accepted. Check them and try again.'
      : 'Your HR session has expired. Sign in again to continue.';
  }
  if (status === 403) {
    return 'This account is not permitted to access the HR Manager workspace.';
  }
  if (status === 404) {
    return context === 'save' || context === 'profile'
      ? 'This employee is no longer available. Refresh the directory before continuing.'
      : 'The requested employee could not be found. Refresh the directory and try again.';
  }
  if (status === 409) {
    return context === 'save'
      ? 'This compensation changed after you opened it. Refresh the current record before making another update.'
      : 'An employee with this employee number or email already exists, or the profile changed. Refresh and review the employee data.';
  }
  return context === 'login'
    ? 'The HR service could not be reached. Check the connection and try again.'
    : 'Something went wrong while contacting the HR service. Your changes have not been confirmed.';
}

@Component({
  imports: [DatePipe, ReactiveFormsModule],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  readonly auth = inject(AuthService);
  private readonly api = inject(EmployeeApiService);

  readonly countries = COUNTRY_OPTIONS;
  readonly periods: SalaryPeriod[] = ['ANNUAL', 'MONTHLY', 'HOURLY'];
  readonly loginForm = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly filtersForm = new FormGroup({
    name: new FormControl('', { nonNullable: true }),
    employeeNumber: new FormControl('', { nonNullable: true }),
    countryCode: new FormControl('', { nonNullable: true }),
    department: new FormControl('', { nonNullable: true }),
  });
  readonly compensationForm = new FormGroup({
    amount: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^(?:0|[1-9]\d{0,14})(?:\.\d{1,4})?$/)],
    }),
    currencyCode: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Z]{3}$/)],
    }),
    salaryPeriod: new FormControl<SalaryPeriod>('ANNUAL', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly createForm = this.createEmployeeForm();
  readonly profileForm = this.profileEditorForm();

  readonly employees = signal<Employee[]>([]);
  readonly page = signal(0);
  readonly pageSize = 25;
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly directoryError = signal<string | null>(null);
  readonly loginError = signal<string | null>(null);
  readonly sortBy = signal<EmployeeSortField>('lastName');
  readonly sortDirection = signal<'asc' | 'desc'>('asc');
  readonly selectedEmployee = signal<Employee | null>(null);
  readonly compensation = signal<Compensation | null>(null);
  readonly history = signal<SalaryChange[]>([]);
  readonly detailLoading = signal(false);
  readonly detailError = signal<string | null>(null);
  readonly editingCompensation = signal(false);
  readonly savingCompensation = signal(false);
  readonly compensationError = signal<string | null>(null);
  readonly compensationSaved = signal(false);
  readonly staleCompensation = signal(false);
  readonly creatingEmployee = signal(false);
  readonly createError = signal<string | null>(null);
  readonly createSaved = signal(false);
  readonly showCreateEmployee = signal(false);
  readonly editingProfile = signal(false);
  readonly savingProfile = signal(false);
  readonly profileError = signal<string | null>(null);
  readonly profileSaved = signal(false);

  submitLogin(): void {
    this.loginError.set(null);
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }
    const { username, password } = this.loginForm.getRawValue();
    this.auth.setCredentials(username.trim(), password);
    this.loading.set(true);
    this.api.search({ ...DEFAULT_QUERY }).subscribe({
      next: (response) => {
        this.auth.markAuthenticated();
        this.setDirectoryPage(response);
        this.loading.set(false);
        this.loginForm.controls.password.reset('');
      },
      error: (error: unknown) => {
        this.auth.clear();
        this.loading.set(false);
        this.loginError.set(apiErrorMessage(error, 'login'));
      },
    });
  }

  signOut(): void {
    this.auth.clear();
    this.loginForm.reset();
    this.employees.set([]);
    this.selectedEmployee.set(null);
    this.compensation.set(null);
    this.history.set([]);
    this.directoryError.set(null);
    this.loginError.set(null);
  }

  search(): void {
    this.loadDirectory(0);
  }

  goToPage(nextPage: number): void {
    if (nextPage < 0 || nextPage >= this.totalPages() || this.loading()) {
      return;
    }
    this.loadDirectory(nextPage);
  }

  toggleSort(field: EmployeeSortField): void {
    const direction = this.sortBy() === field && this.sortDirection() === 'asc' ? 'desc' : 'asc';
    this.sortBy.set(field);
    this.sortDirection.set(direction);
    this.loadDirectory(0);
  }

  sortIndicator(field: EmployeeSortField): string {
    if (this.sortBy() !== field) {
      return '';
    }
    return this.sortDirection() === 'asc' ? '↑' : '↓';
  }

  ariaSort(field: EmployeeSortField): 'ascending' | 'descending' | 'none' {
    return this.sortBy() !== field ? 'none' : this.sortDirection() === 'asc' ? 'ascending' : 'descending';
  }

  openEmployee(employee: Employee): void {
    this.selectedEmployee.set(employee);
    this.compensation.set(null);
    this.history.set([]);
    this.editingCompensation.set(false);
    this.editingProfile.set(false);
    this.profileSaved.set(false);
    this.compensationError.set(null);
    this.compensationSaved.set(false);
    this.refreshEmployeeDetails(employee.id);
  }

  closeEmployee(): void {
    this.selectedEmployee.set(null);
    this.compensation.set(null);
    this.history.set([]);
    this.editingCompensation.set(false);
    this.editingProfile.set(false);
    this.staleCompensation.set(false);
  }

  openCreateEmployee(): void {
    this.createForm.reset({ ...this.emptyEmployeeForm(), salaryPeriod: 'ANNUAL' });
    this.createError.set(null);
    this.createSaved.set(false);
    this.showCreateEmployee.set(true);
  }

  closeCreateEmployee(): void {
    if (!this.creatingEmployee()) this.showCreateEmployee.set(false);
  }

  createEmployee(): void {
    if (this.creatingEmployee()) return;
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    const form = this.createForm.getRawValue();
    const request: EmployeeCreate = {
      employeeNumber: form.employeeNumber.trim(), firstName: form.firstName.trim(),
      lastName: form.lastName.trim(), email: form.email.trim(), countryCode: form.countryCode,
      department: form.department.trim(), jobTitle: form.jobTitle.trim(), amount: form.amount.trim(),
      currencyCode: form.currencyCode.trim(), salaryPeriod: form.salaryPeriod,
    };
    this.creatingEmployee.set(true);
    this.createError.set(null);
    this.createSaved.set(false);
    this.api.createEmployee(request).subscribe({
      next: (created) => {
        this.creatingEmployee.set(false);
        this.showCreateEmployee.set(false);
        this.createSaved.set(true);
        this.filtersForm.reset({ name: '', employeeNumber: created.employeeNumber, countryCode: '', department: '' });
        this.loadDirectory(0);
        this.openEmployee(created);
      },
      error: (error: unknown) => {
        this.creatingEmployee.set(false);
        this.createError.set(apiErrorMessage(error, 'create'));
        this.handleAuthFailure(error);
      },
    });
  }

  startProfileEdit(): void {
    const employee = this.selectedEmployee();
    if (!employee) return;
    this.profileForm.reset({
      employeeNumber: employee.employeeNumber, firstName: employee.firstName, lastName: employee.lastName,
      email: employee.email, countryCode: employee.countryCode, department: employee.department,
      jobTitle: employee.jobTitle,
    });
    this.profileError.set(null);
    this.profileSaved.set(false);
    this.editingProfile.set(true);
  }

  cancelProfileEdit(): void {
    this.editingProfile.set(false);
    this.profileError.set(null);
  }

  saveProfile(): void {
    const employee = this.selectedEmployee();
    if (!employee || this.profileForm.invalid || this.savingProfile()) {
      this.profileForm.markAllAsTouched();
      return;
    }
    const form = this.profileForm.getRawValue();
    const request: EmployeeProfileUpdate = {
      employeeNumber: form.employeeNumber.trim(), firstName: form.firstName.trim(),
      lastName: form.lastName.trim(), email: form.email.trim(), countryCode: form.countryCode,
      department: form.department.trim(), jobTitle: form.jobTitle.trim(), version: employee.version,
    };
    this.savingProfile.set(true);
    this.profileError.set(null);
    this.profileSaved.set(false);
    this.api.updateEmployeeProfile(employee.id, request).subscribe({
      next: () => {
        this.savingProfile.set(false);
        this.editingProfile.set(false);
        this.profileSaved.set(true);
        this.loadDirectory(this.page());
        this.refreshEmployeeDetails(employee.id);
      },
      error: (error: unknown) => {
        this.savingProfile.set(false);
        this.profileError.set(apiErrorMessage(error, 'profile'));
        this.handleAuthFailure(error);
      },
    });
  }

  startCompensationEdit(): void {
    const current = this.compensation();
    if (!current) {
      return;
    }
    this.compensationForm.reset({
      amount: this.amountInput(current.amount),
      currencyCode: current.currencyCode,
      salaryPeriod: current.salaryPeriod,
    });
    this.compensationError.set(null);
    this.compensationSaved.set(false);
    this.staleCompensation.set(false);
    this.editingCompensation.set(true);
  }

  cancelCompensationEdit(): void {
    this.editingCompensation.set(false);
    this.staleCompensation.set(false);
    this.compensationError.set(null);
  }

  saveCompensation(): void {
    const current = this.compensation();
    if (!current || this.staleCompensation()) {
      return;
    }
    if (this.compensationForm.invalid) {
      this.compensationForm.markAllAsTouched();
      return;
    }
    const form = this.compensationForm.getRawValue();
    this.savingCompensation.set(true);
    this.compensationError.set(null);
    this.compensationSaved.set(false);
    this.api.updateCompensation(current.employeeId, {
      amount: form.amount.trim(),
      currencyCode: form.currencyCode.trim(),
      salaryPeriod: form.salaryPeriod,
      version: current.version,
    }).subscribe({
      next: () => {
        this.savingCompensation.set(false);
        this.editingCompensation.set(false);
        this.compensationSaved.set(true);
        this.refreshEmployeeDetails(current.employeeId);
      },
      error: (error: unknown) => {
        this.savingCompensation.set(false);
        this.compensationError.set(apiErrorMessage(error, 'save'));
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.staleCompensation.set(true);
        } else if (error instanceof HttpErrorResponse && error.status === 401) {
          this.auth.clear();
          this.loginError.set(apiErrorMessage(error, 'login'));
        }
      },
    });
  }

  refreshCompensation(): void {
    const selected = this.selectedEmployee();
    if (!selected) {
      return;
    }
    this.refreshEmployeeDetails(selected.id, true);
  }

  formatAmount(amount: string, currencyCode: string): string {
    const [integer, fraction] = amount.split('.');
    const formattedInteger = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(BigInt(integer));
    const decimalSeparator = new Intl.NumberFormat(undefined).formatToParts(1.1)
      .find((part) => part.type === 'decimal')?.value ?? '.';
    return `${currencyCode} ${formattedInteger}${fraction === undefined ? '' : decimalSeparator + fraction}`;
  }

  periodLabel(period: SalaryPeriod): string {
    switch (period) {
      case 'ANNUAL': return 'per year';
      case 'MONTHLY': return 'per month';
      case 'HOURLY': return 'per hour';
    }
  }

  resultRange(): string {
    if (this.totalElements() === 0) {
      return '0 employees';
    }
    const start = this.page() * this.pageSize + 1;
    const end = Math.min((this.page() + 1) * this.pageSize, this.totalElements());
    return `Showing ${start.toLocaleString()}–${end.toLocaleString()} of ${this.totalElements().toLocaleString()} employees`;
  }

  fieldError(name: 'amount' | 'currencyCode'): string | null {
    const control = this.compensationForm.controls[name];
    if (!control.touched || !control.invalid) {
      return null;
    }
    if (control.hasError('required')) {
      return name === 'amount' ? 'Enter a compensation amount.' : 'Enter a currency code.';
    }
    return name === 'amount'
      ? 'Use a non-negative amount with up to 15 integer and 4 decimal digits.'
      : 'Use a three-letter ISO currency code, such as USD.';
  }

  createFieldErrors(): string[] {
    return this.formErrors(this.createForm, {
      employeeNumber: 'Employee number', firstName: 'First name', lastName: 'Last name', email: 'Email',
      countryCode: 'Country', department: 'Department', jobTitle: 'Job title', amount: 'Amount',
      currencyCode: 'Currency code', salaryPeriod: 'Pay period',
    });
  }

  profileFieldErrors(): string[] {
    return this.formErrors(this.profileForm, {
      employeeNumber: 'Employee number', firstName: 'First name', lastName: 'Last name', email: 'Email',
      countryCode: 'Country', department: 'Department', jobTitle: 'Job title',
    });
  }

  private loadDirectory(page: number): void {
    const filters = this.filtersForm.getRawValue();
    const query: EmployeeSearchQuery = {
      page,
      size: this.pageSize,
      name: filters.name.trim() || undefined,
      employeeNumber: filters.employeeNumber.trim() || undefined,
      countryCode: filters.countryCode || undefined,
      department: filters.department.trim() || undefined,
      sortBy: this.sortBy(),
      sortDirection: this.sortDirection(),
    };
    this.loading.set(true);
    this.loaded.set(false);
    this.directoryError.set(null);
    this.api.search(query).subscribe({
      next: (response) => {
        this.page.set(page);
        this.setDirectoryPage(response);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.directoryError.set(apiErrorMessage(error, 'directory'));
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.auth.clear();
          this.loginError.set(apiErrorMessage(error, 'login'));
        }
      },
    });
  }

  private setDirectoryPage(response: PageResponse<Employee>): void {
    this.employees.set(response.items);
    this.page.set(response.page);
    this.totalElements.set(response.totalElements);
    this.totalPages.set(response.totalPages);
    this.loaded.set(true);
    this.directoryError.set(null);
  }

  private refreshEmployeeDetails(employeeId: string, refreshEditForm = false): void {
    this.detailLoading.set(true);
    this.detailError.set(null);
    forkJoin({
      employee: this.api.getEmployee(employeeId),
      compensation: this.api.getCompensation(employeeId),
      history: this.api.getHistory(employeeId),
    }).subscribe({
      next: ({ employee, compensation, history }) => {
        this.selectedEmployee.set(employee);
        this.compensation.set(compensation);
        this.history.set(history.items);
        this.detailLoading.set(false);
        this.staleCompensation.set(false);
        if (refreshEditForm && this.editingCompensation()) {
          this.compensationForm.reset({
            amount: this.amountInput(compensation.amount),
            currencyCode: compensation.currencyCode,
            salaryPeriod: compensation.salaryPeriod,
          });
        }
        if (this.compensationSaved()) {
          this.editingCompensation.set(false);
        }
      },
      error: (error: unknown) => {
        this.detailLoading.set(false);
        this.detailError.set(apiErrorMessage(error, 'detail'));
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.auth.clear();
          this.loginError.set(apiErrorMessage(error, 'login'));
        }
      },
    });
  }

  private amountInput(amount: string): string {
    return amount;
  }

  private handleAuthFailure(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      this.auth.clear();
      this.loginError.set(apiErrorMessage(error, 'login'));
    }
  }

  private emptyEmployeeForm() {
    return { employeeNumber: '', firstName: '', lastName: '', email: '', countryCode: '', department: '',
      jobTitle: '', amount: '', currencyCode: '', salaryPeriod: 'ANNUAL' as SalaryPeriod };
  }

  private createEmployeeForm() {
    const requiredText = (maxLength: number) => [Validators.required, Validators.maxLength(maxLength)];
    return new FormGroup({
      employeeNumber: new FormControl('', { nonNullable: true, validators: [...requiredText(32), Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9._-]*$/)] }),
      firstName: new FormControl('', { nonNullable: true, validators: requiredText(100) }),
      lastName: new FormControl('', { nonNullable: true, validators: requiredText(100) }),
      email: new FormControl('', { nonNullable: true, validators: [...requiredText(254), Validators.email] }),
      countryCode: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[A-Z]{2}$/)] }),
      department: new FormControl('', { nonNullable: true, validators: requiredText(100) }),
      jobTitle: new FormControl('', { nonNullable: true, validators: requiredText(120) }),
      amount: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^(?:0|[1-9]\d{0,14})(?:\.\d{1,4})?$/)] }),
      currencyCode: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[A-Z]{3}$/)] }),
      salaryPeriod: new FormControl<SalaryPeriod>('ANNUAL', { nonNullable: true, validators: [Validators.required] }),
    });
  }

  private profileEditorForm() {
    const requiredText = (maxLength: number) => [Validators.required, Validators.maxLength(maxLength)];
    return new FormGroup({
      employeeNumber: new FormControl('', { nonNullable: true, validators: [...requiredText(32), Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9._-]*$/)] }),
      firstName: new FormControl('', { nonNullable: true, validators: requiredText(100) }),
      lastName: new FormControl('', { nonNullable: true, validators: requiredText(100) }),
      email: new FormControl('', { nonNullable: true, validators: [...requiredText(254), Validators.email] }),
      countryCode: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[A-Z]{2}$/)] }),
      department: new FormControl('', { nonNullable: true, validators: requiredText(100) }),
      jobTitle: new FormControl('', { nonNullable: true, validators: requiredText(120) }),
    });
  }

  private formErrors(form: FormGroup, labels: Record<string, string>): string[] {
    return Object.entries(form.controls).flatMap(([name, control]) => {
      if (!control.invalid) return [];
      const label = labels[name] ?? name;
      if (control.hasError('required')) return [`${label} is required.`];
      if (control.hasError('email')) return [`${label} must be a valid email address.`];
      if (control.hasError('maxlength')) return [`${label} is too long.`];
      if (name === 'amount') return ['Amount must be non-negative with up to 15 integer and 4 fractional digits.'];
      if (name === 'currencyCode') return ['Currency code must be a supported three-letter ISO code.'];
      if (name === 'countryCode') return ['Choose a supported country.'];
      return [`${label} has an invalid format.`];
    });
  }
}
