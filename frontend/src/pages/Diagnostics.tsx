import { useState } from 'react';
import { motion } from 'framer-motion';
import { useQuery, useMutation } from '@tanstack/react-query';
import {
  Activity,
  Cpu,
  HardDrive,
  Layers,
  RefreshCw,
  Terminal,
  Gauge,
  MemoryStick,
  Timer,
  Network,
  Download,
  Trash2,
  Play,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, Button, Badge } from '@/components/ui';
import { cn, formatBytes, formatDuration, formatUptime } from '@/lib/utils';
import { api } from '@/lib/api';

export function Diagnostics() {
  const [activeTab, setActiveTab] = useState<'jvm' | 'http' | 'loggers' | 'env'>('jvm');

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-obsidian-100">Diagnostics</h1>
          <p className="text-obsidian-400 mt-1">
            JVM metrics, HTTP traces, and environment info
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-2 border-b border-obsidian-800 pb-4">
        {[
          { id: 'jvm', label: 'JVM', icon: Cpu },
          { id: 'http', label: 'HTTP Traces', icon: Network },
          { id: 'loggers', label: 'Loggers', icon: Terminal },
          { id: 'env', label: 'Environment', icon: HardDrive },
        ].map((tab) => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as typeof activeTab)}
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all',
                activeTab === tab.id
                  ? 'bg-emerald-600 text-white border border-emerald-500'
                  : 'text-obsidian-400 hover:text-obsidian-200 hover:bg-obsidian-800'
              )}
            >
              <Icon className="w-4 h-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Tab Content */}
      {activeTab === 'jvm' && <JVMDiagnostics />}
      {activeTab === 'http' && <HTTPTraces />}
      {activeTab === 'loggers' && <LoggerManagement />}
      {activeTab === 'env' && <EnvironmentInfo />}
    </div>
  );
}

