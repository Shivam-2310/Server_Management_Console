import { motion, HTMLMotionProps } from 'framer-motion';
import { cn } from '@/lib/utils';
import { forwardRef } from 'react';

interface CardProps extends HTMLMotionProps<'div'> {
  variant?: 'default' | 'glass' | 'solid';
  hover?: boolean;
  glow?: 'none' | 'emerald' | 'amber' | 'rose' | 'cyan';
}

export const Card = forwardRef<HTMLDivElement, CardProps>(
  ({ className, variant = 'default', hover = false, glow = 'none', children, ...props }, ref) => {
    const variants = {
      default: 'bg-obsidian-900 border border-obsidian-800',
      glass: 'bg-obsidian-900 border border-obsidian-800',
      solid: 'bg-obsidian-900 border border-obsidian-800',
    };

    const glowClasses = {
      none: '',
      emerald: '',
      amber: '',
      rose: '',
      cyan: '',
    };

    return (
      <motion.div
        ref={ref}
        className={cn(
          'rounded-xl p-6',
          variants[variant],
          glowClasses[glow],
          hover && 'transition-all duration-300 hover:border-obsidian-700 hover:bg-obsidian-800',
          className
        )}
        initial={false}
        {...props}
      >
        {children}
      </motion.div>
    );
  }
);

Card.displayName = 'Card';

interface CardHeaderProps {
  className?: string;
  children: React.ReactNode;
}

export function CardHeader({ className, children }: CardHeaderProps) {
  return (
    <div className={cn('mb-4', className)}>
      {children}
    </div>
  );
}

interface CardTitleProps {
  className?: string;
  children: React.ReactNode;
}

export function CardTitle({ className, children }: CardTitleProps) {
  return (
    <h3 className={cn('text-lg font-semibold text-obsidian-100', className)}>
      {children}
    </h3>
  );
}

interface CardDescriptionProps {
  className?: string;
  children: React.ReactNode;
}

export function CardDescription({ className, children }: CardDescriptionProps) {
  return (
    <p className={cn('text-sm text-obsidian-400 mt-1', className)}>
      {children}
    </p>
  );
}

interface CardContentProps {
  className?: string;
  children: React.ReactNode;
}

export function CardContent({ className, children }: CardContentProps) {
  return (
    <div className={cn('', className)}>
      {children}
    </div>
  );
}
