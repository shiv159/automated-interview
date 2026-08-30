import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AdminKeyService } from './admin-key.service';

/** Attaches X-Api-Key to every /api/v1/question-bank request.
 *  Clears the stored key on 401 so the component can re-prompt. */
export const questionBankAuthInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const adminKey = inject(AdminKeyService);

  if (!req.url.includes('/api/v1/question-bank')) {
    return next(req);
  }

  const key = adminKey.getKey();
  const authReq = key
    ? req.clone({ setHeaders: { 'X-Api-Key': key } })
    : req;

  return next(authReq).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        adminKey.clearKey();
      }
      return throwError(() => err);
    })
  );
};
