import { motion } from 'framer-motion';
import { cn, getHealthDotClass } from '@/lib/utils';
import type { HealthStatus } from '@/types';

interface StatusDotProps {
  status: HealthStatus;
  size?: 'sm' | 'md' | 'lg';
  pulse?: boolean;
  className?: string;
}

export function StatusDot({ status, size = 'md', pulse = true, className }: StatusDotProps) {
  const sizeClasses = {
    sm: 'w-1.5 h-1.5',
    md: 'w-2 h-2',
    lg: 'w-3 h-3',
  };

  const shouldPulse = pulse && (status === 'CRITICAL' || status === 'DOWN');

  return (
    <motion.div
      className={cn(
        'rounded-full',
        sizeClasses[size],
        getHealthDotClass(status),
        className
      )}
      animate={shouldPulse ? {
        scale: [1, 1.2, 1],
      } : {}}
      transition={{
        duration: 1.5,
        repeat: Infinity,
        ease: 'easeInOut',
      }}
    />
  );
}