function JVMDiagnostics() {
  // Fetch real JVM data from API
  const { data: jvmInfo, isLoading: jvmLoading } = useQuery({
    queryKey: ['jvm-info'],
    queryFn: () => api.getJvmInfo(),
    refetchInterval: 5000, // Refresh every 5 seconds
  });

  const { data: heapInfo, isLoading: heapLoading } = useQuery({
    queryKey: ['heap-info'],
    queryFn: () => api.getHeapInfo(),
    refetchInterval: 5000,
  });

  const { data: threadInfo, isLoading: threadLoading } = useQuery({
    queryKey: ['thread-dump'],
    queryFn: () => api.getThreadDump(),
    refetchInterval: 5000,
  });

  const gcMutation = useMutation({
    mutationFn: () => api.requestGC(),
    onSuccess: () => {
      // Refetch heap info after GC
      setTimeout(() => {
        window.location.reload();
      }, 1000);
    },
  });

  if (jvmLoading || heapLoading || threadLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <RefreshCw className="w-6 h-6 animate-spin text-obsidian-400" />
      </div>
    );
  }

  // Extract values from API responses
  const javaVersion = jvmInfo?.systemProperties?.['java.version'] || jvmInfo?.vmVersion || 'Unknown';
  const javaVendor = jvmInfo?.vmVendor || 'Unknown';
  const vmName = jvmInfo?.vmName || 'Unknown';
  const vmVersion = jvmInfo?.vmVersion || 'Unknown';
  const osName = jvmInfo?.osName || 'Unknown';
  const osArch = jvmInfo?.osArch || 'Unknown';
  const processors = jvmInfo?.availableProcessors || 0;
  const uptimeMs = jvmInfo?.uptime || 0;
  const uptimeSeconds = Math.floor(uptimeMs / 1000); // Convert milliseconds to seconds

  const heapUsed = heapInfo?.heapUsed || 0;
  const heapCommitted = heapInfo?.heapCommitted || 0;
  const heapMax = heapInfo?.heapMax || 0;
  const usedPercent = heapMax > 0 ? (heapUsed / heapMax) * 100 : 0;

  const totalThreads = threadInfo?.totalThreads || 0;
  const peakThreads = threadInfo?.peakThreadCount || 0;
  const daemonThreads = threadInfo?.daemonThreadCount || threadInfo?.threads?.filter(t => t.daemon).length || 0;
  const startedThreads = threadInfo?.totalStartedThreadCount || 0;

  return (
    <div className="space-y-6">
      {/* Quick Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <MetricCard
          title="Heap Used"
          value={formatBytes(heapUsed)}
          subtitle={`${usedPercent.toFixed(1)}% of max`}
          icon={<MemoryStick className="w-5 h-5" />}
          color="violet"
        />
        <MetricCard
          title="Threads"
          value={totalThreads.toString()}
          subtitle={`${daemonThreads} daemon`}
          icon={<Layers className="w-5 h-5" />}
          color="cyan"
        />
        <MetricCard
          title="Processors"
          value={processors.toString()}
          subtitle={osArch}
          icon={<Cpu className="w-5 h-5" />}
          color="emerald"
        />
        <MetricCard
          title="Uptime"
          value={formatUptime(uptimeSeconds)}
          subtitle="Since last restart"
          icon={<Timer className="w-5 h-5" />}
          color="amber"
        />
      </div>

      {/* JVM Info & Heap */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Cpu className="w-5 h-5 text-cyan-400" />
              JVM Information
            </CardTitle>
          </CardHeader>
          <div className="space-y-3 text-sm">
            <InfoRow label="Java Version" value={javaVersion} />
            <InfoRow label="Vendor" value={javaVendor} />
            <InfoRow label="VM Name" value={vmName} />
            <InfoRow label="VM Version" value={vmVersion} />
            <InfoRow label="OS" value={`${osName} (${osArch})`} />
          </div>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <MemoryStick className="w-5 h-5 text-violet-400" />
              Memory Usage
            </CardTitle>
            <Button 
              variant="ghost" 
              size="sm" 
              icon={<Trash2 className="w-4 h-4" />}
              onClick={() => gcMutation.mutate()}
              disabled={gcMutation.isPending}
            >
              {gcMutation.isPending ? 'Requesting...' : 'Request GC'}
            </Button>
          </CardHeader>
          <div className="space-y-4">
            <div>
              <div className="flex items-center justify-between text-sm mb-2">
                <span className="text-obsidian-400">Heap Memory</span>
                <span className="text-obsidian-200">{formatBytes(heapUsed)} / {formatBytes(heapMax)}</span>
              </div>
              <div className="h-3 bg-obsidian-800 rounded-full overflow-hidden">
                <motion.div
                  className="h-full bg-gradient-to-r from-violet-600 to-violet-400 rounded-full"
                  initial={{ width: 0 }}
                  animate={{ width: `${usedPercent}%` }}
                  transition={{ duration: 1 }}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <p className="text-obsidian-500">Committed</p>
                <p className="text-obsidian-200 font-medium">{formatBytes(heapCommitted)}</p>
              </div>
              <div>
                <p className="text-obsidian-500">Max</p>
                <p className="text-obsidian-200 font-medium">{formatBytes(heapMax)}</p>
              </div>
            </div>
          </div>
        </Card>
      </div>

      {/* Thread Info */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="flex items-center gap-2">
            <Layers className="w-5 h-5 text-cyan-400" />
            Thread Summary
          </CardTitle>
          <Button 
            variant="secondary" 
            size="sm" 
            icon={<Download className="w-4 h-4" />}
            onClick={async () => {
              try {
                const blob = await api.downloadThreadDump();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `thread-dump-${new Date().toISOString()}.txt`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);
              } catch (error) {
                console.error('Failed to download thread dump:', error);
              }
            }}
          >
            Download Thread Dump
          </Button>
        </CardHeader>
        <div className="grid grid-cols-4 gap-4">
          <div className="text-center p-4 bg-obsidian-800 rounded-lg">
            <p className="text-2xl font-bold text-obsidian-100">{totalThreads}</p>
            <p className="text-sm text-obsidian-500">Total</p>
          </div>
          <div className="text-center p-4 bg-obsidian-800 rounded-lg">
            <p className="text-2xl font-bold text-cyan-400">{daemonThreads}</p>
            <p className="text-sm text-obsidian-500">Daemon</p>
          </div>
          <div className="text-center p-4 bg-obsidian-800 rounded-lg">
            <p className="text-2xl font-bold text-amber-400">{peakThreads}</p>
            <p className="text-sm text-obsidian-500">Peak</p>
          </div>
          <div className="text-center p-4 bg-obsidian-800 rounded-lg">
            <p className="text-2xl font-bold text-violet-400">{startedThreads}</p>
            <p className="text-sm text-obsidian-500">Started</p>
          </div>
        </div>
      </Card>
    </div>
  );
}

