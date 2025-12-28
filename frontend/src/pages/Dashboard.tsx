import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import {
  Server,
  Activity,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Clock,
  TrendingUp,
  TrendingDown,
  Zap,
  Shield,
  ArrowRight,
  Cpu,
  MemoryStick,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, Badge, StatusDot } from '@/components/ui';
import { useDashboardStore } from '@/lib/store';
import api from '@/lib/api';
import {
  cn,
  formatRelativeTime,
  getHealthColor,
  getSeverityColor,
  getActionColor,
} from '@/lib/utils';
import type { Service, Incident, AuditLog } from '@/types';

const containerVariants = {
  hidden: {},
  visible: {
    transition: {
      staggerChildren: 0.05,
    },
  },
};

const itemVariants = {
  hidden: {},
  visible: {},
};

export function Dashboard() {
  const { dashboard, setDashboard } = useDashboardStore();

  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: api.getDashboard,
    refetchInterval: 10000,
  });

  const { data: services } = useQuery({
    queryKey: ['services'],
    queryFn: api.getServices,
    refetchInterval: 10000,
  });

  useEffect(() => {
    if (data) {
      setDashboard(data);
    }
  }, [data, setDashboard]);

  const stats = dashboard || data;

  if (isLoading && !stats) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-obsidian-400">Loading dashboard...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-obsidian-100">Dashboard</h1>
          <p className="text-obsidian-400 mt-1">Real-time system overview</p>
        </div>
        <div className="flex items-center gap-2 text-sm text-obsidian-400">
          <motion.div
            animate={{ rotate: [0, 360] }}
            transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
            whileHover={{ scale: 1.3, rotate: 0 }}
          >
            <Clock className="w-4 h-4 text-obsidian-400" style={{ strokeWidth: 2.5 }} />
          </motion.div>
          Last updated: {formatRelativeTime(new Date())}
        </div>
      </div>

      {/* Main Stats */}
      <motion.div variants={itemVariants} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Services"
          value={stats?.totalServices || 0}
          icon={<Server className="w-5 h-5" style={{ strokeWidth: 2.5 }} />}
          color="violet"
          subtitle={`${stats?.backendServices || 0} backend · ${stats?.frontendServices || 0} frontend`}
        />
        <StatCard
          title="Healthy"
          value={stats?.healthyCount || 0}
          icon={<CheckCircle className="w-5 h-5" style={{ strokeWidth: 2.5 }} />}
          color="cyan"
          trend={stats?.healthyCount ? ((stats.healthyCount / (stats.totalServices || 1)) * 100).toFixed(0) + '%' : '0%'}
          trendUp={true}
        />
        <StatCard
          title="Active Incidents"
          value={stats?.activeIncidents || 0}
          icon={<AlertTriangle className="w-5 h-5" style={{ strokeWidth: 2.5 }} />}
          color="rose"
          subtitle={`${stats?.criticalIncidents || 0} critical`}
          alert={stats?.criticalIncidents ? stats.criticalIncidents > 0 : false}
        />
        <StatCard
          title="Avg Stability"
          value={`${(stats?.averageStabilityScore || 0).toFixed(0)}%`}
          icon={<Activity className="w-5 h-5" style={{ strokeWidth: 2.5 }} />}
          color="amber"
          trend={stats?.highRiskServices ? `${stats.highRiskServices} at risk` : 'All stable'}
          trendUp={(stats?.highRiskServices || 0) === 0}
        />
      </motion.div>

      {/* Health Distribution & Services Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Health Overview */}
        <motion.div variants={itemVariants} className="lg:col-span-1">
          <Card className="h-full">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <motion.div
                  whileHover={{ scale: 1.2, rotate: 15 }}
                  whileTap={{ scale: 0.9 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 17 }}
                >
                  <Activity className="w-5 h-5 text-emerald-400" style={{ strokeWidth: 2.5 }} />
                </motion.div>
                Health Overview
              </CardTitle>
            </CardHeader>
            <div className="space-y-4">
              <HealthBar
                label="Healthy"
                count={stats?.healthyCount || 0}
                total={stats?.totalServices || 1}
                color="emerald"
              />
              <HealthBar
                label="Degraded"
                count={stats?.degradedCount || 0}
                total={stats?.totalServices || 1}
                color="amber"
              />
              <HealthBar
                label="Critical"
                count={stats?.criticalCount || 0}
                total={stats?.totalServices || 1}
                color="rose"
              />
              <HealthBar
                label="Down"
                count={stats?.downCount || 0}
                total={stats?.totalServices || 1}
                color="red"
              />
              <HealthBar
                label="Unknown"
                count={stats?.unknownCount || 0}
                total={stats?.totalServices || 1}
                color="gray"
              />
            </div>
          </Card>
        </motion.div>

        {/* Services Grid */}
        <motion.div variants={itemVariants} className="lg:col-span-2">
          <Card className="h-full">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <motion.div
                  whileHover={{ scale: 1.2, rotate: 15 }}
                  whileTap={{ scale: 0.9 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 17 }}
                >
                  <Server className="w-5 h-5 text-cyan-400" style={{ strokeWidth: 2.5 }} />
                </motion.div>
                Services Overview
              </CardTitle>
              <Link
                to="/services"
                className="text-sm text-emerald-400 hover:text-emerald-300 flex items-center gap-1"
              >
                View all{' '}
                <motion.div
                  whileHover={{ x: 5, scale: 1.2 }}
                  whileTap={{ scale: 0.9 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 17 }}
                >
                  <ArrowRight className="w-4 h-4" style={{ strokeWidth: 2.5 }} />
                </motion.div>
              </Link>
            </CardHeader>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
              {(services || []).filter((service: Service) => service.isRunning).slice(0, 6).map((service: Service) => (
                <ServiceMiniCard key={service.id} service={service} />
              ))}
            </div>
          </Card>
        </motion.div>
      </div>

      {/* Active Incidents & Recent Actions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Active Incidents */}
        <motion.div variants={itemVariants}>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <motion.div
                  animate={{ rotate: [0, -5, 5, -5, 0] }}
                  transition={{ duration: 2, repeat: Infinity }}
                  whileHover={{ scale: 1.2, rotate: 0 }}
                  whileTap={{ scale: 0.9 }}
                >
                  <AlertTriangle className="w-5 h-5 text-rose-400" style={{ strokeWidth: 2.5 }} />
                </motion.div>
                Active Incidents
              </CardTitle>
              <Link
                to="/incidents"
                className="text-sm text-emerald-400 hover:text-emerald-300 flex items-center gap-1"
              >
                View all{' '}
                <motion.div
                  whileHover={{ x: 5, scale: 1.2 }}
                  whileTap={{ scale: 0.9 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 17 }}
                >
                  <ArrowRight className="w-4 h-4" style={{ strokeWidth: 2.5 }} />
                </motion.div>
              </Link>
            </CardHeader>
            <div className="space-y-3">
              {(stats?.activeIncidentsList || []).length === 0 ? (
                <div className="flex flex-col items-center justify-center py-8 text-obsidian-400">
                  <motion.div
                    animate={{ scale: [1, 1.1, 1] }}
                    transition={{ duration: 2, repeat: Infinity }}
                    whileHover={{ scale: 1.2, rotate: 360 }}
                  >
                    <CheckCircle className="w-12 h-12 mb-2 text-emerald-400" style={{ strokeWidth: 2.5 }} />
                  </motion.div>
                  <p>No active incidents</p>
                </div>
              ) : (
                (stats?.activeIncidentsList || []).slice(0, 5).map((incident: Incident) => (
                  <IncidentRow key={incident.id} incident={incident} />
                ))
              )}
            </div>
          </Card>
        </motion.div>

        {/* Recent Actions */}
        <motion.div variants={itemVariants}>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <motion.div
                  whileHover={{ scale: 1.2, rotate: 15 }}
                  whileTap={{ scale: 0.9 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 17 }}
                >
                  <Shield className="w-5 h-5 text-violet-400" style={{ strokeWidth: 2.5 }} />
                </motion.div>
                Recent Actions
              </CardTitle>
              <Link
                to="/audit"
                className="text-sm text-emerald-400 hover:text-emerald-300 flex items-center gap-1"
              >
                View all{' '}
                <motion.div
                  whileHover={{ x: 5, scale: 1.2 }}
                  whileTap={{ scale: 0.9 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 17 }}
                >
                  <ArrowRight className="w-4 h-4" style={{ strokeWidth: 2.5 }} />
                </motion.div>
              </Link>
            </CardHeader>
            <div className="space-y-3">
              {(stats?.recentActions || []).length === 0 ? (
                <div className="flex flex-col items-center justify-center py-8 text-obsidian-400">
                  <motion.div
                    animate={{ rotate: [0, 360] }}
                    transition={{ duration: 10, repeat: Infinity, ease: 'linear' }}
                    whileHover={{ scale: 1.2 }}
                  >
                    <Activity className="w-12 h-12 mb-2 text-obsidian-400" style={{ strokeWidth: 2.5 }} />
                  </motion.div>
                  <p>No recent actions</p>
                </div>
              ) : (
                (stats?.recentActions || []).slice(0, 5).map((action: AuditLog) => (
                  <ActionRow key={action.id} action={action} />
                ))
              )}
            </div>
          </Card>
        </motion.div>
      </div>
    </div>
  );
}

