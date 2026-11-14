import { HttpContextToken, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, defer, finalize, tap, throwError } from 'rxjs';
import { WarehouseActivityService } from './warehouse-activity.service';

/** Explicit user operations are tracked; background refreshes stay quiet. */
export const WAREHOUSE_ACTION = new HttpContextToken<boolean>(() => false);

export const warehouseActivityInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.context.get(WAREHOUSE_ACTION) || !request.url.startsWith('/api/v1/')) return next(request);
  const activity = inject(WarehouseActivityService);
  return defer(() => {
    const id = activity.begin(request.method, request.url, request.body);
    let settled = false;
    return next(request).pipe(
      tap(event => {
        if (event instanceof HttpResponse) {
          settled = true;
          activity.succeed(id, event.body, event.status);
        }
      }),
      catchError(error => {
        settled = true;
        activity.fail(id, error);
        return throwError(() => error);
      }),
      finalize(() => { if (!settled) activity.fail(id, { status: 0 }); }),
    );
  });
};
