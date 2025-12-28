import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import {
  Server,
  Search,
  Filter,
  Plus,
  Activity,
  Cpu,
  MemoryStick,
  Clock,
  ArrowUpRight,
  LayoutGrid,
  List,
  RefreshCw,
  CheckCircle,
  AlertTriangle,
  XCircle,
} from 'lucide-react';
import { Card, Button, Badge, StatusDot, Input } from '@/components/ui';
import api from '@/lib/api';
import {
  cn,
  formatRelativeTime,
  getHealthColor,
  getHealthBgColor,
  getRiskColor,
} from '@/lib/utils';
import type { Service, HealthStatus, ServiceType } from '@/types';

export function Services() {
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState<ServiceType | 'ALL'>('ALL');
  const [filterHealth, setFilterHealth] = useState<HealthStatus | 'ALL'>('ALL');

  const { data: services, isLoading, error, refetch } = useQuery({
    queryKey: ['services'],
    queryFn: () => {
      if (!api) {
        throw new Error('API client not available');
      }
      return api.getServices();
    },
    refetchInterval: 15000,
    retry: 2,
  });

  // Debug: Log services when they change
  useEffect(() => {
    if (services) {
      console.log('Services loaded:', services.length, services);
    }
    if (error) {
      console.error('Error loading services:', error);
    }
  }, [services, error]);

  const filteredServices = (services || []).filter((service: Service) => {
    // Only show services that are actually running
    if (!service.isRunning) {
      return false;
    }
    const matchesSearch = !searchQuery || service.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      service.description?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesType = filterType === 'ALL' || service.serviceType === filterType;
    const matchesHealth = filterHealth === 'ALL' || service.healthStatus === filterHealth;
    return matchesSearch && matchesType && matchesHealth;
  });

  const healthCounts = (services || []).filter((service: Service) => service.isRunning).reduce((acc: Record<string, number>, service: Service) => {
    acc[service.healthStatus] = (acc[service.healthStatus] || 0) + 1;
    return acc;
  }, {});

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-obsidian-100">Services</h1>
          <p className="text-obsidian-400 mt-1">
            {(services || []).filter((s: Service) => s.isRunning).length || 0} services running
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => refetch()}
            icon={<RefreshCw className="w-4 h-4" />}
          >
            Refresh
          </Button>
          <Link to="/services/new">
            <Button size="sm" icon={<Plus className="w-4 h-4" />}>
              Add Service
            </Button>
          </Link>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          title="Running Services"
          value={(services || []).filter((s: Service) => s.isRunning).length || 0}
          icon={<Server className="w-5 h-5" />}
          color="violet"
        />
        <StatCard
          title="Healthy"
          value={healthCounts.HEALTHY || 0}
          icon={<CheckCircle className="w-5 h-5" />}
          color="emerald"
        />
        <StatCard
          title="Degraded"
          value={(healthCounts.DEGRADED || 0) + (healthCounts.CRITICAL || 0)}
          icon={<AlertTriangle className="w-5 h-5" />}
          color="amber"
        />
        <StatCard
          title="Down"
          value={healthCounts.DOWN || 0}
          icon={<XCircle className="w-5 h-5" />}
          color="rose"
        />
      </div>

      {/* Health Quick Filters */}
      <div className="flex items-center gap-2 flex-wrap">
        {[
          { status: 'ALL', label: 'All Running', count: (services || []).filter((s: Service) => s.isRunning).length || 0 },
          { status: 'HEALTHY', label: 'Healthy', count: healthCounts.HEALTHY || 0 },
          { status: 'DEGRADED', label: 'Degraded', count: healthCounts.DEGRADED || 0 },
          { status: 'CRITICAL', label: 'Critical', count: healthCounts.CRITICAL || 0 },
          { status: 'DOWN', label: 'Down', count: healthCounts.DOWN || 0 },
        ].map(({ status, label, count }) => (
          <button
            key={status}
            onClick={() => setFilterHealth(status as HealthStatus | 'ALL')}
            className={cn(
              'px-3 py-1.5 rounded-lg text-sm font-medium transition-all',
              filterHealth === status
                ? 'bg-emerald-600 text-white border border-emerald-500'
                : 'bg-obsidian-800 text-obsidian-400 border border-obsidian-700 hover:border-obsidian-600'
            )}
          >
            {label}
            <span className="ml-1.5 text-xs opacity-70">({count})</span>
          </button>
        ))}
      </div>

      {/* Filters & Search */}
      <div className="flex flex-col md:flex-row gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-obsidian-500" />
          <input
            type="text"
            placeholder="Search services..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 placeholder:text-obsidian-500 focus:outline-none focus:border-emerald-500"
          />
        </div>
        <div className="flex items-center gap-3">
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value as ServiceType | 'ALL')}
            className="px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500"
          >
            <option value="ALL">All Types</option>
            <option value="BACKEND">Backend</option>
            <option value="FRONTEND">Frontend</option>
          </select>
          <div className="flex items-center bg-obsidian-900 border border-obsidian-800 rounded-lg">
            <button
              onClick={() => setViewMode('grid')}
              className={cn(
                'p-2.5 rounded-l-lg transition-colors',
                viewMode === 'grid' ? 'bg-emerald-600 text-white' : 'text-obsidian-400 hover:text-obsidian-200'
              )}
            >
              <LayoutGrid className="w-4 h-4" />
            </button>
            <button
              onClick={() => setViewMode('list')}
              className={cn(
                'p-2.5 rounded-r-lg transition-colors',
                viewMode === 'list' ? 'bg-emerald-600 text-white' : 'text-obsidian-400 hover:text-obsidian-200'
              )}
            >
              <List className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      {/* Services List/Grid */}
      {error ? (
        <Card className="flex flex-col items-center justify-center py-16">
          <AlertTriangle className="w-16 h-16 text-rose-500 mb-4" />
          <p className="text-rose-400 text-lg mb-2">Error loading services</p>
          <p className="text-obsidian-500 text-sm mb-4">
            {error instanceof Error ? error.message : 'Failed to fetch services'}
          </p>
          <Button onClick={() => refetch()} icon={<RefreshCw className="w-4 h-4" />}>
            Retry
          </Button>
        </Card>
      ) : isLoading ? (
        <div className="flex items-center justify-center min-h-[300px]">
          <div className="text-obsidian-400">Loading services...</div>
        </div>
      ) : filteredServices.length === 0 ? (
        <Card className="flex flex-col items-center justify-center py-16">
          <Server className="w-16 h-16 text-obsidian-700 mb-4" />
          <p className="text-obsidian-400 text-lg mb-2">No services found</p>
          <p className="text-obsidian-500 text-sm">
            {searchQuery ? 'Try adjusting your search criteria' : 'Register your first service to get started'}
          </p>
        </Card>
      ) : viewMode === 'grid' ? (
        <motion.div
          className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
          initial="hidden"
          animate="visible"
          variants={{
            visible: { transition: { staggerChildren: 0.05 } },
          }}
        >
          {filteredServices.map((service: Service) => (
            <ServiceCard key={service.id} service={service} />
          ))}
        </motion.div>
      ) : (
        <div className="space-y-2">
          {filteredServices.map((service: Service) => (
            <ServiceListItem key={service.id} service={service} />
          ))}
        </div>
      )}
    </div>
  );
}

