'use client';

import { useState, useEffect, useCallback } from 'react';
import { Project, CreateProjectRequest } from '@/types';
import { projectApi, ApiClientError } from '@/lib/api';

export function useProjects(initialProjectId?: string) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(initialProjectId || null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchProjects = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await projectApi.getProjects();
      setProjects(data);

      if (data.length > 0) {
        if (!selectedProjectId || !data.some(p => p.id === selectedProjectId)) {
          setSelectedProjectId(data[0].id);
        }
      } else {
        setSelectedProjectId(null);
      }
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        setError(err.message);
      } else {
        setError('Failed to load projects');
      }
    } finally {
      setLoading(false);
    }
  }, [selectedProjectId]);

  useEffect(() => {
    fetchProjects();
  }, [fetchProjects]);

  const createProject = async (request: CreateProjectRequest): Promise<Project> => {
    const created = await projectApi.createProject(request);
    setProjects(prev => [...prev, created]);
    setSelectedProjectId(created.id);
    return created;
  };

  const selectedProject = projects.find(p => p.id === selectedProjectId) || null;

  return {
    projects,
    selectedProjectId,
    selectedProject,
    setSelectedProjectId,
    loading,
    error,
    refreshProjects: fetchProjects,
    createProject,
  };
}