// Stat Card Component - Matches Diagnostics aesthetic
function StatCard({
  title,
  value,
  icon,
  color,
  subtitle,
  trend,
  trendUp,
  alert,
}: {
  title: string;
  value: number | string;
  icon: React.ReactNode;
  color: 'cyan' | 'emerald' | 'rose' | 'violet' | 'amber';
  subtitle?: string;
  trend?: string;
  trendUp?: boolean;
  alert?: boolean;
}) {
  const colorClasses = {
    violet: 'from-violet-500/20 to-violet-500/5 border-l-violet-500',
    cyan: 'from-cyan-500/20 to-cyan-500/5 border-l-cyan-500',
    emerald: 'from-emerald-500/20 to-emerald-500/5 border-l-emerald-500',
    rose: 'from-rose-500/20 to-rose-500/5 border-l-rose-500',
    amber: 'from-amber-500/20 to-amber-500/5 border-l-amber-500',
  };

  const iconColors = {
    violet: 'text-violet-400',
    cyan: 'text-cyan-400',
    emerald: 'text-emerald-400',
    rose: 'text-rose-400',
    amber: 'text-amber-400',
  };

  return (
    <motion.div
      whileHover={{ scale: 1.02, y: -2 }}
      className={cn(
        'relative overflow-hidden rounded-xl p-5',
        'bg-gradient-to-br border-l-4 border border-obsidian-800/50',
        colorClasses[color],
        alert && 'animate-pulse'
      )}
    >
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-obsidian-400 font-medium">{title}</p>
          <p className="text-3xl font-bold text-obsidian-100 mt-1">{value}</p>
          {subtitle && (
            <p className="text-xs text-obsidian-500 mt-1">{subtitle}</p>
          )}
          {trend && (
            <div className="flex items-center gap-1 mt-2">
              {trendUp ? (
                <motion.div
                  animate={{ y: [0, -3, 0] }}
                  transition={{ duration: 1.5, repeat: Infinity }}
                  whileHover={{ scale: 1.3 }}
                >
                  <TrendingUp className="w-3 h-3 text-emerald-400" style={{ strokeWidth: 2.5 }} />
                </motion.div>
              ) : (
                <motion.div
                  animate={{ y: [0, 3, 0] }}
                  transition={{ duration: 1.5, repeat: Infinity }}
                  whileHover={{ scale: 1.3 }}
                >
                  <TrendingDown className="w-3 h-3 text-rose-400" style={{ strokeWidth: 2.5 }} />
                </motion.div>
              )}
              <span className={cn('text-xs', trendUp ? 'text-emerald-400' : 'text-rose-400')}>
                {trend}
              </span>
            </div>
          )}
        </div>
        <motion.div
          className={cn('p-2.5 rounded-lg bg-obsidian-900/60', iconColors[color])}
          whileHover={{ scale: 1.15, rotate: [0, -10, 10, -10, 0] }}
          whileTap={{ scale: 0.95 }}
          transition={{ type: 'spring', stiffness: 400, damping: 17 }}
        >
          {icon}
        </motion.div>
      </div>
    </motion.div>
  );
}

