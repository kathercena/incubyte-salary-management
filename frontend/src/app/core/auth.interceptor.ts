import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from './api-base-url';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const apiBase = inject(API_BASE_URL);
  const authorization = inject(AuthService).authorizationHeader();
  const normalizedRequestUrl = request.url.toLowerCase();
  const normalizedApiBase = apiBase.replace(/\/+$/, '').toLowerCase();
  const targetsApi = normalizedRequestUrl.startsWith(`${normalizedApiBase}/`)
    || normalizedRequestUrl === normalizedApiBase;

  return authorization && targetsApi
    ? next(request.clone({ setHeaders: { Authorization: authorization } }))
    : next(request);
};
