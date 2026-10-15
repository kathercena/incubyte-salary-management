export type SalaryPeriod = 'ANNUAL' | 'MONTHLY' | 'HOURLY';

export interface Employee {
  id: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  countryCode: string;
  department: string;
  jobTitle: string;
  amount: string;
  currencyCode: string;
  salaryPeriod: SalaryPeriod;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeCreate {
  employeeNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  countryCode: string;
  department: string;
  jobTitle: string;
  amount: string;
  currencyCode: string;
  salaryPeriod: SalaryPeriod;
}

export interface EmployeeProfileUpdate {
  employeeNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  countryCode: string;
  department: string;
  jobTitle: string;
  version: number;
}

export interface Compensation {
  employeeId: string;
  amount: string;
  currencyCode: string;
  salaryPeriod: SalaryPeriod;
  version: number;
}

export interface SalaryChange {
  id: string;
  employeeId: string;
  previousAmount: string;
  previousCurrencyCode: string;
  previousSalaryPeriod: SalaryPeriod;
  newAmount: string;
  newCurrencyCode: string;
  newSalaryPeriod: SalaryPeriod;
  changedBy: string;
  changedAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors: ApiFieldError[];
}

export type EmployeeSortField =
  | 'employeeNumber'
  | 'firstName'
  | 'lastName'
  | 'countryCode'
  | 'department'
  | 'jobTitle'
  | 'createdAt';

export interface EmployeeSearchQuery {
  page: number;
  size: number;
  name?: string;
  employeeNumber?: string;
  countryCode?: string;
  department?: string;
  sortBy: EmployeeSortField;
  sortDirection: 'asc' | 'desc';
}

export interface CompensationUpdate {
  /** Decimal string keeps the user's exact input instead of converting money through binary floating point. */
  amount: string;
  currencyCode: string;
  salaryPeriod: SalaryPeriod;
  version: number;
}
