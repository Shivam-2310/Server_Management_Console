import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Server,
  Play,
  Square,
  RefreshCw,
  ArrowUp,
  ArrowDown,
  Activity,
  Cpu,
  MemoryStick,
  Clock,
  Zap,
  AlertTriangle,
  ChevronLeft,
  ExternalLink,
  Sparkles,
  Shield,
  Trash2,
  Settings,
} from 'lucide-react';
import {
  LineChart,
  Line,
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
  formatDate,
  getHealthColor,
  getHealthBgColor,
  getRiskColor,
  formatBytes,
  formatDuration,
} from '@/lib/utils';
import type { Metrics, HealthCheck, AIAnalysis } from '@/types';

export function ServiceDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuthStore();
  const serviceId = Number(id);

  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [pendingAction, setPendingAction] = useState<{
    action: 'stop' | 'restart' | 'delete';
    reason: string;
  } | null>(null);

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

  const { data: healthChecks } = useQuery({
    queryKey: ['healthChecks', serviceId],
    queryFn: () => api.getHealthChecks(serviceId, 24),
    refetchInterval: 30000,
  });

  const { data: analysis } = useQuery({
    queryKey: ['analysis', serviceId],
    queryFn: () => api.getServiceAnalysis(serviceId),
    refetchInterval: 60000,
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

