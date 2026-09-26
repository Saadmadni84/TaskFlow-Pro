import React from 'react';
import { cn } from '@/lib/utils/cn';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({ className, children, ...props }) => {
  return (
    <div
      className={cn(
        'rounded-lg border border-zinc-800/80 bg-zinc-900/60 backdrop-blur-sm p-5 shadow-sm',
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
};
