import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from './api-base-url';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const apiBase = inject(API_BASE_URL);
  const authorization = inject(AuthService).authorizationHeader();
  let targetsApi = false;
  try {
    const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
    const configuredApi = new URL(apiBase, origin);
    const requestUrl = new URL(request.url, origin);
    const configuredPath = configuredApi.pathname.replace(/\/+$/, '');
    targetsApi = configuredPath === '/api'
      && requestUrl.origin === configuredApi.origin
      && (requestUrl.pathname === '/api' || requestUrl.pathname.startsWith('/api/'));
  } catch {
    targetsApi = false;
  }

  return authorization && targetsApi
    ? next(request.clone({ setHeaders: { Authorization: authorization } }))
    : next(request);
};