function HTTPTraces() {
  const { data: traces = [], isLoading, refetch } = useQuery({
    queryKey: ['http-traces'],
    queryFn: () => api.getHttpTraces(100),
    refetchInterval: 3000, // Refresh every 3 seconds
  });

  const clearMutation = useMutation({
    mutationFn: () => api.clearHttpTraces(),
    onSuccess: () => {
      refetch();
    },
  });

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Network className="w-5 h-5 text-emerald-400" />
            Recent HTTP Requests
          </CardTitle>
        </CardHeader>
        <div className="flex items-center justify-center p-8">
          <RefreshCw className="w-6 h-6 animate-spin text-obsidian-400" />
        </div>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2">
          <Network className="w-5 h-5 text-emerald-400" />
          Recent HTTP Requests
        </CardTitle>
        <Button 
          variant="ghost" 
          size="sm" 
          icon={<Trash2 className="w-4 h-4" />}
          onClick={() => clearMutation.mutate()}
          disabled={clearMutation.isPending}
        >
          {clearMutation.isPending ? 'Clearing...' : 'Clear'}
        </Button>
      </CardHeader>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-obsidian-800">
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Method</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">URI</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Status</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Time</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Timestamp</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-obsidian-800">
            {traces.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-obsidian-400">
                  No HTTP traces available
                </td>
              </tr>
            ) : (
              traces.map((trace, i) => (
                <tr key={i} className="hover:bg-obsidian-800">
                  <td className="px-4 py-3">
                    <Badge
                      variant={trace.method === 'GET' ? 'info' : trace.method === 'POST' ? 'success' : 'warning'}
                      size="sm"
                    >
                      {trace.method}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-sm text-obsidian-200 font-mono">{trace.uri}</td>
                  <td className="px-4 py-3">
                    <Badge variant={trace.status < 400 ? 'success' : 'danger'} size="sm">
                      {trace.status}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-sm text-obsidian-400">{Math.round(trace.timeTaken)}ms</td>
                  <td className="px-4 py-3 text-sm text-obsidian-500">
                    {new Date(trace.timestamp).toLocaleTimeString()}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function LoggerManagement() {
  const { data: loggers = [], isLoading, refetch } = useQuery({
    queryKey: ['loggers'],
    queryFn: () => api.getLoggers(),
  });

  const { data: availableLevels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'] } = useQuery({
    queryKey: ['logger-levels'],
    queryFn: () => api.getAvailableLogLevels(),
  });

  const setLevelMutation = useMutation({
    mutationFn: ({ loggerName, level }: { loggerName: string; level: string }) =>
      api.setLoggerLevel(loggerName, level),
    onSuccess: () => {
      refetch();
    },
  });

  const resetLevelMutation = useMutation({
    mutationFn: (loggerName: string) => api.resetLoggerLevel(loggerName),
    onSuccess: () => {
      refetch();
    },
  });

  const handleLevelChange = (loggerName: string, newLevel: string) => {
    if (newLevel === 'RESET') {
      resetLevelMutation.mutate(loggerName);
    } else {
      setLevelMutation.mutate({ loggerName, level: newLevel });
    }
  };

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Terminal className="w-5 h-5 text-amber-400" />
            Logger Configuration
          </CardTitle>
        </CardHeader>
        <div className="flex items-center justify-center p-8">
          <RefreshCw className="w-6 h-6 animate-spin text-obsidian-400" />
        </div>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Terminal className="w-5 h-5 text-amber-400" />
          Logger Configuration
        </CardTitle>
      </CardHeader>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-obsidian-800">
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Logger</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Effective Level</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Configured</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-obsidian-800">
            {loggers.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-obsidian-400">
                  No loggers found
                </td>
              </tr>
            ) : (
              loggers.map((logger) => (
                <tr key={logger.name} className="hover:bg-obsidian-800">
                  <td className="px-4 py-3 text-sm text-obsidian-200 font-mono">{logger.name}</td>
                  <td className="px-4 py-3">
                    <Badge
                      variant={
                        logger.effectiveLevel === 'ERROR' ? 'danger' :
                        logger.effectiveLevel === 'WARN' ? 'warning' :
                        logger.effectiveLevel === 'DEBUG' ? 'info' : 'outline'
                      }
                      size="sm"
                    >
                      {logger.effectiveLevel}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-sm text-obsidian-400">
                    {logger.configuredLevel || 'Inherited'}
                  </td>
                  <td className="px-4 py-3">
                    <select
                      className="px-2 py-1 text-xs bg-obsidian-800 border border-obsidian-700 rounded text-obsidian-200"
                      value={logger.configuredLevel || 'RESET'}
                      onChange={(e) => handleLevelChange(logger.name, e.target.value)}
                      disabled={setLevelMutation.isPending || resetLevelMutation.isPending}
                    >
                      <option value="RESET">Reset (Inherit)</option>
                      {availableLevels.map((level) => (
                        <option key={level} value={level}>
                          {level}
                        </option>
                      ))}
                    </select>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function EnvironmentInfo() {
  const { data: envInfo, isLoading } = useQuery({
    queryKey: ['environment'],
    queryFn: () => api.getEnvironment(),
  });

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <HardDrive className="w-5 h-5 text-emerald-400" />
            Environment Properties
          </CardTitle>
        </CardHeader>
        <div className="flex items-center justify-center p-8">
          <RefreshCw className="w-6 h-6 animate-spin text-obsidian-400" />
        </div>
      </Card>
    );
  }

  // Combine all properties from different sources
  const allProperties: Array<{ name: string; value: string; source: string }> = [];

  // Add system properties
  if (envInfo?.systemProperties) {
    Object.entries(envInfo.systemProperties).forEach(([name, value]) => {
      allProperties.push({ name, value, source: 'System Properties' });
    });
  }

  // Add system environment
  if (envInfo?.systemEnvironment) {
    Object.entries(envInfo.systemEnvironment).forEach(([name, value]) => {
      allProperties.push({ name, value, source: 'System Environment' });
    });
  }

  // Add property sources
  if (envInfo?.propertySources) {
    envInfo.propertySources.forEach((source) => {
      if (source.properties) {
        Object.entries(source.properties).forEach(([name, value]) => {
          allProperties.push({ name, value, source: source.name || 'Unknown' });
        });
      }
    });
  }

  // Sort by name for better organization
  allProperties.sort((a, b) => a.name.localeCompare(b.name));

  return (
    <div className="space-y-6">
      {envInfo?.activeProfiles && envInfo.activeProfiles.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <HardDrive className="w-5 h-5 text-emerald-400" />
              Active Profiles
            </CardTitle>
          </CardHeader>
          <div className="flex flex-wrap gap-2 p-4">
            {envInfo.activeProfiles.map((profile) => (
              <Badge key={profile} variant="outline" size="sm">
                {profile}
              </Badge>
            ))}
          </div>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <HardDrive className="w-5 h-5 text-emerald-400" />
            Environment Properties ({allProperties.length})
          </CardTitle>
        </CardHeader>
        <div className="overflow-x-auto max-h-[600px] overflow-y-auto">
          <table className="w-full">
            <thead className="sticky top-0 bg-obsidian-900">
              <tr className="border-b border-obsidian-800">
                <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Property</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Value</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Source</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-obsidian-800">
              {allProperties.length === 0 ? (
                <tr>
                  <td colSpan={3} className="px-4 py-8 text-center text-obsidian-400">
                    No environment properties found
                  </td>
                </tr>
              ) : (
                allProperties.map((prop, i) => (
                  <tr key={`${prop.source}-${prop.name}-${i}`} className="hover:bg-obsidian-800">
                    <td className="px-4 py-3 text-sm text-obsidian-200 font-mono">{prop.name}</td>
                    <td className="px-4 py-3 text-sm text-obsidian-400 font-mono max-w-[400px] truncate" title={prop.value}>
                      {prop.value}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="outline" size="sm">{prop.source}</Badge>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

function MetricCard({
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

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-obsidian-500">{label}</span>
      <span className="text-obsidian-200 font-mono text-xs">{value}</span>
    </div>
  );
}
