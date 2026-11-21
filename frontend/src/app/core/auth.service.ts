import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CurrentUser } from './models';

/**
 * Session-cookie authentication. The API sets an HttpOnly session cookie and an XSRF-TOKEN cookie;
 * Angular's HttpClient echoes the latter as X-XSRF-TOKEN on every write.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly user = signal<CurrentUser | null>(null);
  readonly isLoggedIn = computed(() => this.user() !== null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');

  /** Called once at startup (see app.config.ts). */
  async init(): Promise<void> {
    await this.ensureCsrfCookie();
    try {
      this.user.set(await firstValueFrom(this.http.get<CurrentUser>('/api/auth/me')));
    } catch (error) {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        console.warn('Could not load the current user', error);
      }
      this.user.set(null);
    }
  }

  async login(email: string, password: string): Promise<CurrentUser> {
    const user = await firstValueFrom(this.http.post<CurrentUser>('/api/auth/login', { email, password }));
    this.user.set(user);
    return user;
  }

  async register(fullName: string, email: string, password: string): Promise<CurrentUser> {
    const user = await firstValueFrom(
      this.http.post<CurrentUser>('/api/auth/register', { fullName, email, password }),
    );
    this.user.set(user);
    return user;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/logout', {}));
    } finally {
      this.user.set(null);
      // Logout clears the CSRF token; fetch a fresh one for the next write.
      await this.ensureCsrfCookie();
    }
  }

  /** Called by the HTTP interceptor when the server says the session is gone. */
  sessionExpired(): void {
    this.user.set(null);
  }

  private async ensureCsrfCookie(): Promise<void> {
    try {
      await firstValueFrom(this.http.get('/api/auth/csrf'));
    } catch {
      // Non-fatal: the next GET to the API also sets the cookie.
    }
  }
}
