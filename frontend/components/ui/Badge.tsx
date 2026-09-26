import React from 'react';
import { cn } from '@/lib/utils/cn';

export type BadgeVariant = 'ready' | 'blocked' | 'warning' | 'success' | 'neutral';

interface BadgeProps {
  variant: BadgeVariant;
  children: React.ReactNode;
  className?: string;
}

const variantStyles: Record<BadgeVariant, string> = {
  ready: 'bg-emerald-950/60 border-emerald-500/30 text-emerald-400',
  blocked: 'bg-rose-950/60 border-rose-500/30 text-rose-400',
  warning: 'bg-amber-950/60 border-amber-500/30 text-amber-400',
  success: 'bg-emerald-950/60 border-emerald-500/30 text-emerald-300',
  neutral: 'bg-zinc-800/60 border-zinc-700/40 text-zinc-400',
};

const dotStyles: Record<BadgeVariant, string> = {
  ready: 'bg-emerald-400',
  blocked: 'bg-rose-400',
  warning: 'bg-amber-400',
  success: 'bg-emerald-400',
  neutral: 'bg-zinc-400',
};

export const Badge: React.FC<BadgeProps> = ({ variant, children, className }) => {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border tracking-wide uppercase',
        variantStyles[variant],
        className
      )}
    >
      <span className={cn('h-1.5 w-1.5 rounded-full', dotStyles[variant])} />
      {children}
    </span>
  );
};
