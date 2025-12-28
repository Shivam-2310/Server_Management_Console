import { forwardRef } from 'react';
import { motion, HTMLMotionProps } from 'framer-motion';
import { cn } from '@/lib/utils';
import { Loader2 } from 'lucide-react';

interface ButtonProps extends Omit<HTMLMotionProps<'button'>, 'children'> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  icon?: React.ReactNode;
  children?: React.ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', loading = false, icon, children, disabled, ...props }, ref) => {
    const variants = {
      primary: 'bg-emerald-600 hover:bg-emerald-500 text-white border border-emerald-500',
      secondary: 'bg-obsidian-700 hover:bg-obsidian-600 text-obsidian-100 border-2 border-obsidian-600',
      danger: 'bg-rose-600 hover:bg-rose-500 text-white border border-rose-500',
      ghost: 'bg-obsidian-800 hover:bg-obsidian-700 text-obsidian-200 hover:text-white border border-obsidian-700',
      outline: 'bg-obsidian-900 border-2 border-obsidian-600 hover:border-obsidian-500 text-obsidian-200 hover:text-white',
    };

    const sizes = {
      sm: 'px-3 py-1.5 text-sm gap-1.5',
      md: 'px-4 py-2 text-sm gap-2',
      lg: 'px-6 py-3 text-base gap-2',
    };

    return (
      <motion.button
        ref={ref}
        className={cn(
          'inline-flex items-center justify-center font-medium rounded-lg transition-all duration-200',
          'focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-obsidian-900',
          variant === 'primary' && 'focus:ring-emerald-500',
          variant === 'danger' && 'focus:ring-rose-500',
          variants[variant],
          sizes[size],
          (disabled || loading) && 'cursor-not-allowed',
          className
        )}
        disabled={disabled || loading}
        whileHover={{ scale: disabled || loading ? 1 : 1.02 }}
        whileTap={{ scale: disabled || loading ? 1 : 0.98 }}
        {...props}
      >
        {loading ? (
          <Loader2 className="w-4 h-4 animate-spin" />
        ) : icon ? (
          <motion.div
            whileHover={{ scale: 1.1, rotate: 5 }}
            whileTap={{ scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 400, damping: 17 }}
          >
            {icon}
          </motion.div>
        ) : null}
        {children}
      </motion.button>
    );
  }
);

Button.displayName = 'Button';
