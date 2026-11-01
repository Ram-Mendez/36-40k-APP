import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = sessionStorage.getItem('accessToken');
  const publicAuth = request.url.endsWith('/auth/login') || request.url.endsWith('/auth/refresh');
  return next(token && request.url.startsWith('/api/') && !publicAuth ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request);
};
