import { useState, useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import {
  FileText,
  Search,
  Filter,
  Download,
  RefreshCw,
  Terminal,
  AlertCircle,
  AlertTriangle,
  Info,
  Bug,
  ChevronDown,
  Layers,
  Activity,
  Zap,
} from 'lucide-react';
import { Card, Button, Badge } from '@/components/ui';
import api from '@/lib/api';
import { cn, formatDate } from '@/lib/utils';
import type { LogEntry, LogResponse } from '@/types';

const LOG_CATEGORIES = [
  { id: 'unified', label: 'All Logs', icon: Terminal },
  { id: 'lifecycle', label: 'Lifecycle', icon: RefreshCw },
  { id: 'health', label: 'Health Checks', icon: AlertCircle },
  { id: 'errors', label: 'Errors', icon: Bug },
  { id: 'ai', label: 'AI Analysis', icon: Info },
  { id: 'incidents', label: 'Incidents', icon: AlertTriangle },
];

const LOG_LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

export function Logs() {
  const [searchParams] = useSearchParams();
  const initialSearch = searchParams.get('search') || '';
  
  const [category, setCategory] = useState('unified');
  const [level, setLevel] = useState('INFO');
  const [searchQuery, setSearchQuery] = useState(initialSearch);
  const [lines, setLines] = useState(200);
  const [autoScroll, setAutoScroll] = useState(true);
  const logContainerRef = useRef<HTMLDivElement>(null);

  const { data: logs, isLoading, refetch } = useQuery({
    queryKey: ['logs', category, level, lines],
    queryFn: () => {
      if (category === 'unified') {
        return api.getUnifiedLogs(lines, level);
      }
      return api.getLogsByCategory(category, lines);
    },
    refetchInterval: 5000,
  });

  const { data: searchResults } = useQuery({
    queryKey: ['logsSearch', searchQuery],
    queryFn: () => api.searchLogs(searchQuery, 100),
    enabled: searchQuery.length > 2,
  });

  const displayLogs = searchQuery.length > 2 ? searchResults : logs;

  useEffect(() => {
    if (autoScroll && logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [displayLogs, autoScroll]);

  const filteredEntries = (displayLogs?.entries || []).filter((entry: LogEntry) => {
    if (!searchQuery) return true;
    return (
      entry.message.toLowerCase().includes(searchQuery.toLowerCase()) ||
      entry.logger?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      entry.serviceName?.toLowerCase().includes(searchQuery.toLowerCase())
    );
  });

  const getLevelColor = (logLevel: string) => {
    switch (logLevel?.toUpperCase()) {
      case 'ERROR':
        return 'text-rose-400 bg-rose-600';
      case 'WARN':
        return 'text-amber-400 bg-amber-600';
      case 'INFO':
        return 'text-cyan-400 bg-cyan-600';
      case 'DEBUG':
        return 'text-obsidian-300 bg-obsidian-600';
      case 'TRACE':
        return 'text-obsidian-400 bg-obsidian-700';
      default:
        return 'text-obsidian-300 bg-obsidian-600';
    }
  };

  // Calculate stats
  const errorCount = filteredEntries.filter((e: LogEntry) => e.level === 'ERROR').length;
  const warnCount = filteredEntries.filter((e: LogEntry) => e.level === 'WARN').length;
  const infoCount = filteredEntries.filter((e: LogEntry) => e.level === 'INFO').length;

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="space-y-6"
    >
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-obsidian-100">Logs</h1>
          <p className="text-obsidian-400 mt-1">
            {displayLogs?.totalCount || 0} log entries
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant={autoScroll ? 'primary' : 'ghost'}
            size="sm"
            onClick={() => setAutoScroll(!autoScroll)}
          >
            {autoScroll ? 'Auto-scroll ON' : 'Auto-scroll OFF'}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => refetch()}
            icon={<RefreshCw className="w-4 h-4" />}
          >
            Refresh
          </Button>
          <Button
            variant="secondary"
            size="sm"
            icon={<Download className="w-4 h-4" />}
          >
            Export
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          title="Total Entries"
          value={filteredEntries.length}
          icon={<Layers className="w-5 h-5" />}
          color="violet"
        />
        <StatCard
          title="Errors"
          value={errorCount}
          icon={<Bug className="w-5 h-5" />}
          color="rose"
        />
        <StatCard
          title="Warnings"
          value={warnCount}
          icon={<AlertTriangle className="w-5 h-5" />}
          color="amber"
        />
        <StatCard
          title="Info"
          value={infoCount}
          icon={<Info className="w-5 h-5" />}
          color="cyan"
        />
      </div>

      {/* Category Tabs */}
      <div className="flex items-center gap-2 overflow-x-auto pb-2">
        {LOG_CATEGORIES.map((cat) => {
          const Icon = cat.icon;
          return (
            <button
              key={cat.id}
              onClick={() => setCategory(cat.id)}
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap',
                category === cat.id
                  ? 'bg-emerald-600 text-white border border-emerald-500'
                  : 'bg-obsidian-800 text-obsidian-300 border border-obsidian-700 hover:border-obsidian-600'
              )}
            >
              <Icon className="w-4 h-4" />
              {cat.label}
            </button>
          );
        })}
      </div>

      {/* Filters */}
      <div className="flex flex-col md:flex-row gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-obsidian-400" />
          <input
            type="text"
            placeholder="Search logs..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 placeholder:text-obsidian-500 focus:outline-none focus:border-emerald-500 font-mono text-sm"
          />
        </div>
        <select
          value={level}
          onChange={(e) => setLevel(e.target.value)}
          className="px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500"
        >
          {LOG_LEVELS.map((l) => (
            <option key={l} value={l}>
              {l}+
            </option>
          ))}
        </select>
        <select
          value={lines}
          onChange={(e) => setLines(Number(e.target.value))}
          className="px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500"
        >
          <option value={100}>100 lines</option>
          <option value={200}>200 lines</option>
          <option value={500}>500 lines</option>
          <option value={1000}>1000 lines</option>
        </select>
      </div>

      {/* Log Viewer */}
      <Card className="p-0 overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-obsidian-800 bg-obsidian-900">
          <div className="flex items-center gap-2 text-sm text-obsidian-300">
            <Terminal className="w-4 h-4" />
            <span className="font-mono">
              {category === 'unified' ? 'All Services' : category}
            </span>
          </div>
          <div className="flex items-center gap-4 text-xs text-obsidian-400">
            <span>{filteredEntries.length} entries</span>
            {displayLogs?.category === 'search' && (
              <Badge variant="info" size="sm">Search Results</Badge>
            )}
          </div>
        </div>

        <div
          ref={logContainerRef}
          className="h-[600px] overflow-y-auto font-mono text-xs bg-obsidian-950"
        >
          {isLoading ? (
            <div className="flex items-center justify-center h-full text-obsidian-400">
              Loading logs...
            </div>
          ) : filteredEntries.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-obsidian-400">
              <FileText className="w-12 h-12 mb-2" />
              <p>No log entries found</p>
            </div>
          ) : (
            <div className="p-2 space-y-0.5">
              {filteredEntries.map((entry: LogEntry, index: number) => (
                <LogLine key={index} entry={entry} getLevelColor={getLevelColor} />
              ))}
            </div>
          )}
        </div>
      </Card>
    </motion.div>
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
  color: 'violet' | 'rose' | 'amber' | 'cyan' | 'emerald';
}) {
  const colorClasses = {
    violet: 'from-violet-500/20 to-violet-500/5 border-l-violet-500',
    rose: 'from-rose-500/20 to-rose-500/5 border-l-rose-500',
    amber: 'from-amber-500/20 to-amber-500/5 border-l-amber-500',
    cyan: 'from-cyan-500/20 to-cyan-500/5 border-l-cyan-500',
    emerald: 'from-emerald-500/20 to-emerald-500/5 border-l-emerald-500',
  };

  const iconColors = {
    violet: 'text-violet-400',
    rose: 'text-rose-400',
    amber: 'text-amber-400',
    cyan: 'text-cyan-400',
    emerald: 'text-emerald-400',
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

function LogLine({
  entry,
  getLevelColor,
}: {
  entry: LogEntry;
  getLevelColor: (level: string) => string;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className={cn(
        'group px-3 py-1.5 rounded hover:bg-obsidian-900 transition-colors',
        entry.level === 'ERROR' && 'bg-rose-950/50',
        entry.level === 'WARN' && 'bg-amber-950/50'
      )}
    >
      <div className="flex items-start gap-3">
        {/* Timestamp */}
        <span className="text-obsidian-400 whitespace-nowrap flex-shrink-0">
          {entry.timestamp ? formatDate(entry.timestamp).split(' ')[1] : '--:--:--'}
        </span>

        {/* Level */}
        <span
          className={cn(
            'px-1.5 py-0.5 rounded text-[10px] font-bold uppercase flex-shrink-0 text-white',
            getLevelColor(entry.level)
          )}
        >
          {entry.level?.slice(0, 4) || 'INFO'}
        </span>

        {/* Service */}
        {entry.serviceName && (
          <span className="text-cyan-400 flex-shrink-0">[{entry.serviceName}]</span>
        )}

        {/* Logger */}
        <span className="text-violet-400 flex-shrink-0 max-w-[150px] truncate">
          {entry.logger?.split('.').pop() || 'App'}
        </span>

        {/* Message */}
        <span
          className={cn(
            'text-obsidian-200 flex-1',
            !expanded && 'truncate'
          )}
        >
          {entry.message}
        </span>

        {/* Expand Button */}
        {entry.message?.length > 100 && (
          <button
            onClick={() => setExpanded(!expanded)}
            className="text-obsidian-400 hover:text-obsidian-200 transition-colors"
          >
            <ChevronDown className={cn('w-4 h-4 transition-transform', expanded && 'rotate-180')} />
          </button>
        )}
      </div>

      {/* Exception */}
      {entry.exception && expanded && (
        <pre className="mt-2 p-2 bg-obsidian-900 rounded text-rose-400 text-[10px] overflow-x-auto">
          {entry.exception}
        </pre>
      )}
    </motion.div>
  );
}
