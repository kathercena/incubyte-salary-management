import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-base-url';
import {
  Compensation,
  CompensationUpdate,
  Employee,
  EmployeeSearchQuery,
  PageResponse,
  SalaryChange,
} from './models';

export function buildEmployeeSearchParams(query: EmployeeSearchQuery): HttpParams {
  let params = new HttpParams()
    .set('page', query.page)
    .set('size', query.size)
    .set('sort', `${query.sortBy},${query.sortDirection}`);

  for (const key of ['name', 'employeeNumber', 'countryCode', 'department'] as const) {
    const value = query[key]?.trim();
    if (value) {
      params = params.set(key, value);
    }
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class EmployeeApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = inject(API_BASE_URL).replace(/\/+$/, '');

  search(query: EmployeeSearchQuery): Observable<PageResponse<Employee>> {
    return this.http.get<PageResponse<Employee>>(`${this.apiBase}/employees`, {
      params: buildEmployeeSearchParams(query),
    });
  }

  getEmployee(id: string): Observable<Employee> {
    return this.http.get<Employee>(`${this.apiBase}/employees/${encodeURIComponent(id)}`);
  }

  getCompensation(id: string): Observable<Compensation> {
    return this.http.get<Compensation>(`${this.apiBase}/employees/${encodeURIComponent(id)}/compensation`);
  }

  getHistory(id: string): Observable<PageResponse<SalaryChange>> {
    return this.http.get<PageResponse<SalaryChange>>(
      `${this.apiBase}/employees/${encodeURIComponent(id)}/compensation/history`,
      { params: new HttpParams().set('page', 0).set('size', 10) },
    );
  }

  updateCompensation(id: string, update: CompensationUpdate): Observable<Compensation> {
    return this.http.put<Compensation>(
      `${this.apiBase}/employees/${encodeURIComponent(id)}/compensation`,
      update,
    );
  }
}
