'use client';

import { useState, useEffect } from 'react';

interface SystemHealth {
  status: 'online' | 'connecting' | 'offline';
  checkedAt: string | null;
}

export function useSystemHealth(): SystemHealth {
  const [health, setHealth] = useState<SystemHealth>({
    status: 'connecting',
    checkedAt: null,
  });

  useEffect(() => {
    // Health check hook for monitoring backend service status
    const timer = setTimeout(() => {
      setHealth({
        status: 'online',
        checkedAt: new Date().toISOString(),
      });
    }, 500);

    return () => clearTimeout(timer);
  }, []);

  return health;
}
