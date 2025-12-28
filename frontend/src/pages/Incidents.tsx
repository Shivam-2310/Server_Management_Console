import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  CheckCircle,
  Clock,
  User,
  MessageSquare,
  Filter,
  Search,
  XCircle,
  AlertCircle,
  ArrowRight,
  Sparkles,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, Button, Badge, Modal, Input } from '@/components/ui';
import api from '@/lib/api';
import { cn, formatRelativeTime, formatDate, getSeverityColor } from '@/lib/utils';
import type { Incident, IncidentSeverity, IncidentStatus } from '@/types';

export function Incidents() {
  const queryClient = useQueryClient();
  const [filterStatus, setFilterStatus] = useState<IncidentStatus | 'ALL'>('ALL');
  const [filterSeverity, setFilterSeverity] = useState<IncidentSeverity | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [showResolveModal, setShowResolveModal] = useState(false);
  const [resolution, setResolution] = useState('');

  const { data: incidents, isLoading } = useQuery({
    queryKey: ['incidents'],
    queryFn: () => api.getActiveIncidents(),
    refetchInterval: 10000,
  });

  const acknowledgeMutation = useMutation({
    mutationFn: (id: number) => api.acknowledgeIncident(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['incidents'] });
    },
  });

  const resolveMutation = useMutation({
    mutationFn: ({ id, resolution }: { id: number; resolution: string }) =>
      api.resolveIncident(id, resolution),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['incidents'] });
      setShowResolveModal(false);
      setSelectedIncident(null);
      setResolution('');
    },
  });

  const filteredIncidents = (incidents || []).filter((incident: Incident) => {
    const matchesSearch = incident.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      incident.description?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesStatus = filterStatus === 'ALL' || incident.status === filterStatus;
    const matchesSeverity = filterSeverity === 'ALL' || incident.severity === filterSeverity;
    return matchesSearch && matchesStatus && matchesSeverity;
  });

  const statusCounts = (incidents || []).reduce((acc: Record<string, number>, inc: Incident) => {
    acc[inc.status] = (acc[inc.status] || 0) + 1;
    return acc;
  }, {});

  const severityCounts = (incidents || []).reduce((acc: Record<string, number>, inc: Incident) => {
    acc[inc.severity] = (acc[inc.severity] || 0) + 1;
    return acc;
  }, {});

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-obsidian-100">Incidents</h1>
          <p className="text-obsidian-400 mt-1">
            {incidents?.length || 0} active incidents
          </p>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-sm">
            {(severityCounts.CRITICAL || 0) > 0 && (
              <Badge variant="danger" className="animate-pulse">
                {severityCounts.CRITICAL} Critical
              </Badge>
            )}
            {(severityCounts.HIGH || 0) > 0 && (
              <Badge variant="warning">{severityCounts.HIGH} High</Badge>
            )}
          </div>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <SummaryCard
          title="Open"
          count={statusCounts.OPEN || 0}
          icon={<AlertCircle className="w-5 h-5" />}
          color="rose"
        />
        <SummaryCard
          title="Investigating"
          count={statusCounts.INVESTIGATING || 0}
          icon={<Search className="w-5 h-5" />}
          color="amber"
        />
        <SummaryCard
          title="Resolved"
          count={statusCounts.RESOLVED || 0}
          icon={<CheckCircle className="w-5 h-5" />}
          color="emerald"
        />
        <SummaryCard
          title="Critical"
          count={severityCounts.CRITICAL || 0}
          icon={<AlertTriangle className="w-5 h-5" />}
          color="violet"
          pulse={severityCounts.CRITICAL > 0}
        />
      </div>

      {/* Filters */}
      <div className="flex flex-col md:flex-row gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-obsidian-400" />
          <input
            type="text"
            placeholder="Search incidents..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 placeholder:text-obsidian-500 focus:outline-none focus:border-emerald-500"
          />
        </div>
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value as IncidentStatus | 'ALL')}
          className="px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500"
        >
          <option value="ALL">All Status</option>
          <option value="OPEN">Open</option>
          <option value="INVESTIGATING">Investigating</option>
          <option value="RESOLVED">Resolved</option>
          <option value="CLOSED">Closed</option>
        </select>
        <select
          value={filterSeverity}
          onChange={(e) => setFilterSeverity(e.target.value as IncidentSeverity | 'ALL')}
          className="px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500"
        >
          <option value="ALL">All Severity</option>
          <option value="CRITICAL">Critical</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
      </div>

      {/* Incidents List */}
      {isLoading ? (
        <div className="flex items-center justify-center min-h-[300px]">
          <div className="text-obsidian-400">Loading incidents...</div>
        </div>
      ) : filteredIncidents.length === 0 ? (
        <Card className="flex flex-col items-center justify-center py-16">
          <CheckCircle className="w-16 h-16 text-emerald-500 mb-4" />
          <p className="text-obsidian-300 text-lg mb-2">No incidents found</p>
          <p className="text-obsidian-400 text-sm">All systems operational</p>
        </Card>
      ) : (
        <div className="space-y-3">
          <AnimatePresence>
            {filteredIncidents.map((incident: Incident) => (
              <IncidentCard
                key={incident.id}
                incident={incident}
                onAcknowledge={() => acknowledgeMutation.mutate(incident.id)}
                onResolve={() => {
                  setSelectedIncident(incident);
                  setShowResolveModal(true);
                }}
              />
            ))}
          </AnimatePresence>
        </div>
      )}

      {/* Resolve Modal */}
      <Modal
        isOpen={showResolveModal}
        onClose={() => {
          setShowResolveModal(false);
          setSelectedIncident(null);
          setResolution('');
        }}
        title="Resolve Incident"
        description={selectedIncident?.title}
      >
        <div className="space-y-4">
          <div>
            <label className="block text-sm text-obsidian-300 mb-2">
              Resolution
            </label>
            <textarea
              value={resolution}
              onChange={(e) => setResolution(e.target.value)}
              placeholder="Describe how this incident was resolved..."
              rows={4}
              className="w-full px-4 py-3 bg-obsidian-800 border border-obsidian-700 rounded-lg text-obsidian-200 placeholder:text-obsidian-500 focus:outline-none focus:border-emerald-500 resize-none"
            />
          </div>
          <div className="flex justify-end gap-3">
            <Button
              variant="ghost"
              onClick={() => {
                setShowResolveModal(false);
                setSelectedIncident(null);
                setResolution('');
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={() => {
                if (selectedIncident) {
                  resolveMutation.mutate({ id: selectedIncident.id, resolution });
                }
              }}
              loading={resolveMutation.isPending}
              disabled={!resolution.trim()}
            >
              Resolve Incident
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

function SummaryCard({
  title,
  count,
  icon,
  color,
  pulse,
}: {
  title: string;
  count: number;
  icon: React.ReactNode;
  color: 'rose' | 'amber' | 'emerald' | 'cyan' | 'violet';
  pulse?: boolean;
}) {
  const colorClasses = {
    rose: 'from-rose-500/20 to-rose-500/5 border-l-rose-500',
    amber: 'from-amber-500/20 to-amber-500/5 border-l-amber-500',
    emerald: 'from-emerald-500/20 to-emerald-500/5 border-l-emerald-500',
    cyan: 'from-cyan-500/20 to-cyan-500/5 border-l-cyan-500',
    violet: 'from-violet-500/20 to-violet-500/5 border-l-violet-500',
  };

  const iconColors = {
    rose: 'text-rose-400',
    amber: 'text-amber-400',
    emerald: 'text-emerald-400',
    cyan: 'text-cyan-400',
    violet: 'text-violet-400',
  };

  return (
    <motion.div
      whileHover={{ scale: 1.02, y: -2 }}
      className={cn(
        'p-4 rounded-xl bg-gradient-to-br border-l-4 border border-obsidian-800/50',
        colorClasses[color],
        pulse && 'animate-pulse'
      )}
    >
      <div className="flex items-center justify-between">
        <div>
          <p className="text-obsidian-400 text-sm">{title}</p>
          <p className="text-2xl font-bold text-obsidian-100 mt-1">{count}</p>
        </div>
        <div className={cn('p-2 rounded-lg bg-obsidian-900/60', iconColors[color])}>
          {icon}
        </div>
      </div>
    </motion.div>
  );
}

function IncidentCard({
  incident,
  onAcknowledge,
  onResolve,
}: {
  incident: Incident;
  onAcknowledge: () => void;
  onResolve: () => void;
}) {
  const statusIcons = {
    OPEN: AlertCircle,
    INVESTIGATING: Search,
    RESOLVED: CheckCircle,
    CLOSED: XCircle,
  };
  const StatusIcon = statusIcons[incident.status as keyof typeof statusIcons] || AlertCircle;

  const severityColors = {
    CRITICAL: 'from-rose-500/20 to-rose-500/5 border-l-rose-500',
    HIGH: 'from-orange-500/20 to-orange-500/5 border-l-orange-500',
    MEDIUM: 'from-amber-500/20 to-amber-500/5 border-l-amber-500',
    LOW: 'from-cyan-500/20 to-cyan-500/5 border-l-cyan-500',
  };

  return (
    <motion.div
      layout
      className={cn(
        'p-5 rounded-xl bg-gradient-to-br border-l-4 border border-obsidian-800/50 transition-all',
        severityColors[incident.severity as keyof typeof severityColors] || 'from-obsidian-800/50 to-obsidian-900/50 border-l-obsidian-600'
      )}
    >
      <div className="flex items-start gap-4">
        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-4">
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-2">
                <Badge className={getSeverityColor(incident.severity)} size="sm">
                  {incident.severity}
                </Badge>
                <Badge variant="outline" size="sm">
                  <StatusIcon className="w-3 h-3 mr-1" />
                  {incident.status}
                </Badge>
              </div>
              <h3 className="text-lg font-semibold text-obsidian-100 mb-1">
                {incident.title}
              </h3>
              <p className="text-sm text-obsidian-300 line-clamp-2">
                {incident.description}
              </p>
            </div>
          </div>

          {/* AI Summary */}
          {incident.aiSummary && (
            <div className="mt-4 p-3 bg-violet-500/20 border border-violet-500/30 rounded-lg">
              <div className="flex items-center gap-2 text-violet-400 text-xs font-medium mb-1">
                <Sparkles className="w-3 h-3" />
                AI Summary
              </div>
              <p className="text-sm text-obsidian-200 line-clamp-2">{incident.aiSummary}</p>
            </div>
          )}

          {/* Meta & Actions */}
          <div className="flex items-center justify-between mt-4 pt-4 border-t border-obsidian-800/50">
            <div className="flex items-center gap-4 text-xs text-obsidian-400">
              <span className="flex items-center gap-1">
                <Clock className="w-3 h-3" />
                {formatRelativeTime(incident.createdAt)}
              </span>
              {incident.serviceName && (
                <Link
                  to={`/services/${incident.serviceId}`}
                  className="flex items-center gap-1 text-emerald-400 hover:text-emerald-300"
                >
                  {incident.serviceName}
                  <ArrowRight className="w-3 h-3" />
                </Link>
              )}
              {incident.acknowledgedBy && (
                <span className="flex items-center gap-1">
                  <User className="w-3 h-3" />
                  {incident.acknowledgedBy}
                </span>
              )}
            </div>

            {/* Actions */}
            {incident.status === 'OPEN' && (
              <div className="flex items-center gap-2">
                <Button variant="ghost" size="sm" onClick={onAcknowledge}>
                  Acknowledge
                </Button>
                <Button size="sm" onClick={onResolve}>
                  Resolve
                </Button>
              </div>
            )}
            {incident.status === 'INVESTIGATING' && (
              <Button size="sm" onClick={onResolve}>
                Resolve
              </Button>
            )}
          </div>
        </div>
      </div>
    </motion.div>
  );
}
