import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { Logo } from '../../shared/logo';
import { safeReturnUrl } from './login-page';

@Component({
  selector: 'jf-register-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, Logo],
  styleUrl: './auth-card.scss',
  template: `
    <section class="container page auth">
      <div class="card stack">
        <div class="head">
          <jf-logo variant="mark" [height]="52" />
          <h1>Create your account</h1>
        </div>
        @if (error()) {
          <div class="alert error">{{ error() }}</div>
        }
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="field">
            <label for="fullName">Full name</label>
            <input id="fullName" formControlName="fullName" autocomplete="name" />
          </div>
          <div class="field">
            <label for="email">Email</label>
            <input id="email" type="email" formControlName="email" autocomplete="email" />
          </div>
          <div class="field">
            <label for="password">Password</label>
            <input id="password" type="password" formControlName="password" autocomplete="new-password" />
            @if (form.controls.password.touched && form.controls.password.invalid) {
              <span class="error">Use 8 to 72 characters.</span>
            }
          </div>
          <button class="btn block" type="submit" [disabled]="busy()">
            {{ busy() ? 'Creating account…' : 'Create account' }}
          </button>
        </form>
        <p class="muted small">
          Already have an account?
          <a routerLink="/account/login" [queryParams]="{ returnUrl: returnUrl() }">Sign in</a>
        </p>
      </div>
    </section>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly returnUrl = input<string>();
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const { fullName, email, password } = this.form.getRawValue();
      await this.auth.register(fullName, email, password);
      await this.router.navigateByUrl(safeReturnUrl(this.returnUrl(), '/'));
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