function StatCard({
  title,
  value,
  icon,
  color,
}: {
  title: string;
  value: number;
  icon: React.ReactNode;
  color: 'violet' | 'emerald' | 'amber' | 'rose' | 'cyan';
}) {
  const colorClasses = {
    violet: 'from-violet-500/20 to-violet-500/5 border-l-violet-500',
    emerald: 'from-emerald-500/20 to-emerald-500/5 border-l-emerald-500',
    amber: 'from-amber-500/20 to-amber-500/5 border-l-amber-500',
    rose: 'from-rose-500/20 to-rose-500/5 border-l-rose-500',
    cyan: 'from-cyan-500/20 to-cyan-500/5 border-l-cyan-500',
  };

  const iconColors = {
    violet: 'text-violet-400',
    emerald: 'text-emerald-400',
    amber: 'text-amber-400',
    rose: 'text-rose-400',
    cyan: 'text-cyan-400',
  };

  return (
    <motion.div
      whileHover={{ scale: 1.02, y: -2 }}
      className={cn(
        'p-4 rounded-xl bg-gradient-to-br border-l-4 border border-obsidian-800/50',
        colorClasses[color]
      )}
    >
      <div className="flex items-center justify-between">
        <div>
          <p className="text-obsidian-400 text-sm">{title}</p>
          <p className="text-2xl font-bold text-obsidian-100 mt-1">{value}</p>
        </div>
        <div className={cn('p-2 rounded-lg bg-obsidian-900/60', iconColors[color])}>
          {icon}
        </div>
      </div>
    </motion.div>
  );
}

