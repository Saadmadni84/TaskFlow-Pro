import { ApiErrorResponse } from '@/types';

const rawBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_URL ||
  'http://localhost:8080/api';

const BASE_URL = rawBaseUrl.endsWith('/api')
  ? rawBaseUrl
  : `${rawBaseUrl.replace(/\/+$/, '')}/api`;

export class ApiClientError extends Error {
  public readonly status: number;
  public readonly code: string;
  public readonly details?: { field: string; message: string }[];
  public readonly path?: string;
  public readonly requestId?: string;

  constructor(errorResponse: ApiErrorResponse) {
    super(errorResponse.message || 'An unexpected API error occurred');
    this.name = 'ApiClientError';
    this.status = errorResponse.status;
    this.code = errorResponse.code;
    this.details = errorResponse.details;
    this.path = errorResponse.path;
    this.requestId = errorResponse.requestId;
  }
}

/**
 * Normalizes any error (ApiClientError, standard Error, string) into a user-facing
 * message, machine-readable code, and correlation requestId for diagnostics.
 */
export function formatApiError(err: unknown): {
  message: string;
  code?: string;
  requestId?: string;
} {
  if (err instanceof ApiClientError) {
    let friendlyMessage = err.message;

    switch (err.code) {
      case 'CYCLE_DETECTED':
      case 'DEPENDENCY_CYCLE':
        friendlyMessage = 'This dependency would create a cycle, so it was not added.';
        break;
      case 'CONCURRENCY_CONFLICT':
      case 'RESOURCE_VERSION_CONFLICT':
        friendlyMessage = 'This task was changed elsewhere. Refresh and try again.';
        break;
      case 'VALIDATION_ERROR':
        friendlyMessage = err.details?.length
          ? `Validation error: ${err.details.map(d => d.message).join(', ')}`
          : 'Please check the task details and try again.';
        break;
      case 'AI_DISABLED':
      case 'AI_UNAVAILABLE':
        friendlyMessage = 'AI suggestions are temporarily unavailable. You can add the dependency manually.';
        break;
      case 'RATE_LIMIT_EXCEEDED':
        friendlyMessage = 'Rate limit reached. Please wait a moment before trying again.';
        break;
      case 'RESOURCE_NOT_FOUND':
        friendlyMessage = err.message || 'The requested resource was not found.';
        break;
      case 'NETWORK_FAILURE':
        friendlyMessage = 'Unable to connect to TaskFlow Pro backend. Check network connectivity.';
        break;
    }

    return {
      message: friendlyMessage,
      code: err.code,
      requestId: err.requestId,
    };
  }

  if (err instanceof Error) {
    return {
      message: err.message || 'An unexpected error occurred.',
    };
  }

  return {
    message: typeof err === 'string' ? err : 'An unexpected error occurred.',
  };
}

async function request<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
  const url = `${BASE_URL}${cleanEndpoint}`;

  const defaultHeaders: HeadersInit = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };

  const config: RequestInit = {
    ...options,
    headers: {
      ...defaultHeaders,
      ...options?.headers,
    },
  };

  let response: Response;
  try {
    response = await fetch(url, config);
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Network request failed';
    throw new ApiClientError({
      timestamp: new Date().toISOString(),
      status: 0,
      code: 'NETWORK_FAILURE',
      message: `Unable to connect to TaskFlow Pro backend: ${errorMsg}`,
      path: cleanEndpoint,
    });
  }

  if (!response.ok) {
    const headerRequestId = response.headers.get('X-Request-Id') || undefined;
    let errorData: ApiErrorResponse;
    try {
      errorData = await response.json();
      if (!errorData.requestId && headerRequestId) {
        errorData.requestId = headerRequestId;
      }
    } catch {
      errorData = {
        timestamp: new Date().toISOString(),
        status: response.status,
        code: 'HTTP_ERROR',
        message: `Request failed with status ${response.status}: ${response.statusText}`,
        path: cleanEndpoint,
        requestId: headerRequestId,
      };
    }
    throw new ApiClientError(errorData);
  }

  // Handle empty responses (like 204 No Content)
  if (response.status === 204) {
    return {} as T;
  }

  const text = await response.text();
  return text ? (JSON.parse(text) as T) : ({} as T);
}

export const apiClient = {
  get: <T>(endpoint: string, options?: RequestInit) => request<T>(endpoint, { ...options, method: 'GET' }),
  post: <T>(endpoint: string, body?: unknown, options?: RequestInit) =>
    request<T>(endpoint, {
      ...options,
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    }),
  put: <T>(endpoint: string, body?: unknown, options?: RequestInit) =>
    request<T>(endpoint, {
      ...options,
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    }),
  patch: <T>(endpoint: string, body?: unknown, options?: RequestInit) =>
    request<T>(endpoint, {
      ...options,
      method: 'PATCH',
      body: body ? JSON.stringify(body) : undefined,
    }),
  delete: <T>(endpoint: string, options?: RequestInit) =>
    request<T>(endpoint, { ...options, method: 'DELETE' }),
};
