import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly authenticated = signal(false);
  private authorization: string | null = null;

  setCredentials(username: string, password: string): void {
    const encoded = btoa(new TextEncoder().encode(`${username}:${password}`)
      .reduce((binary, byte) => binary + String.fromCharCode(byte), ''));
    this.authorization = `Basic ${encoded}`;
  }

  markAuthenticated(): void {
    this.authenticated.set(true);
  }

  authorizationHeader(): string | null {
    return this.authorization;
  }

  clear(): void {
    this.authorization = null;
    this.authenticated.set(false);
  }
}
