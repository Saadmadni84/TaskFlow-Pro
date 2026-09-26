import { describe, it, expect } from 'vitest';
import { ApiClientError, formatApiError } from '@/lib/api/client';

describe('formatApiError', () => {
  it('formats cycle error with clear user message', () => {
    const error = new ApiClientError({
      timestamp: new Date().toISOString(),
      status: 422,
      code: 'CYCLE_DETECTED',
      message: 'Circular dependency detected',
      path: '/api/dependencies',
      requestId: 'req-cycle-123',
    });

    const formatted = formatApiError(error);
    expect(formatted.message).toBe('This dependency would create a cycle, so it was not added.');
    expect(formatted.code).toBe('CYCLE_DETECTED');
    expect(formatted.requestId).toBe('req-cycle-123');
  });

  it('formats concurrency conflict error with clear user message', () => {
    const error = new ApiClientError({
      timestamp: new Date().toISOString(),
      status: 409,
      code: 'CONCURRENCY_CONFLICT',
      message: 'Modified by another transaction',
      path: '/api/tasks/123',
      requestId: 'req-lock-456',
    });

    const formatted = formatApiError(error);
    expect(formatted.message).toBe('This task was changed elsewhere. Refresh and try again.');
    expect(formatted.code).toBe('CONCURRENCY_CONFLICT');
    expect(formatted.requestId).toBe('req-lock-456');
  });

  it('formats network failure error', () => {
    const error = new ApiClientError({
      timestamp: new Date().toISOString(),
      status: 0,
      code: 'NETWORK_FAILURE',
      message: 'Unable to connect to TaskFlow Pro backend',
      path: '/api/tasks',
    });

    const formatted = formatApiError(error);
    expect(formatted.message).toBe('Unable to connect to TaskFlow Pro backend. Check network connectivity.');
  });
});