function ServiceCard({ service }: { service: Service }) {
  const healthColors = {
    HEALTHY: 'from-emerald-500/20 to-emerald-500/5 border-l-emerald-500',
    DEGRADED: 'from-amber-500/20 to-amber-500/5 border-l-amber-500',
    CRITICAL: 'from-rose-500/20 to-rose-500/5 border-l-rose-500',
    DOWN: 'from-rose-600/20 to-rose-600/5 border-l-rose-600',
    UNKNOWN: 'from-obsidian-600/20 to-obsidian-600/5 border-l-obsidian-600',
  };

  return (
    <motion.div
      variants={{
        hidden: { opacity: 0, y: 20 },
        visible: { opacity: 1, y: 0 },
      }}
    >
      <Link to={`/services/${service.id}`}>
        <motion.div
          whileHover={{ scale: 1.02, y: -2 }}
          className={cn(
            'relative overflow-hidden rounded-xl p-5 bg-gradient-to-br border-l-4 border border-obsidian-800/50 group transition-all',
            healthColors[service.healthStatus as keyof typeof healthColors] || healthColors.UNKNOWN
          )}
        >
          {/* Status Indicator */}
          <div className="absolute top-4 right-4">
            <StatusDot status={service.healthStatus} size="lg" />
          </div>

          {/* Content */}
          <div className="space-y-4">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Server className="w-5 h-5 text-obsidian-500" />
                <Badge variant="outline" size="sm">{service.serviceType}</Badge>
              </div>
              <h3 className="text-lg font-semibold text-obsidian-100 group-hover:text-emerald-400 transition-colors">
                {service.name}
              </h3>
              <p className="text-sm text-obsidian-500 line-clamp-2 mt-1">
                {service.description || `${service.host}:${service.port}`}
              </p>
            </div>

            {/* Metrics */}
            <div className="grid grid-cols-3 gap-4">
              <MetricItem
                icon={<Cpu className="w-3.5 h-3.5" />}
                label="CPU"
                value={service.cpuUsage != null ? `${service.cpuUsage.toFixed(0)}%` : '-'}
              />
              <MetricItem
                icon={<MemoryStick className="w-3.5 h-3.5" />}
                label="Memory"
                value={service.memoryUsage != null ? `${service.memoryUsage.toFixed(0)}%` : '-'}
              />
              <MetricItem
                icon={<Activity className="w-3.5 h-3.5" />}
                label="Stability"
                value={`${service.stabilityScore}%`}
                valueClass={getRiskColor(100 - service.stabilityScore)}
              />
            </div>

            {/* Footer */}
            <div className="flex items-center justify-between pt-3 border-t border-obsidian-800/50">
              <div className="flex items-center gap-1 text-xs text-obsidian-500">
                <Clock className="w-3 h-3" />
                {service.lastHealthCheck ? formatRelativeTime(service.lastHealthCheck) : 'Never'}
              </div>
              <div className="flex items-center gap-1 text-emerald-400 text-sm opacity-0 group-hover:opacity-100 transition-opacity">
                View details <ArrowUpRight className="w-4 h-4" />
              </div>
            </div>
          </div>
        </motion.div>
      </Link>
    </motion.div>
  );
}

function ServiceListItem({ service }: { service: Service }) {
  const healthColors = {
    HEALTHY: 'from-emerald-500/20 to-emerald-500/5 border-l-emerald-500',
    DEGRADED: 'from-amber-500/20 to-amber-500/5 border-l-amber-500',
    CRITICAL: 'from-rose-500/20 to-rose-500/5 border-l-rose-500',
    DOWN: 'from-rose-600/20 to-rose-600/5 border-l-rose-600',
    UNKNOWN: 'from-obsidian-600/20 to-obsidian-600/5 border-l-obsidian-600',
  };

  return (
    <Link to={`/services/${service.id}`}>
      <motion.div
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
        whileHover={{ scale: 1.01, x: 4 }}
        className={cn(
          'flex items-center gap-4 p-4 rounded-xl bg-gradient-to-br border-l-4 border border-obsidian-800/50 transition-all group',
          healthColors[service.healthStatus as keyof typeof healthColors] || healthColors.UNKNOWN
        )}
      >
        <StatusDot status={service.healthStatus} size="md" />
        
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="font-medium text-obsidian-100 group-hover:text-emerald-400 transition-colors truncate">
              {service.name}
            </h3>
            <Badge variant="outline" size="sm">{service.serviceType}</Badge>
          </div>
          <p className="text-sm text-obsidian-500 truncate">
            {service.host}:{service.port}
          </p>
        </div>

        <div className="hidden md:flex items-center gap-6 text-sm">
          <div className="text-center">
            <p className="text-obsidian-500 text-xs">CPU</p>
            <p className="text-obsidian-200 font-medium">
              {service.cpuUsage != null ? `${service.cpuUsage.toFixed(0)}%` : '-'}
            </p>
          </div>
          <div className="text-center">
            <p className="text-obsidian-500 text-xs">Memory</p>
            <p className="text-obsidian-200 font-medium">
              {service.memoryUsage != null ? `${service.memoryUsage.toFixed(0)}%` : '-'}
            </p>
          </div>
          <div className="text-center">
            <p className="text-obsidian-500 text-xs">Stability</p>
            <p className={cn('font-medium', getRiskColor(100 - service.stabilityScore))}>
              {service.stabilityScore}%
            </p>
          </div>
        </div>

        <ArrowUpRight className="w-5 h-5 text-obsidian-500 group-hover:text-emerald-400 transition-colors" />
      </motion.div>
    </Link>
  );
}

function MetricItem({
  icon,
  label,
  value,
  valueClass,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  valueClass?: string;
}) {
  return (
    <div className="text-center">
      <div className="flex items-center justify-center gap-1 text-obsidian-500 mb-1">
        {icon}
        <span className="text-xs">{label}</span>
      </div>
      <p className={cn('text-sm font-semibold text-obsidian-200', valueClass)}>{value}</p>
    </div>
  );
}
