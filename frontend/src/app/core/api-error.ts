import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

/** Turns any HTTP failure into a message that is safe to show to the user. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && body.message) {
      const fields = body.fieldErrors ? Object.values(body.fieldErrors) : [];
      return fields.length ? `${body.message} ${fields.join(' ')}` : body.message;
    }
    if (error.status === 0) {
      return 'Cannot reach the store right now. Check your connection and try again.';
    }
    if (error.status === 401) {
      return 'Please sign in to continue.';
    }
    if (error.status === 403) {
      return 'You do not have permission to do that.';
    }
  }
  return fallback;
}

export function errorCode(error: unknown): string | undefined {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return (error.error as Partial<ApiError>).code;
  }
  return undefined;
}
