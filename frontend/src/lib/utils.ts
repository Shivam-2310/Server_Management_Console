import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { format, formatDistanceToNow } from 'date-fns';
import type { HealthStatus, IncidentSeverity, ServiceAction } from '@/types';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDate(date: string | Date): string {
  return format(new Date(date), 'MMM dd, yyyy HH:mm');
}

export function formatRelativeTime(date: string | Date): string {
  return formatDistanceToNow(new Date(date), { addSuffix: true });
}

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
}

export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  if (ms < 3600000) return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`;
  return `${Math.floor(ms / 3600000)}h ${Math.floor((ms % 3600000) / 60000)}m`;
}

export function formatUptime(seconds: number): string {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

export function getHealthColor(status: HealthStatus): string {
  switch (status) {
    case 'HEALTHY':
      return 'text-emerald-400';
    case 'DEGRADED':
      return 'text-amber-400';
    case 'CRITICAL':
      return 'text-rose-400';
    case 'DOWN':
      return 'text-rose-500';
    case 'UNKNOWN':
    default:
      return 'text-obsidian-400';
  }
}

export function getHealthBgColor(status: HealthStatus): string {
  switch (status) {
    case 'HEALTHY':
      return 'bg-emerald-600 border-emerald-500';
    case 'DEGRADED':
      return 'bg-amber-600 border-amber-500';
    case 'CRITICAL':
      return 'bg-rose-600 border-rose-500';
    case 'DOWN':
      return 'bg-rose-700 border-rose-600';
    case 'UNKNOWN':
    default:
      return 'bg-obsidian-600 border-obsidian-500';
  }
}

export function getHealthDotClass(status: HealthStatus): string {
  switch (status) {
    case 'HEALTHY':
      return 'status-healthy';
    case 'DEGRADED':
      return 'status-degraded';
    case 'CRITICAL':
      return 'status-critical';
    case 'DOWN':
      return 'status-down';
    case 'UNKNOWN':
    default:
      return 'status-unknown';
  }
}

export function getSeverityColor(severity: IncidentSeverity): string {
  switch (severity) {
    case 'CRITICAL':
      return 'text-white bg-rose-600 border-rose-500';
    case 'HIGH':
      return 'text-white bg-orange-600 border-orange-500';
    case 'MEDIUM':
      return 'text-white bg-amber-600 border-amber-500';
    case 'LOW':
      return 'text-white bg-cyan-600 border-cyan-500';
    default:
      return 'text-white bg-obsidian-600 border-obsidian-500';
  }
}

export function getActionColor(action: ServiceAction): string {
  switch (action) {
    case 'START':
      return 'text-emerald-400';
    case 'STOP':
      return 'text-rose-400';
    case 'RESTART':
      return 'text-amber-400';
    case 'SCALE_UP':
      return 'text-cyan-400';
    case 'SCALE_DOWN':
      return 'text-violet-400';
    default:
      return 'text-obsidian-400';
  }
}

export function getRiskColor(score: number): string {
  if (score >= 70) return 'text-rose-400';
  if (score >= 40) return 'text-amber-400';
  return 'text-emerald-400';
}

export function getStabilityColor(score: number): string {
  if (score >= 80) return 'text-emerald-400';
  if (score >= 50) return 'text-amber-400';
  return 'text-rose-400';
}

export function truncate(str: string, length: number): string {
  if (str.length <= length) return str;
  return str.slice(0, length) + '...';
}

export function capitalizeFirst(str: string): string {
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

export function slugify(str: string): string {
  return str
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '');
}
