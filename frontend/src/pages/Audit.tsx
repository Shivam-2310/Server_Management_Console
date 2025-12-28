import { useState } from 'react';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import {
  Shield,
  Search,
  Clock,
  User,
  Server,
  CheckCircle,
  XCircle,
  Zap,
  Filter,
  Download,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, Badge, Button } from '@/components/ui';
import api from '@/lib/api';
import { cn, formatRelativeTime, formatDate, getActionColor, formatDuration } from '@/lib/utils';
import type { AuditLog, ServiceAction, ActionStatus } from '@/types';

export function Audit() {
  const [filterAction, setFilterAction] = useState<ServiceAction | 'ALL'>('ALL');
  const [filterStatus, setFilterStatus] = useState<ActionStatus | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  const { data: auditLogs, isLoading } = useQuery({
    queryKey: ['auditLogs'],
    queryFn: () => api.getRecentActions(72),
    refetchInterval: 30000,
  });

  const filteredLogs = (auditLogs || []).filter((log: AuditLog) => {
    const matchesSearch = 
      log.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
      log.serviceName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      log.action.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesAction = filterAction === 'ALL' || log.action === filterAction;
    const matchesStatus = filterStatus === 'ALL' || log.status === filterStatus;
    return matchesSearch && matchesAction && matchesStatus;
  });

  const actionCounts = (auditLogs || []).reduce((acc: Record<string, number>, log: AuditLog) => {
    acc[log.action] = (acc[log.action] || 0) + 1;
    return acc;
  }, {});

  const successCount = (auditLogs || []).filter((l: AuditLog) => l.status === 'SUCCESS').length;
  const failedCount = (auditLogs || []).filter((l: AuditLog) => l.status === 'FAILED').length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-obsidian-100">Audit Trail</h1>
          <p className="text-obsidian-400 mt-1">
            Complete history of system actions
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          icon={<Download className="w-4 h-4" />}
        >
          Export
        </Button>
      </div>

      {/* Summary Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          title="Total Actions"
          value={auditLogs?.length || 0}
          icon={<Shield className="w-5 h-5" />}
          color="violet"
        />
        <StatCard
          title="Successful"
          value={successCount}
          icon={<CheckCircle className="w-5 h-5" />}
          color="emerald"
        />
        <StatCard
          title="Failed"
          value={failedCount}
          icon={<XCircle className="w-5 h-5" />}
          color="rose"
        />
        <StatCard
          title="Restarts"
          value={actionCounts.RESTART || 0}
          icon={<Zap className="w-5 h-5" />}
          color="amber"
        />
      </div>

      {/* Filters */}
      <div className="flex flex-col md:flex-row gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-obsidian-400" />
          <input
            type="text"
            placeholder="Search by user, service, or action..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 placeholder:text-obsidian-500 focus:outline-none focus:border-emerald-500"
          />
        </div>
        <select
          value={filterAction}
          onChange={(e) => setFilterAction(e.target.value as ServiceAction | 'ALL')}
          className="px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500"
        >
          <option value="ALL">All Actions</option>
          <option value="START">Start</option>
          <option value="STOP">Stop</option>
          <option value="RESTART">Restart</option>
          <option value="SCALE_UP">Scale Up</option>
          <option value="SCALE_DOWN">Scale Down</option>
        </select>
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value as ActionStatus | 'ALL')}
          className="px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500"
        >
          <option value="ALL">All Status</option>
          <option value="SUCCESS">Success</option>
          <option value="FAILED">Failed</option>
          <option value="PENDING">Pending</option>
        </select>
      </div>

      {/* Audit Log Table */}
      {isLoading ? (
        <div className="flex items-center justify-center min-h-[300px]">
          <div className="text-obsidian-400">Loading audit logs...</div>
        </div>
      ) : filteredLogs.length === 0 ? (
        <Card className="flex flex-col items-center justify-center py-16">
          <Shield className="w-16 h-16 text-obsidian-600 mb-4" />
          <p className="text-obsidian-300 text-lg mb-2">No audit logs found</p>
          <p className="text-obsidian-400 text-sm">No actions match your filters</p>
        </Card>
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-obsidian-800">
                  <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-400 uppercase tracking-wider">
                    Time
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-400 uppercase tracking-wider">
                    User
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-400 uppercase tracking-wider">
                    Action
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-400 uppercase tracking-wider">
                    Service
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-400 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-400 uppercase tracking-wider">
                    Duration
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-400 uppercase tracking-wider">
                    Details
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-obsidian-800">
                {filteredLogs.map((log: AuditLog) => (
                  <motion.tr
                    key={log.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="hover:bg-obsidian-800 transition-colors"
                  >
                    <td className="px-4 py-4 whitespace-nowrap">
                      <div className="flex items-center gap-2 text-sm">
                        <Clock className="w-4 h-4 text-obsidian-400" />
                        <div>
                          <p className="text-obsidian-200">{formatRelativeTime(log.timestamp)}</p>
                          <p className="text-xs text-obsidian-400">{formatDate(log.timestamp)}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap">
                      <div className="flex items-center gap-2">
                        <div className="w-8 h-8 rounded-lg bg-obsidian-700 flex items-center justify-center">
                          <User className="w-4 h-4 text-obsidian-300" />
                        </div>
                        <div>
                          <p className="text-sm font-medium text-obsidian-200">{log.username}</p>
                          {log.ipAddress && (
                            <p className="text-xs text-obsidian-400 font-mono">{log.ipAddress}</p>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap">
                      <Badge
                        className={cn(
                          'font-medium',
                          log.action === 'START' && 'bg-emerald-600 text-white border-emerald-500',
                          log.action === 'STOP' && 'bg-rose-600 text-white border-rose-500',
                          log.action === 'RESTART' && 'bg-amber-600 text-white border-amber-500',
                          log.action === 'SCALE_UP' && 'bg-cyan-600 text-white border-cyan-500',
                          log.action === 'SCALE_DOWN' && 'bg-violet-600 text-white border-violet-500'
                        )}
                      >
                        {log.action}
                      </Badge>
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap">
                      {log.serviceName ? (
                        <div className="flex items-center gap-2 text-sm text-obsidian-200">
                          <Server className="w-4 h-4 text-obsidian-400" />
                          {log.serviceName}
                        </div>
                      ) : (
                        <span className="text-obsidian-400">-</span>
                      )}
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap">
                      <Badge
                        variant={log.status === 'SUCCESS' ? 'success' : log.status === 'FAILED' ? 'danger' : 'warning'}
                      >
                        {log.status === 'SUCCESS' && <CheckCircle className="w-3 h-3 mr-1" />}
                        {log.status === 'FAILED' && <XCircle className="w-3 h-3 mr-1" />}
                        {log.status}
                      </Badge>
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap text-sm text-obsidian-300">
                      {log.durationMs ? formatDuration(log.durationMs) : '-'}
                    </td>
                    <td className="px-4 py-4 max-w-[200px]">
                      {log.resultMessage ? (
                        <p className="text-sm text-obsidian-300 truncate" title={log.resultMessage}>
                          {log.resultMessage}
                        </p>
                      ) : log.reason ? (
                        <p className="text-sm text-obsidian-300 truncate" title={log.reason}>
                          {log.reason}
                        </p>
                      ) : (
                        <span className="text-obsidian-400">-</span>
                      )}
                    </td>
                  </motion.tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
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
  color: 'violet' | 'emerald' | 'rose' | 'amber' | 'cyan';
}) {
  const colorClasses = {
    violet: 'from-violet-500/20 to-violet-500/5 border-l-violet-500',
    emerald: 'from-emerald-500/20 to-emerald-500/5 border-l-emerald-500',
    rose: 'from-rose-500/20 to-rose-500/5 border-l-rose-500',
    amber: 'from-amber-500/20 to-amber-500/5 border-l-amber-500',
    cyan: 'from-cyan-500/20 to-cyan-500/5 border-l-cyan-500',
  };

  const iconColors = {
    violet: 'text-violet-400',
    emerald: 'text-emerald-400',
    rose: 'text-rose-400',
    amber: 'text-amber-400',
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
