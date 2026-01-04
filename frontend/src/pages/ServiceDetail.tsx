import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Server,
  Play,
  Square,
  RefreshCw,
  Activity,
  Cpu,
  MemoryStick,
  Clock,
  Zap,
  AlertTriangle,
  ChevronLeft,
  ExternalLink,
  Sparkles,
  Trash2,
  Settings,
  FileText,
  Database,
  HardDrive,
  Search,
  Filter,
  X,
  AlertCircle,
  Info,
  Bug,
  Timer,
  Layers,
  Gauge,
} from 'lucide-react';
import {
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Area,
  AreaChart,
} from 'recharts';
import { Card, CardHeader, CardTitle, Button, Badge, StatusDot, Modal } from '@/components/ui';
import api from '@/lib/api';
import { useAuthStore } from '@/lib/store';
import {
  cn,
  formatRelativeTime,
  getRiskColor,
  formatBytes,
  formatDuration,
} from '@/lib/utils';
import type { Metrics } from '@/types';

export function ServiceDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuthStore();
  const serviceId = Number(id);

  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [activeTab, setActiveTab] = useState<'overview' | 'logs' | 'config' | 'infrastructure'>('overview');
  const [pendingAction, setPendingAction] = useState<{
    action: 'stop' | 'restart' | 'delete';
    reason: string;
  } | null>(null);
  
  // Log filters
  const [logLevel, setLogLevel] = useState<string>('ALL');
  const [logSearchQuery, setLogSearchQuery] = useState<string>('');
  const [logLines, setLogLines] = useState<number>(100);
  
  // Configuration filters
  const [configSearchQuery, setConfigSearchQuery] = useState<string>('');
  const [expandedSources, setExpandedSources] = useState<Set<number>>(new Set([0])); // Expand first source by default

  const { data: service, isLoading } = useQuery({
    queryKey: ['service', serviceId],
    queryFn: () => api.getService(serviceId),
    refetchInterval: 10000,
  });

  const { data: metrics } = useQuery({
    queryKey: ['metrics', serviceId],
    queryFn: () => api.getMetrics(serviceId, 24),
    refetchInterval: 15000,
  });

  // Health checks are available but not displayed in current view
  // const { data: healthChecks } = useQuery({
  //   queryKey: ['healthChecks', serviceId],
  //   queryFn: () => api.getHealthChecks(serviceId, 24),
  //   refetchInterval: 30000,
  // });

  const { data: analysis } = useQuery({
    queryKey: ['analysis', serviceId],
    queryFn: () => api.getServiceAnalysis(serviceId),
    refetchInterval: 60000,
  });

  // Remote logs query (only for BACKEND services)
  const { data: remoteLogs, isLoading: logsLoading, error: logsError } = useQuery({
    queryKey: ['remoteLogs', serviceId, logLines, logLevel],
    queryFn: () => {
      console.log('Fetching remote logs for service', serviceId, 'lines:', logLines, 'level:', logLevel);
      return api.getServiceRemoteLogs(serviceId, logLines, logLevel !== 'ALL' ? logLevel : undefined);
    },
    enabled: service?.serviceType === 'BACKEND' && activeTab === 'logs',
    refetchInterval: activeTab === 'logs' ? 10000 : false,
    onSuccess: (data) => {
      console.log('Remote logs fetched:', data?.length || 0, 'entries', data);
    },
    onError: (error) => {
      console.error('Error fetching remote logs:', error);
    },
  });
  
  // Filter logs based on search query
  const filteredLogs = remoteLogs?.filter((log) => {
    if (!logSearchQuery.trim()) return true;
    const query = logSearchQuery.toLowerCase();
    return (
      log.message?.toLowerCase().includes(query) ||
      log.logger?.toLowerCase().includes(query) ||
      log.level?.toLowerCase().includes(query) ||
      log.thread?.toLowerCase().includes(query)
    );
  }) || [];
  
  // Get log level icon
  const getLogLevelIcon = (level: string) => {
    switch (level?.toUpperCase()) {
      case 'ERROR':
        return <AlertCircle className="w-3.5 h-3.5" />;
      case 'WARN':
        return <AlertTriangle className="w-3.5 h-3.5" />;
      case 'INFO':
        return <Info className="w-3.5 h-3.5" />;
      case 'DEBUG':
        return <Bug className="w-3.5 h-3.5" />;
      default:
        return <FileText className="w-3.5 h-3.5" />;
    }
  };
  
  // Get log level color
  const getLogLevelColor = (level: string) => {
    switch (level?.toUpperCase()) {
      case 'ERROR':
        return 'text-rose-400 bg-rose-500/10 border-rose-500/30';
      case 'WARN':
        return 'text-amber-400 bg-amber-500/10 border-amber-500/30';
      case 'INFO':
        return 'text-cyan-400 bg-cyan-500/10 border-cyan-500/30';
      case 'DEBUG':
        return 'text-purple-400 bg-purple-500/10 border-purple-500/30';
      case 'TRACE':
        return 'text-slate-400 bg-slate-500/10 border-slate-500/30';
      default:
        return 'text-obsidian-300 bg-obsidian-800/50 border-obsidian-700';
    }
  };
  
  // Count logs by level
  const logLevelCounts = remoteLogs?.reduce((acc, log) => {
    const level = log.level?.toUpperCase() || 'UNKNOWN';
    acc[level] = (acc[level] || 0) + 1;
    return acc;
  }, {} as Record<string, number>) || {};

  // Configuration query
  const { data: configuration, isLoading: configLoading, error: configError } = useQuery({
    queryKey: ['configuration', serviceId],
    queryFn: async () => {
      console.log('[ServiceDetail] Fetching configuration for service', serviceId);
      try {
        const data = await api.getServiceConfiguration(serviceId);
        console.log('[ServiceDetail] Configuration fetched successfully:', {
          propertySources: data?.propertySources?.length || 0,
          activeProfiles: data?.activeProfiles?.length || 0,
          hasData: !!(data && data.propertySources && data.propertySources.length > 0)
        });
        
        // Validate that we have meaningful data
        if (!data || !data.propertySources || data.propertySources.length === 0) {
          console.warn('[ServiceDetail] Configuration data appears empty or invalid');
        }
        
        return data;
      } catch (error) {
        console.error('[ServiceDetail] Error in configuration query:', error);
        throw error;
      }
    },
    enabled: service?.serviceType === 'BACKEND' && activeTab === 'config',
    retry: 2,
    retryDelay: 1000,
  });

  // Infrastructure query
  const { data: infrastructure, isLoading: infraLoading, error: infraError } = useQuery({
    queryKey: ['infrastructure', serviceId],
    queryFn: async () => {
      console.log('[ServiceDetail] Fetching infrastructure for service', serviceId);
      try {
        const data = await api.getServiceInfrastructure(serviceId);
        console.log('[ServiceDetail] Infrastructure fetched successfully:', {
          serviceId: data?.serviceId,
          serviceName: data?.serviceName,
          osName: data?.osName,
          jvmName: data?.jvmName,
          hasData: !!(data && (data.serviceId || data.serviceName || data.osName || data.jvmName))
        });
        
        // Validate that we have meaningful data
        if (!data || (!data.serviceId && !data.serviceName && !data.osName && !data.jvmName)) {
          console.warn('[ServiceDetail] Infrastructure data appears empty or invalid');
        }
        
        return data;
      } catch (error) {
        console.error('[ServiceDetail] Error in infrastructure query:', error);
        throw error;
      }
    },
    enabled: service?.serviceType === 'BACKEND' && activeTab === 'infrastructure',
    retry: 2,
    retryDelay: 1000,
  });

  // JVM info query
  const { data: jvmInfo, isLoading: jvmLoading, error: jvmError } = useQuery({
    queryKey: ['jvmInfo', serviceId],
    queryFn: async () => {
      console.log('[ServiceDetail] Fetching JVM info for service', serviceId);
      try {
        const data = await api.getServiceJvmInfo(serviceId);
        console.log('[ServiceDetail] JVM info fetched successfully:', {
          serviceId: data?.serviceId,
          serviceName: data?.serviceName,
          jvmName: data?.jvmName,
          hasData: !!(data && (data.serviceId || data.serviceName || data.jvmName || data.heapUsed))
        });
        
        // Validate that we have meaningful data
        if (!data || (!data.serviceId && !data.serviceName && !data.jvmName && !data.heapUsed)) {
          console.warn('[ServiceDetail] JVM info data appears empty or invalid');
        }
        
        return data;
      } catch (error) {
        console.error('[ServiceDetail] Error in JVM info query:', error);
        throw error;
      }
    },
    enabled: service?.serviceType === 'BACKEND' && activeTab === 'infrastructure',
    retry: 2,
    retryDelay: 1000,
  });

  const startMutation = useMutation({
    mutationFn: () => api.startService(serviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['service', serviceId] });
    },
  });

  const stopMutation = useMutation({
    mutationFn: (reason: string) => api.stopService(serviceId, reason, true),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['service', serviceId] });
      setShowConfirmModal(false);
    },
  });

  const restartMutation = useMutation({
    mutationFn: (reason: string) => api.restartService(serviceId, reason, true),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['service', serviceId] });
      setShowConfirmModal(false);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => api.deleteService(serviceId),
    onSuccess: () => {
      navigate('/services');
    },
  });

  const handleAction = (action: 'stop' | 'restart' | 'delete') => {
    setPendingAction({ action, reason: '' });
    setShowConfirmModal(true);
  };

  const confirmAction = () => {
    if (!pendingAction) return;
    
    switch (pendingAction.action) {
      case 'stop':
        stopMutation.mutate(pendingAction.reason);
        break;
      case 'restart':
        restartMutation.mutate(pendingAction.reason);
        break;
      case 'delete':
        deleteMutation.mutate();
        break;
    }
  };

  const canControl = user?.role === 'ADMIN' || user?.role === 'OPERATOR';

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-obsidian-400">Loading service...</div>
      </div>
    );
  }

  if (!service) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px]">
        <Server className="w-16 h-16 text-obsidian-700 mb-4" />
        <p className="text-obsidian-400 text-lg">Service not found</p>
        <Link to="/services" className="mt-4 text-emerald-400 hover:text-emerald-300">
          Back to services
        </Link>
      </div>
    );
  }

  // Prepare chart data
  const chartData = (metrics || []).slice(0, 50).reverse().map((m: Metrics, index: number) => ({
    time: index,
    cpu: m.cpuUsage || 0,
    memory: m.memoryUsagePercent || 0,
    errors: m.errorRate || 0,
  }));

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-start justify-between gap-4">
        <div className="flex items-start gap-4">
          <Link
            to="/services"
            className="p-2 text-obsidian-400 hover:text-obsidian-200 hover:bg-obsidian-800/50 rounded-lg transition-colors"
          >
            <ChevronLeft className="w-5 h-5" />
          </Link>
          <div>
            <div className="flex items-center gap-3 mb-2">
              <StatusDot status={service.healthStatus} size="lg" />
              <h1 className="text-2xl font-bold text-obsidian-100">{service.name}</h1>
              <Badge variant="outline">{service.serviceType}</Badge>
              {service.environment && (
                <Badge
                  variant={service.environment === 'PROD' ? 'danger' : 'info'}
                  size="sm"
                >
                  {service.environment}
                </Badge>
              )}
            </div>
            <p className="text-obsidian-400">
              {service.description || `${service.host}:${service.port}`}
            </p>
            <div className="flex items-center gap-4 mt-2 text-sm text-obsidian-500">
              <span className="flex items-center gap-1">
                <Clock className="w-4 h-4" />
                Last checked: {service.lastHealthCheck ? formatRelativeTime(service.lastHealthCheck) : 'Never'}
              </span>
              {service.baseUrl && (
                <a
                  href={service.baseUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-1 text-emerald-400 hover:text-emerald-300"
                >
                  <ExternalLink className="w-4 h-4" />
                  Open
                </a>
              )}
            </div>
          </div>
        </div>

        {/* Actions */}
        {canControl && (
          <div className="flex items-center gap-2">
            {!service.isRunning ? (
              <Button
                onClick={() => startMutation.mutate()}
                loading={startMutation.isPending}
                icon={<Play className="w-4 h-4" />}
              >
                Start
              </Button>
            ) : (
              <>
                <Button
                  variant="secondary"
                  onClick={() => handleAction('restart')}
                  loading={restartMutation.isPending}
                  icon={<RefreshCw className="w-4 h-4" />}
                >
                  Restart
                </Button>
                <Button
                  variant="danger"
                  onClick={() => handleAction('stop')}
                  loading={stopMutation.isPending}
                  icon={<Square className="w-4 h-4" />}
                >
                  Stop
                </Button>
              </>
            )}
            {user?.role === 'ADMIN' && (
              <Button
                variant="ghost"
                onClick={() => handleAction('delete')}
                icon={<Trash2 className="w-4 h-4" />}
              />
            )}
          </div>
        )}
      </div>

      {/* Tabs */}
      {service.serviceType === 'BACKEND' && (
        <div className="flex items-center gap-2 border-b border-obsidian-800">
          {[
            { id: 'overview', label: 'Overview', icon: Activity },
            { id: 'logs', label: 'Logs', icon: FileText },
            { id: 'config', label: 'Configuration', icon: Settings },
            { id: 'infrastructure', label: 'Infrastructure', icon: HardDrive },
          ].map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as typeof activeTab)}
                className={cn(
                  'flex items-center gap-2 px-4 py-2 border-b-2 transition-colors',
                  activeTab === tab.id
                    ? 'border-emerald-500 text-emerald-400'
                    : 'border-transparent text-obsidian-400 hover:text-obsidian-200'
                )}
              >
                <Icon className="w-4 h-4" />
                {tab.label}
              </button>
            );
          })}
        </div>
      )}

      {/* Tab Content */}
      {activeTab === 'overview' && (
        <>
          {/* Stats Cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <MetricCard
          icon={<Cpu className="w-5 h-5" />}
          label="CPU Usage"
          value={service.cpuUsage != null ? `${service.cpuUsage.toFixed(1)}%` : '-'}
          color="cyan"
        />
        <MetricCard
          icon={<MemoryStick className="w-5 h-5" />}
          label="Memory"
          value={service.memoryUsage != null ? `${service.memoryUsage.toFixed(1)}%` : '-'}
          color="violet"
        />
        <MetricCard
          icon={<Activity className="w-5 h-5" />}
          label="Response Time"
          value={service.responseTime != null ? formatDuration(service.responseTime) : '-'}
          color="emerald"
        />
        <MetricCard
          icon={<AlertTriangle className="w-5 h-5" />}
          label="Error Rate"
          value={service.errorRate != null ? `${service.errorRate.toFixed(2)}%` : '-'}
          color={service.errorRate && service.errorRate > 5 ? 'rose' : 'amber'}
        />
      </div>

      {/* Charts & AI Analysis */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Metrics Chart */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Activity className="w-5 h-5 text-emerald-400" />
              Performance Metrics (24h)
            </CardTitle>
          </CardHeader>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="cpuGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#06b6d4" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#06b6d4" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="memoryGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="time" stroke="#4a4d59" fontSize={10} />
                <YAxis stroke="#4a4d59" fontSize={10} domain={[0, 100]} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#1a1b1f',
                    border: '1px solid #3d3f49',
                    borderRadius: '8px',
                  }}
                  labelStyle={{ color: '#9ea1ab' }}
                />
                <Area
                  type="monotone"
                  dataKey="cpu"
                  stroke="#06b6d4"
                  fill="url(#cpuGradient)"
                  strokeWidth={2}
                  name="CPU %"
                />
                <Area
                  type="monotone"
                  dataKey="memory"
                  stroke="#8b5cf6"
                  fill="url(#memoryGradient)"
                  strokeWidth={2}
                  name="Memory %"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>

        {/* AI Analysis */}
        <Card className={cn(analysis?.anomalyDetected && 'border-amber-500/30')}>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-violet-400" />
              AI Analysis
            </CardTitle>
          </CardHeader>
          {analysis ? (
            <div className="space-y-4">
              {/* Risk Score */}
              <div className="flex items-center justify-between">
                <span className="text-obsidian-400">Risk Score</span>
                <div className="flex items-center gap-2">
                  <div className="w-24 h-2 bg-obsidian-800 rounded-full overflow-hidden">
                    <motion.div
                      className={cn(
                        'h-full rounded-full',
                        analysis.riskScore >= 70 ? 'bg-rose-500' :
                        analysis.riskScore >= 40 ? 'bg-amber-500' : 'bg-emerald-500'
                      )}
                      initial={{ width: 0 }}
                      animate={{ width: `${analysis.riskScore}%` }}
                    />
                  </div>
                  <span className={cn('font-bold', getRiskColor(analysis.riskScore))}>
                    {analysis.riskScore}
                  </span>
                </div>
              </div>

              {/* Stability Score */}
              <div className="flex items-center justify-between">
                <span className="text-obsidian-400">Stability</span>
                <span className={cn('font-bold', getRiskColor(100 - service.stabilityScore))}>
                  {service.stabilityScore}%
                </span>
              </div>

              {/* Trend */}
              <div className="flex items-center justify-between">
                <span className="text-obsidian-400">Trend</span>
                <Badge
                  variant={
                    analysis.riskTrend === 'IMPROVING' ? 'success' :
                    analysis.riskTrend === 'DEGRADING' ? 'danger' : 'outline'
                  }
                >
                  {analysis.riskTrend}
                </Badge>
              </div>

              {/* Anomaly Alert */}
              {analysis.anomalyDetected && (
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="p-3 bg-amber-500/10 border border-amber-500/30 rounded-lg"
                >
                  <div className="flex items-center gap-2 text-amber-400 font-medium text-sm mb-1">
                    <AlertTriangle className="w-4 h-4" />
                    {analysis.anomalyType}
                  </div>
                  <p className="text-xs text-obsidian-400">{analysis.anomalyDescription}</p>
                </motion.div>
              )}

              {/* Recommendations */}
              {analysis.recommendations.length > 0 && (
                <div className="space-y-2">
                  <p className="text-xs text-obsidian-500 uppercase tracking-wider">Recommendations</p>
                  {analysis.recommendations.slice(0, 2).map((rec, i) => (
                    <div
                      key={i}
                      className="p-2 bg-obsidian-800/50 rounded-lg text-xs"
                    >
                      <div className="flex items-center gap-2 mb-1">
                        <Badge
                          variant={rec.urgency === 'HIGH' ? 'danger' : rec.urgency === 'MEDIUM' ? 'warning' : 'info'}
                          size="sm"
                        >
                          {rec.action}
                        </Badge>
                      </div>
                      <p className="text-obsidian-400">{rec.reason}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center py-8 text-obsidian-500">
              <Sparkles className="w-8 h-8 mb-2" />
              <p className="text-sm">Analyzing...</p>
            </div>
          )}
        </Card>
      </div>

      {/* Service Details */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Connection Info */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Server className="w-5 h-5 text-cyan-400" />
              Connection Details
            </CardTitle>
          </CardHeader>
          <div className="space-y-3 text-sm">
            <DetailRow label="Host" value={service.host} />
            <DetailRow label="Port" value={service.port.toString()} />
            <DetailRow label="Base URL" value={service.baseUrl || '-'} />
            {service.serviceType === 'BACKEND' && (
              <DetailRow label="Actuator Path" value={service.actuatorBasePath || '/actuator'} />
            )}
            {service.serviceType === 'FRONTEND' && (
              <>
                <DetailRow label="Technology" value={service.frontendTechnology || '-'} />
                <DetailRow label="Serving" value={service.servingTechnology || '-'} />
              </>
            )}
          </div>
        </Card>

        {/* Lifecycle Commands */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Zap className="w-5 h-5 text-amber-400" />
              Lifecycle Commands
            </CardTitle>
          </CardHeader>
          <div className="space-y-3 text-sm">
            <DetailRow label="Start Command" value={service.startCommand || '-'} mono />
            <DetailRow label="Stop Command" value={service.stopCommand || '-'} mono />
            <DetailRow label="Restart Command" value={service.restartCommand || '-'} mono />
            <DetailRow label="Working Dir" value={service.workingDirectory || '-'} mono />
          </div>
        </Card>
      </div>
        </>
      )}

      {/* Logs Tab */}
      {activeTab === 'logs' && service.serviceType === 'BACKEND' && (
        <div className="space-y-4">
          {/* Filters */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2">
                  <FileText className="w-5 h-5 text-cyan-400" />
                  Application Logs
                </CardTitle>
                {remoteLogs && remoteLogs.length > 0 && (
                  <Badge variant="outline" className="text-xs">
                    {filteredLogs.length} of {remoteLogs.length} logs
                  </Badge>
                )}
              </div>
            </CardHeader>
            <div className="px-6 pb-4 space-y-4">
              {/* Search and Filters Row */}
              <div className="flex flex-col md:flex-row gap-3">
                {/* Search */}
                <div className="flex-1 relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-obsidian-400" />
                  <input
                    type="text"
                    placeholder="Search logs by message, logger, thread..."
                    value={logSearchQuery}
                    onChange={(e) => setLogSearchQuery(e.target.value)}
                    className="w-full pl-10 pr-10 py-2 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 placeholder:text-obsidian-500 focus:outline-none focus:border-cyan-500 text-sm"
                  />
                  {logSearchQuery && (
                    <button
                      onClick={() => setLogSearchQuery('')}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-obsidian-400 hover:text-obsidian-200"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  )}
                </div>
                
                {/* Level Filter */}
                <div className="flex items-center gap-2">
                  <Filter className="w-4 h-4 text-obsidian-400" />
                  <select
                    value={logLevel}
                    onChange={(e) => setLogLevel(e.target.value)}
                    className="px-3 py-2 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-cyan-500 text-sm"
                  >
                    <option value="ALL">All Levels</option>
                    <option value="ERROR">ERROR</option>
                    <option value="WARN">WARN</option>
                    <option value="INFO">INFO</option>
                    <option value="DEBUG">DEBUG</option>
                    <option value="TRACE">TRACE</option>
                  </select>
                </div>
                
                {/* Lines Filter */}
                <select
                  value={logLines}
                  onChange={(e) => setLogLines(Number(e.target.value))}
                  className="px-3 py-2 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-cyan-500 text-sm"
                >
                  <option value={50}>50 lines</option>
                  <option value={100}>100 lines</option>
                  <option value={200}>200 lines</option>
                  <option value={500}>500 lines</option>
                  <option value={1000}>1000 lines</option>
                </select>
              </div>
              
              {/* Log Level Counts */}
              {Object.keys(logLevelCounts).length > 0 && (
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-xs text-obsidian-400">Levels:</span>
                  {['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'].map((level) => {
                    const count = logLevelCounts[level] || 0;
                    if (count === 0) return null;
                    return (
                      <button
                        key={level}
                        onClick={() => setLogLevel(level)}
                        className={cn(
                          'px-2 py-1 rounded text-xs font-medium transition-colors',
                          logLevel === level
                            ? getLogLevelColor(level)
                            : 'bg-obsidian-800 text-obsidian-400 hover:bg-obsidian-700'
                        )}
                      >
                        {level}: {count}
                      </button>
                    );
                  })}
                  {logLevelCounts.UNKNOWN && (
                    <span className="px-2 py-1 rounded text-xs bg-obsidian-800 text-obsidian-400">
                      UNKNOWN: {logLevelCounts.UNKNOWN}
                    </span>
                  )}
                </div>
              )}
            </div>
          </Card>
          
          {/* Logs Display */}
          <Card>
            <div className="max-h-[600px] overflow-y-auto">
              {logsLoading ? (
                <div className="flex flex-col items-center justify-center py-12 text-obsidian-500">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-cyan-500 mb-2"></div>
                  <p>Loading logs...</p>
                </div>
              ) : logsError ? (
                <div className="flex flex-col items-center justify-center py-12 text-rose-500">
                  <FileText className="w-12 h-12 mb-2 opacity-50" />
                  <p>Error loading logs</p>
                  <p className="text-sm mt-1 text-obsidian-400">{logsError.message}</p>
                </div>
              ) : filteredLogs.length > 0 ? (
                <div className="divide-y divide-obsidian-800">
                  {filteredLogs.map((log, idx) => (
                    <div
                      key={idx}
                      className={cn(
                        'px-4 py-3 border-l-4 transition-colors hover:bg-obsidian-900/50',
                        getLogLevelColor(log.level || 'INFO')
                      )}
                    >
                      {/* Header Row */}
                      <div className="flex items-start justify-between gap-3 mb-2">
                        <div className="flex items-center gap-2 flex-wrap">
                          {/* Level Badge */}
                          <div className={cn(
                            'flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-semibold',
                            getLogLevelColor(log.level || 'INFO')
                          )}>
                            {getLogLevelIcon(log.level || 'INFO')}
                            <span>{log.level || 'UNKNOWN'}</span>
                          </div>
                          
                          {/* Timestamp */}
                          <span className="text-xs text-obsidian-400 font-mono">
                            {log.timestamp ? new Date(log.timestamp).toLocaleString('en-US', {
                              month: 'short',
                              day: 'numeric',
                              year: 'numeric',
                              hour: '2-digit',
                              minute: '2-digit',
                              second: '2-digit',
                              hour12: true
                            }) : 'N/A'}
                          </span>
                          
                          {/* Thread */}
                          {log.thread && (
                            <span className="text-xs text-obsidian-500 font-mono bg-obsidian-800 px-1.5 py-0.5 rounded">
                              {log.thread}
                            </span>
                          )}
                        </div>
                      </div>
                      
                      {/* Logger */}
                      {log.logger && (
                        <div className="mb-1.5">
                          <span className="text-xs text-obsidian-400 font-mono">
                            {log.logger}
                          </span>
                        </div>
                      )}
                      
                      {/* Message */}
                      <div className="text-sm text-obsidian-200 font-mono leading-relaxed break-words">
                        {log.message}
                      </div>
                    </div>
                  ))}
                </div>
              ) : logSearchQuery ? (
                <div className="flex flex-col items-center justify-center py-12 text-obsidian-500">
                  <Search className="w-12 h-12 mb-2 opacity-50" />
                  <p>No logs match your search</p>
                  <p className="text-sm mt-1 text-obsidian-400">
                    Try adjusting your search query or filters
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setLogSearchQuery('')}
                    className="mt-3"
                  >
                    Clear Search
                  </Button>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center py-12 text-obsidian-500">
                  <FileText className="w-12 h-12 mb-2 opacity-50" />
                  <p>No logs available</p>
                  <p className="text-sm mt-1">Ensure Actuator logfile endpoint is enabled</p>
                  <p className="text-xs mt-2 text-obsidian-600">Service ID: {serviceId}</p>
                </div>
              )}
            </div>
          </Card>
        </div>
      )}

      {/* Configuration Tab */}
      {activeTab === 'config' && service.serviceType === 'BACKEND' && (
        <div className="space-y-4">
          {/* Configuration Header with Search */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2">
                  <Settings className="w-5 h-5 text-violet-400" />
                  Application Configuration
                </CardTitle>
                {configuration?.propertySources && configuration.propertySources.length > 0 && (
                  <Badge variant="outline" className="text-xs">
                    {configuration.propertySources.reduce((sum, s) => sum + (s.propertyCount || 0), 0)} total properties
                  </Badge>
                )}
              </div>
            </CardHeader>
            <div className="px-6 pb-4">
              {/* Search */}
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-obsidian-400" />
                <input
                  type="text"
                  placeholder="Search configuration properties..."
                  value={configSearchQuery}
                  onChange={(e) => setConfigSearchQuery(e.target.value)}
                  className="w-full pl-10 pr-10 py-2 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 placeholder:text-obsidian-500 focus:outline-none focus:border-violet-500 text-sm"
                />
                {configSearchQuery && (
                  <button
                    onClick={() => setConfigSearchQuery('')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-obsidian-400 hover:text-obsidian-200 transition-colors"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </div>
            </div>
          </Card>

          {/* Configuration Content */}
          {configLoading ? (
            <Card>
              <div className="flex items-center justify-center py-12 text-obsidian-500">
                <div className="text-center">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-violet-500 mx-auto mb-2"></div>
                  <p>Loading configuration...</p>
                </div>
              </div>
            </Card>
          ) : configError ? (
            <Card>
              <div className="flex items-center justify-center py-12 text-rose-500">
                <div className="text-center">
                  <Settings className="w-12 h-12 mx-auto mb-2 opacity-50" />
                  <p>Error loading configuration</p>
                  <p className="text-sm mt-1 text-obsidian-400">{configError.message}</p>
                </div>
              </div>
            </Card>
          ) : configuration ? (
            <>
              {configuration.propertySources && configuration.propertySources.length > 0 ? (
                <div className="space-y-3">
                  {configuration.propertySources.map((source, idx) => {
                    // Filter properties based on search query
                    const filteredProperties = configSearchQuery
                      ? Object.entries(source.properties || {}).filter(([key, value]) => {
                          const query = configSearchQuery.toLowerCase();
                          return (
                            key.toLowerCase().includes(query) ||
                            String(value).toLowerCase().includes(query)
                          );
                        })
                      : Object.entries(source.properties || {});
                    
                    const isExpanded = expandedSources.has(idx);
                    const hasMatches = filteredProperties.length > 0;
                    
                    // Group properties by prefix for better organization
                    const groupedProperties = filteredProperties.reduce((acc, [key, value]) => {
                      const prefix = key.split('.')[0] || 'other';
                      if (!acc[prefix]) acc[prefix] = [];
                      acc[prefix].push([key, value]);
                      return acc;
                    }, {} as Record<string, Array<[string, string]>>);
                    
                    return (
                      <Card key={idx} className="overflow-hidden">
                        <button
                          onClick={() => {
                            const newExpanded = new Set(expandedSources);
                            if (isExpanded) {
                              newExpanded.delete(idx);
                            } else {
                              newExpanded.add(idx);
                            }
                            setExpandedSources(newExpanded);
                          }}
                          className="w-full"
                        >
                          <CardHeader className="hover:bg-obsidian-900/50 transition-colors cursor-pointer">
                            <div className="flex items-center justify-between">
                              <div className="flex items-center gap-3">
                                <div className={cn(
                                  "w-5 h-5 rounded flex items-center justify-center transition-transform",
                                  isExpanded ? "rotate-90" : "",
                                  "text-violet-400"
                                )}>
                                  <ChevronLeft className="w-4 h-4" />
                                </div>
                                <div>
                                  <CardTitle className="text-base font-medium text-obsidian-200 text-left">
                                    {source.name || `Configuration Source ${idx + 1}`}
                                  </CardTitle>
                                  {source.type && (
                                    <p className="text-xs text-obsidian-400 mt-0.5">{source.type}</p>
                                  )}
                                </div>
                              </div>
                              <div className="flex items-center gap-2">
                                {!hasMatches && configSearchQuery && (
                                  <Badge variant="outline" size="sm" className="text-amber-400 border-amber-500/30">
                                    No matches
                                  </Badge>
                                )}
                                <Badge variant="outline" size="sm">
                                  {filteredProperties.length} {configSearchQuery ? 'matched' : 'properties'}
                                </Badge>
                              </div>
                            </div>
                          </CardHeader>
                        </button>
                        
                        {isExpanded && hasMatches && (
                          <div className="px-6 pb-4">
                            <div className="space-y-4 max-h-[600px] overflow-y-auto">
                              {Object.entries(groupedProperties)
                                .sort(([a], [b]) => a.localeCompare(b))
                                .map(([prefix, props]) => (
                                  <div key={prefix} className="space-y-2">
                                    <h4 className="text-xs font-semibold text-violet-400 uppercase tracking-wider px-2 py-1 bg-violet-500/10 rounded">
                                      {prefix}
                                    </h4>
                                    <div className="space-y-1 pl-2 border-l-2 border-obsidian-800">
                                      {props
                                        .sort(([a], [b]) => a.localeCompare(b))
                                        .map(([key, value]) => (
                                          <div
                                            key={key}
                                            className="group hover:bg-obsidian-900/30 rounded px-3 py-2 transition-colors"
                                          >
                                            <div className="flex items-start gap-3">
                                              <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 mb-1">
                                                  <span className="text-obsidian-400 font-mono text-xs font-medium">
                                                    {key}
                                                  </span>
                                                </div>
                                                <div className="text-obsidian-200 font-mono text-sm break-all">
                                                  {value === '******' ? (
                                                    <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/30 text-xs">
                                                      <AlertTriangle className="w-3 h-3" />
                                                      [MASKED]
                                                    </span>
                                                  ) : (
                                                    <span className="text-obsidian-100">{value}</span>
                                                  )}
                                                </div>
                                              </div>
                                              <button
                                                onClick={(e) => {
                                                  e.stopPropagation();
                                                  navigator.clipboard.writeText(value === '******' ? '[MASKED]' : String(value));
                                                }}
                                                className="opacity-0 group-hover:opacity-100 transition-opacity text-obsidian-400 hover:text-obsidian-200 p-1"
                                                title="Copy value"
                                              >
                                                <FileText className="w-3.5 h-3.5" />
                                              </button>
                                            </div>
                                          </div>
                                        ))}
                                    </div>
                                  </div>
                                ))}
                            </div>
                          </div>
                        )}
                        
                        {isExpanded && !hasMatches && configSearchQuery && (
                          <div className="px-6 pb-4">
                            <div className="text-center py-8 text-obsidian-500">
                              <Search className="w-8 h-8 mx-auto mb-2 opacity-50" />
                              <p className="text-sm">No properties match your search</p>
                              <p className="text-xs mt-1 text-obsidian-600">Try a different search term</p>
                            </div>
                          </div>
                        )}
                      </Card>
                    );
                  })}
                </div>
              ) : (
                <Card>
                  <div className="text-center py-8 text-obsidian-500">
                    <Settings className="w-12 h-12 mx-auto mb-2 opacity-50" />
                    <p>No configuration available</p>
                    <p className="text-xs mt-2 text-obsidian-600">Service ID: {serviceId}</p>
                  </div>
                </Card>
              )}
            </>
          ) : (
            <Card>
              <div className="flex items-center justify-center py-12 text-obsidian-500">
                <div className="text-center">
                  <Settings className="w-12 h-12 mx-auto mb-2 opacity-50" />
                  <p>No configuration data</p>
                </div>
              </div>
            </Card>
          )}
        </div>
      )}

      {/* Infrastructure Tab */}
      {activeTab === 'infrastructure' && service.serviceType === 'BACKEND' && (
        <div className="space-y-6">
          {/* Loading State */}
          {(infraLoading || jvmLoading) && (
            <div className="flex items-center justify-center p-8">
              <RefreshCw className="w-6 h-6 animate-spin text-obsidian-400" />
            </div>
          )}

          {/* Error States */}
          {infraError && (
            <Card>
              <div className="text-center py-8 text-rose-500">
                <HardDrive className="w-12 h-12 mx-auto mb-2 opacity-50" />
                <p>Error loading infrastructure</p>
                <p className="text-sm mt-1 text-obsidian-400">
                  {infraError instanceof Error ? infraError.message : 'Unknown error occurred'}
                </p>
              </div>
            </Card>
          )}

          {/* Quick Stats - Metric Cards */}
          {infrastructure && jvmInfo && !infraLoading && !jvmLoading && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <InfraMetricCard
                title="Heap Used"
                value={jvmInfo.heapUsed && jvmInfo.heapMax ? formatBytes(jvmInfo.heapUsed) : '-'}
                subtitle={jvmInfo.heapUsed && jvmInfo.heapMax 
                  ? `${((jvmInfo.heapUsed / jvmInfo.heapMax) * 100).toFixed(1)}% of max`
                  : 'N/A'}
                icon={<MemoryStick className="w-5 h-5" />}
                color="violet"
              />
              <InfraMetricCard
                title="Threads"
                value={jvmInfo.threadCount ? jvmInfo.threadCount.toString() : '-'}
                subtitle="Active threads"
                icon={<Layers className="w-5 h-5" />}
                color="cyan"
              />
              <InfraMetricCard
                title="Processors"
                value={infrastructure.availableProcessors ? infrastructure.availableProcessors.toString() : '-'}
                subtitle={infrastructure.osArch || 'N/A'}
                icon={<Cpu className="w-5 h-5" />}
                color="emerald"
              />
              <InfraMetricCard
                title="Uptime"
                value={jvmInfo.uptime ? formatDuration(jvmInfo.uptime) : '-'}
                subtitle="Since last restart"
                icon={<Timer className="w-5 h-5" />}
                color="amber"
              />
            </div>
          )}

          {/* Main Content Grid */}
          {!infraLoading && !jvmLoading && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* System Information */}
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <HardDrive className="w-5 h-5 text-emerald-400" />
                    System Information
                  </CardTitle>
                </CardHeader>
                <div className="space-y-3 text-sm">
                  {infrastructure ? (
                    <>
                      <InfraInfoRow 
                        label="Service" 
                        value={infrastructure.serviceName || infrastructure.serviceId?.toString() || 'Unknown'} 
                      />
                      <InfraInfoRow 
                        label="OS" 
                        value={
                          infrastructure.osName && infrastructure.osName !== 'Unknown'
                            ? `${infrastructure.osName}${infrastructure.osVersion ? ' ' + infrastructure.osVersion : ''}`
                            : '-'
                        } 
                      />
                      <InfraInfoRow 
                        label="Architecture" 
                        value={infrastructure.osArch && infrastructure.osArch !== 'Unknown' ? infrastructure.osArch : '-'} 
                      />
                      <InfraInfoRow 
                        label="Processors" 
                        value={infrastructure.availableProcessors && infrastructure.availableProcessors > 0 
                          ? infrastructure.availableProcessors.toString() 
                          : '-'} 
                      />
                      <InfraInfoRow 
                        label="Total Memory" 
                        value={infrastructure.totalPhysicalMemory && infrastructure.totalPhysicalMemory > 0
                          ? formatBytes(infrastructure.totalPhysicalMemory) 
                          : '-'} 
                      />
                      <InfraInfoRow 
                        label="Free Memory" 
                        value={infrastructure.freePhysicalMemory && infrastructure.freePhysicalMemory > 0
                          ? formatBytes(infrastructure.freePhysicalMemory) 
                          : '-'} 
                      />
                      {infrastructure.totalPhysicalMemory && infrastructure.freePhysicalMemory && (
                        <div className="pt-2">
                          <div className="flex items-center justify-between text-sm mb-2">
                            <span className="text-obsidian-400">Memory Usage</span>
                            <span className="text-obsidian-200">
                              {formatBytes(infrastructure.totalPhysicalMemory - infrastructure.freePhysicalMemory)} / {formatBytes(infrastructure.totalPhysicalMemory)}
                            </span>
                          </div>
                          <div className="h-2 bg-obsidian-800 rounded-full overflow-hidden">
                            <motion.div
                              className="h-full bg-gradient-to-r from-emerald-600 to-emerald-400 rounded-full"
                              initial={{ width: 0 }}
                              animate={{ 
                                width: `${((infrastructure.totalPhysicalMemory - infrastructure.freePhysicalMemory) / infrastructure.totalPhysicalMemory) * 100}%` 
                              }}
                              transition={{ duration: 1 }}
                            />
                          </div>
                        </div>
                      )}
                      <InfraInfoRow 
                        label="System CPU" 
                        value={infrastructure.systemCpuLoad != null && infrastructure.systemCpuLoad >= 0
                          ? `${infrastructure.systemCpuLoad.toFixed(1)}%` 
                          : '-'} 
                      />
                      <InfraInfoRow 
                        label="Process CPU" 
                        value={infrastructure.processCpuLoad != null && infrastructure.processCpuLoad >= 0
                          ? `${infrastructure.processCpuLoad.toFixed(1)}%` 
                          : '-'} 
                      />
                      {infrastructure.systemCpuLoad != null && infrastructure.systemCpuLoad >= 0 && (
                        <div className="pt-2">
                          <div className="flex items-center justify-between text-sm mb-2">
                            <span className="text-obsidian-400">CPU Usage</span>
                            <span className="text-obsidian-200">{infrastructure.systemCpuLoad.toFixed(1)}%</span>
                          </div>
                          <div className="h-2 bg-obsidian-800 rounded-full overflow-hidden">
                            <motion.div
                              className="h-full bg-gradient-to-r from-cyan-600 to-cyan-400 rounded-full"
                              initial={{ width: 0 }}
                              animate={{ width: `${Math.min(infrastructure.systemCpuLoad, 100)}%` }}
                              transition={{ duration: 1 }}
                            />
                          </div>
                        </div>
                      )}
                      {infrastructure.jvmName && infrastructure.jvmName !== 'Unknown' && (
                        <InfraInfoRow 
                          label="JVM" 
                          value={`${infrastructure.jvmName}${infrastructure.jvmVersion ? ' ' + infrastructure.jvmVersion : ''}`} 
                        />
                      )}
                      {infrastructure.applicationVersion && (
                        <InfraInfoRow 
                          label="App Version" 
                          value={infrastructure.applicationVersion} 
                        />
                      )}
                    </>
                  ) : (
                    <div className="text-center py-8 text-obsidian-500">
                      <HardDrive className="w-12 h-12 mx-auto mb-2 opacity-50" />
                      <p>No infrastructure data available</p>
                    </div>
                  )}
                </div>
              </Card>

              {/* JVM Information */}
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Database className="w-5 h-5 text-cyan-400" />
                    JVM Information
                  </CardTitle>
                </CardHeader>
                <div className="space-y-4">
                  {jvmError ? (
                    <div className="text-center py-8 text-rose-500">
                      <Database className="w-12 h-12 mx-auto mb-2 opacity-50" />
                      <p>Error loading JVM info</p>
                      <p className="text-sm mt-1 text-obsidian-400">
                        {jvmError instanceof Error ? jvmError.message : 'Unknown error occurred'}
                      </p>
                    </div>
                  ) : jvmInfo ? (
                    <>
                      <div className="space-y-3 text-sm">
                        <InfraInfoRow 
                          label="JVM Name" 
                          value={jvmInfo.jvmName && jvmInfo.jvmName !== 'Unknown' ? jvmInfo.jvmName : '-'} 
                        />
                        <InfraInfoRow 
                          label="JVM Version" 
                          value={jvmInfo.jvmVersion && jvmInfo.jvmVersion !== 'Unknown' ? jvmInfo.jvmVersion : '-'} 
                        />
                        <InfraInfoRow 
                          label="JVM Vendor" 
                          value={jvmInfo.jvmVendor && jvmInfo.jvmVendor !== 'Unknown' ? jvmInfo.jvmVendor : '-'} 
                        />
                      </div>

                      {/* Memory Usage with Progress Bar */}
                      {jvmInfo.heapUsed && jvmInfo.heapMax && jvmInfo.heapMax > 0 && (
                        <div className="pt-4 border-t border-obsidian-800">
                          <div className="flex items-center justify-between text-sm mb-2">
                            <span className="text-obsidian-400">Heap Memory</span>
                            <span className="text-obsidian-200">
                              {formatBytes(jvmInfo.heapUsed)} / {formatBytes(jvmInfo.heapMax)}
                            </span>
                          </div>
                          <div className="h-3 bg-obsidian-800 rounded-full overflow-hidden">
                            <motion.div
                              className="h-full bg-gradient-to-r from-violet-600 to-violet-400 rounded-full"
                              initial={{ width: 0 }}
                              animate={{ width: `${(jvmInfo.heapUsed / jvmInfo.heapMax) * 100}%` }}
                              transition={{ duration: 1 }}
                            />
                          </div>
                          <div className="grid grid-cols-2 gap-4 mt-4 text-sm">
                            <div>
                              <p className="text-obsidian-500">Non-Heap</p>
                              <p className="text-obsidian-200 font-medium">
                                {jvmInfo.nonHeapUsed ? formatBytes(jvmInfo.nonHeapUsed) : '-'}
                              </p>
                            </div>
                            <div>
                              <p className="text-obsidian-500">Max</p>
                              <p className="text-obsidian-200 font-medium">{formatBytes(jvmInfo.heapMax)}</p>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* Thread Summary */}
                      {jvmInfo.threadCount && jvmInfo.threadCount > 0 && (
                        <div className="pt-4 border-t border-obsidian-800">
                          <div className="text-center p-4 bg-obsidian-800 rounded-lg">
                            <p className="text-2xl font-bold text-cyan-400">{jvmInfo.threadCount}</p>
                            <p className="text-sm text-obsidian-500">Active Threads</p>
                          </div>
                        </div>
                      )}

                      {/* Uptime */}
                      {jvmInfo.uptime && jvmInfo.uptime > 0 && (
                        <div className="pt-4 border-t border-obsidian-800">
                          <InfraInfoRow 
                            label="Uptime" 
                            value={formatDuration(jvmInfo.uptime)} 
                          />
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="text-center py-8 text-obsidian-500">
                      <Database className="w-12 h-12 mx-auto mb-2 opacity-50" />
                      <p>No JVM data available</p>
                      <p className="text-xs mt-1 text-obsidian-600">
                        The service may not be exposing JVM metrics via actuator endpoints.
                      </p>
                    </div>
                  )}
                </div>
              </Card>
            </div>
          )}
        </div>
      )}

      {/* Confirm Modal */}
      <Modal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        title={`Confirm ${pendingAction?.action}`}
        description={`Are you sure you want to ${pendingAction?.action} ${service.name}?`}
      >
        <div className="space-y-4">
          {pendingAction?.action !== 'delete' && (
            <div>
              <label className="block text-sm text-obsidian-400 mb-2">
                Reason (optional)
              </label>
              <input
                type="text"
                value={pendingAction?.reason || ''}
                onChange={(e) => setPendingAction(prev => prev ? { ...prev, reason: e.target.value } : null)}
                placeholder="Enter reason for this action..."
                className="w-full px-4 py-2 bg-obsidian-800 border border-obsidian-700 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500/50"
              />
            </div>
          )}
          {pendingAction?.action === 'delete' && (
            <p className="text-rose-400 text-sm">
              This action cannot be undone. All service data will be permanently deleted.
            </p>
          )}
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setShowConfirmModal(false)}>
              Cancel
            </Button>
            <Button
              variant={pendingAction?.action === 'delete' ? 'danger' : 'primary'}
              onClick={confirmAction}
              loading={stopMutation.isPending || restartMutation.isPending || deleteMutation.isPending}
            >
              Confirm {pendingAction?.action}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

function MetricCard({
  icon,
  label,
  value,
  color,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  color: 'cyan' | 'violet' | 'emerald' | 'rose' | 'amber';
}) {
  const colors = {
    cyan: 'from-cyan-500/20 to-cyan-500/5 border-cyan-500/30 text-cyan-400',
    violet: 'from-violet-500/20 to-violet-500/5 border-violet-500/30 text-violet-400',
    emerald: 'from-emerald-500/20 to-emerald-500/5 border-emerald-500/30 text-emerald-400',
    rose: 'from-rose-500/20 to-rose-500/5 border-rose-500/30 text-rose-400',
    amber: 'from-amber-500/20 to-amber-500/5 border-amber-500/30 text-amber-400',
  };

  return (
    <Card className={cn('bg-gradient-to-br border', colors[color])}>
      <div className="flex items-center gap-3">
        <div className={cn('p-2 rounded-lg bg-obsidian-900/50', colors[color].split(' ')[3])}>
          {icon}
        </div>
        <div>
          <p className="text-xs text-obsidian-500">{label}</p>
          <p className="text-xl font-bold text-obsidian-100">{value}</p>
        </div>
      </div>
    </Card>
  );
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-obsidian-500">{label}</span>
      <span className={cn('text-obsidian-200 truncate max-w-[200px]', mono && 'font-mono text-xs')}>
        {value}
      </span>
    </div>
  );
}

function InfraInfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-obsidian-500">{label}</span>
      <span className="text-obsidian-200 font-mono text-xs">{value}</span>
    </div>
  );
}

function InfraMetricCard({
  title,
  value,
  subtitle,
  icon,
  color,
}: {
  title: string;
  value: string;
  subtitle: string;
  icon: React.ReactNode;
  color: 'violet' | 'cyan' | 'emerald' | 'amber';
}) {
  const colors = {
    violet: 'from-violet-500/20 to-violet-500/5 border-l-violet-500 text-violet-400',
    cyan: 'from-cyan-500/20 to-cyan-500/5 border-l-cyan-500 text-cyan-400',
    emerald: 'from-emerald-500/20 to-emerald-500/5 border-l-emerald-500 text-emerald-400',
    amber: 'from-amber-500/20 to-amber-500/5 border-l-amber-500 text-amber-400',
  };

  const iconColors = {
    violet: 'text-violet-400',
    cyan: 'text-cyan-400',
    emerald: 'text-emerald-400',
    amber: 'text-amber-400',
  };

  return (
    <motion.div
      whileHover={{ scale: 1.02, y: -2 }}
      className={cn(
        'p-4 rounded-xl bg-gradient-to-br border-l-4 border border-obsidian-800/50',
        colors[color]
      )}
    >
      <div className="flex items-center justify-between">
        <div>
          <p className="text-obsidian-400 text-sm">{title}</p>
          <p className="text-2xl font-bold text-obsidian-100 mt-1">{value}</p>
          <p className="text-xs text-obsidian-500 mt-1">{subtitle}</p>
        </div>
        <div className={cn('p-2 rounded-lg bg-obsidian-900/60', iconColors[color])}>
          {icon}
        </div>
      </div>
    </motion.div>
  );
}