// Health Bar Component
function HealthBar({
  label,
  count,
  total,
  color,
}: {
  label: string;
  count: number;
  total: number;
  color: 'emerald' | 'amber' | 'rose' | 'red' | 'gray';
}) {
  const percentage = total > 0 ? (count / total) * 100 : 0;

  const colors = {
    emerald: 'bg-emerald-500',
    amber: 'bg-amber-500',
    rose: 'bg-rose-500',
    red: 'bg-red-600',
    gray: 'bg-obsidian-600',
  };

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between text-sm">
        <span className="text-obsidian-300">{label}</span>
        <span className="text-obsidian-200 font-medium">{count}</span>
      </div>
      <div className="h-2 bg-obsidian-800 rounded-full overflow-hidden">
        <motion.div
          className={cn('h-full rounded-full', colors[color])}
          initial={{ width: 0 }}
          animate={{ width: `${percentage}%` }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
        />
      </div>
    </div>
  );
}

// Service Mini Card Component
function ServiceMiniCard({ service }: { service: Service }) {
  return (
    <Link
      to={`/services/${service.id}`}
      className="block p-3 bg-obsidian-800/50 hover:bg-obsidian-800 border border-obsidian-700/50 rounded-lg transition-all group"
    >
      <div className="flex items-center gap-2 mb-2">
        <StatusDot status={service.healthStatus} size="sm" />
        <span className="text-sm font-medium text-obsidian-200 truncate group-hover:text-emerald-400 transition-colors">
          {service.name}
        </span>
      </div>
        <div className="flex items-center gap-3 text-xs text-obsidian-300">
        <motion.div
          className="flex items-center gap-1"
          whileHover={{ scale: 1.2 }}
          transition={{ type: 'spring', stiffness: 400, damping: 17 }}
        >
          <Cpu className="w-3 h-3 text-cyan-400" style={{ strokeWidth: 2.5 }} />
          {service.cpuUsage?.toFixed(0) || '-'}%
        </motion.div>
        <motion.div
          className="flex items-center gap-1"
          whileHover={{ scale: 1.2 }}
          transition={{ type: 'spring', stiffness: 400, damping: 17 }}
        >
          <MemoryStick className="w-3 h-3 text-violet-400" style={{ strokeWidth: 2.5 }} />
          {service.memoryUsage?.toFixed(0) || '-'}%
        </motion.div>
      </div>
    </Link>
  );
}

