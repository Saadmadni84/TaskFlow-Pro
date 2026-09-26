import React from 'react';
import { render, screen } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { Header } from '@/components/layout/Header';
import { usePathname } from 'next/navigation';

vi.mock('next/navigation', () => ({
  usePathname: vi.fn(),
}));

describe('Header & Navigation Polish', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders a clickable brand home link pointing to "/" with accessible label', () => {
    (usePathname as unknown as ReturnType<typeof vi.fn>).mockReturnValue('/kanban');
    render(<Header />);

    const brandLink = screen.getByRole('link', { name: /TaskFlow Pro home/i });
    expect(brandLink).toBeInTheDocument();
    expect(brandLink).toHaveAttribute('href', '/');
    expect(brandLink).toHaveAttribute('aria-label', 'TaskFlow Pro home');
    expect(brandLink.className).toContain('cursor-pointer');
    expect(brandLink.className).toContain('focus-visible:ring-1');
  });

  it('displays breadcrumb context and secondary phase indicator for Kanban Board', () => {
    (usePathname as unknown as ReturnType<typeof vi.fn>).mockReturnValue('/kanban');
    render(<Header />);

    expect(screen.getByText('Production Kanban Board')).toBeInTheDocument();
    expect(screen.getByText('Phase 9')).toBeInTheDocument();
  });

  it('displays breadcrumb context and secondary phase indicator for Critical Path Analysis', () => {
    (usePathname as unknown as ReturnType<typeof vi.fn>).mockReturnValue('/critical-path');
    render(<Header />);

    expect(screen.getByText('Critical Path Analysis')).toBeInTheDocument();
    expect(screen.getByText('Phase 8')).toBeInTheDocument();
  });

  it('displays breadcrumb context for Scheduling Engine', () => {
    (usePathname as unknown as ReturnType<typeof vi.fn>).mockReturnValue('/scheduling');
    render(<Header />);

    expect(screen.getByText('Dependency-Aware Scheduling Engine')).toBeInTheDocument();
    expect(screen.getByText('Phase 5')).toBeInTheDocument();
  });

  it('displays breadcrumb context for Visual DAG Graph', () => {
    (usePathname as unknown as ReturnType<typeof vi.fn>).mockReturnValue('/graph');
    render(<Header />);

    expect(screen.getByText('Visual DAG Graph')).toBeInTheDocument();
    expect(screen.getByText('Phase 9')).toBeInTheDocument();
  });

  it('displays breadcrumb context for root Architecture & Foundation page', () => {
    (usePathname as unknown as ReturnType<typeof vi.fn>).mockReturnValue('/');
    render(<Header />);

    const brandLink = screen.getByRole('link', { name: /TaskFlow Pro home/i });
    expect(brandLink).toBeInTheDocument();
    expect(brandLink).toHaveAttribute('href', '/');
    expect(screen.getByText('Architecture & Foundation')).toBeInTheDocument();
    expect(screen.getByText('Phase 1')).toBeInTheDocument();
  });

  it('is keyboard accessible and allows focusing the brand link', () => {
    (usePathname as unknown as ReturnType<typeof vi.fn>).mockReturnValue('/domain-model');
    render(<Header />);

    const brandLink = screen.getByRole('link', { name: /TaskFlow Pro home/i });
    brandLink.focus();
    expect(document.activeElement).toBe(brandLink);
  });
});
