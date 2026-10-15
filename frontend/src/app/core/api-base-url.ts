import { InjectionToken } from '@angular/core';

declare global {
  interface Window {
    salaryManagementConfig?: {
      apiBaseUrl?: string;
    };
  }
}

export function configuredApiBaseUrl(): string {
  const configured = typeof window === 'undefined'
    ? undefined
    : window.salaryManagementConfig?.apiBaseUrl;
  return (configured?.trim() || '/api').replace(/\/+$/, '');
}

export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: configuredApiBaseUrl,
});