// Incident Row Component
function IncidentRow({ incident }: { incident: Incident }) {
  return (
    <Link
      to={`/incidents/${incident.id}`}
      className="flex items-center gap-3 p-3 bg-obsidian-800/50 hover:bg-obsidian-800 border border-obsidian-700/50 rounded-lg transition-all group"
    >
      <div className={cn('w-2 h-2 rounded-full', incident.severity === 'CRITICAL' ? 'bg-rose-500 animate-pulse' : 'bg-amber-500')} />
      <div className="flex-1 min-w-0">
        <p className="text-sm text-obsidian-200 truncate group-hover:text-emerald-400 transition-colors">
          {incident.title}
        </p>
        <p className="text-xs text-obsidian-400">{incident.serviceName || `Service #${incident.serviceId}`}</p>
      </div>
      <Badge className={getSeverityColor(incident.severity)} size="sm">
        {incident.severity}
      </Badge>
    </Link>
  );
}

// Action Row Component
function ActionRow({ action }: { action: AuditLog }) {
  return (
    <div className="flex items-center gap-3 p-3 bg-obsidian-800/50 border border-obsidian-700/50 rounded-lg">
      <motion.div
        className={cn('p-1.5 rounded-lg bg-obsidian-900/60', getActionColor(action.action))}
        whileHover={{ scale: 1.2, rotate: 15 }}
        whileTap={{ scale: 0.9 }}
        transition={{ type: 'spring', stiffness: 400, damping: 17 }}
      >
        <Zap className="w-3 h-3" style={{ strokeWidth: 2.5 }} />
      </motion.div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-obsidian-200 truncate">
          <span className="font-medium">{action.action}</span>
          {action.serviceName && ` on ${action.serviceName}`}
        </p>
        <p className="text-xs text-obsidian-400">
          {action.username} · {formatRelativeTime(action.timestamp)}
        </p>
      </div>
      <Badge variant={action.status === 'SUCCESS' ? 'success' : 'danger'} size="sm">
        {action.status}
      </Badge>
    </div>
  );
}
