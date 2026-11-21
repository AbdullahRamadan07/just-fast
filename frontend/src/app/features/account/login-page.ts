import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { Logo } from '../../shared/logo';

/** Only allow in-app return URLs (prevents open redirects). */
export function safeReturnUrl(url: string | undefined, fallback: string): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : fallback;
}

@Component({
  selector: 'jf-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, Logo],
  styleUrl: './auth-card.scss',
  template: `
    <section class="container page auth">
      <div class="card stack">
        <div class="head">
          <jf-logo variant="mark" [height]="52" />
          <h1>Welcome back</h1>
        </div>
        @if (error()) {
          <div class="alert error">{{ error() }}</div>
        }
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="field">
            <label for="email">Email</label>
            <input id="email" type="email" formControlName="email" autocomplete="email" />
          </div>
          <div class="field">
            <label for="password">Password</label>
            <input id="password" type="password" formControlName="password" autocomplete="current-password" />
          </div>
          <button class="btn block" type="submit" [disabled]="busy()">{{ busy() ? 'Signing in…' : 'Sign in' }}</button>
        </form>
        <p class="muted small">
          New to Just Fast?
          <a routerLink="/account/register" [queryParams]="{ returnUrl: returnUrl() }">Create an account</a>
        </p>
      </div>
    </section>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly returnUrl = input<string>();
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const { email, password } = this.form.getRawValue();
      const user = await this.auth.login(email, password);
      const fallback = user.role === 'ADMIN' ? '/admin' : '/';
      await this.router.navigateByUrl(safeReturnUrl(this.returnUrl(), fallback));
    } catch (e) {
      this.error.set(errorMessage(e, 'Invalid email or password.'));
    } finally {
      this.busy.set(false);
    }
  }
}
