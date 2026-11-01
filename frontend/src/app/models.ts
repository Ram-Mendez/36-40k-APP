export interface Tokens { accessToken: string; refreshToken: string; tokenType: string; expiresIn: number; }
export interface PageResponse<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export interface Problem { detail?: string; code?: string; requestId?: string; }
