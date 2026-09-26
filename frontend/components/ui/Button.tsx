import React from 'react';
import { cn } from '@/lib/utils/cn';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
}

export const Button: React.FC<ButtonProps> = ({
  variant = 'secondary',
  size = 'md',
  className,
  children,
  ...props
}) => {
  const baseStyles = 'inline-flex items-center justify-center font-medium transition-colors focus:outline-none focus:ring-1 focus:ring-zinc-400 disabled:opacity-50 disabled:pointer-events-none rounded-md';

  const sizeStyles = {
    sm: 'text-xs px-2.5 py-1.5 h-8',
    md: 'text-sm px-3.5 py-2 h-9',
    lg: 'text-sm px-4 py-2.5 h-10',
  };

  const variantStyles = {
    primary: 'bg-zinc-100 text-zinc-900 hover:bg-white active:bg-zinc-200',
    secondary: 'bg-zinc-900 text-zinc-100 border border-zinc-800 hover:bg-zinc-800/80 active:bg-zinc-800',
    ghost: 'text-zinc-400 hover:text-zinc-100 hover:bg-zinc-800/50',
    danger: 'bg-rose-950/50 text-rose-300 border border-rose-800/40 hover:bg-rose-900/60',
  };

  return (
    <button
      className={cn(baseStyles, sizeStyles[size], variantStyles[variant], className)}
      {...props}
    >
      {children}
    </button>
  );
};
