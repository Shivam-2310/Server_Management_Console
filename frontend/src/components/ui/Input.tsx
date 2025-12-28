import { forwardRef, InputHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  icon?: React.ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, icon, type = 'text', ...props }, ref) => {
    return (
      <div className="w-full">
        {label && (
          <label className="block text-sm font-medium text-obsidian-300 mb-2">
            {label}
          </label>
        )}
        <div className="relative">
          {icon && (
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-obsidian-500">
              {icon}
            </div>
          )}
          <input
            type={type}
            className={cn(
              'w-full px-4 py-3 bg-obsidian-900/50 border border-obsidian-700/50 rounded-lg',
              'text-obsidian-100 placeholder:text-obsidian-500',
              'focus:outline-none focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/30',
              'transition-all duration-200',
              icon && 'pl-10',
              error && 'border-rose-500/50 focus:border-rose-500/50 focus:ring-rose-500/30',
              className
            )}
            ref={ref}
            {...props}
          />
        </div>
        {error && (
          <p className="mt-1.5 text-sm text-rose-400">{error}</p>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';

