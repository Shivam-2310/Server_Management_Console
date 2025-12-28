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

// Note: These would normally come from the API
// For demo purposes, we'll use mock data and local JVM info

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
  // Mock JVM data - would come from API
  const jvmInfo = {
    javaVersion: '17.0.9',
    javaVendor: 'Eclipse Adoptium',
    vmName: 'OpenJDK 64-Bit Server VM',
    vmVersion: '17.0.9+9',
    osName: 'Windows 10',
    osArch: 'amd64',
    processors: 8,
    uptime: 345600,
  };

  const heapInfo = {
    used: 256 * 1024 * 1024,
    committed: 512 * 1024 * 1024,
    max: 1024 * 1024 * 1024,
    usedPercent: 25,
  };

  const threadInfo = {
    total: 45,
    peak: 52,
    daemon: 38,
    started: 156,
  };

  return (
    <div className="space-y-6">
      {/* Quick Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <MetricCard
          title="Heap Used"
          value={formatBytes(heapInfo.used)}
          subtitle={`${heapInfo.usedPercent}% of max`}
          icon={<MemoryStick className="w-5 h-5" />}
          color="violet"
        />
        <MetricCard
          title="Threads"
          value={threadInfo.total.toString()}
          subtitle={`${threadInfo.daemon} daemon`}
          icon={<Layers className="w-5 h-5" />}
          color="cyan"
        />
        <MetricCard
          title="Processors"
          value={jvmInfo.processors.toString()}
          subtitle={jvmInfo.osArch}
          icon={<Cpu className="w-5 h-5" />}
          color="emerald"
        />
        <MetricCard
          title="Uptime"
          value={formatUptime(jvmInfo.uptime)}
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
            <InfoRow label="Java Version" value={jvmInfo.javaVersion} />
            <InfoRow label="Vendor" value={jvmInfo.javaVendor} />
            <InfoRow label="VM Name" value={jvmInfo.vmName} />
            <InfoRow label="VM Version" value={jvmInfo.vmVersion} />
            <InfoRow label="OS" value={`${jvmInfo.osName} (${jvmInfo.osArch})`} />
          </div>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <MemoryStick className="w-5 h-5 text-violet-400" />
              Memory Usage
            </CardTitle>
            <Button variant="ghost" size="sm" icon={<Trash2 className="w-4 h-4" />}>
              Request GC
            </Button>
          </CardHeader>
          <div className="space-y-4">
            <div>
              <div className="flex items-center justify-between text-sm mb-2">
                <span className="text-obsidian-400">Heap Memory</span>
                <span className="text-obsidian-200">{formatBytes(heapInfo.used)} / {formatBytes(heapInfo.max)}</span>
              </div>
              <div className="h-3 bg-obsidian-800 rounded-full overflow-hidden">
                <motion.div
                  className="h-full bg-gradient-to-r from-violet-600 to-violet-400 rounded-full"
                  initial={{ width: 0 }}
                  animate={{ width: `${heapInfo.usedPercent}%` }}
                  transition={{ duration: 1 }}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <p className="text-obsidian-500">Committed</p>
                <p className="text-obsidian-200 font-medium">{formatBytes(heapInfo.committed)}</p>
              </div>
              <div>
                <p className="text-obsidian-500">Max</p>
                <p className="text-obsidian-200 font-medium">{formatBytes(heapInfo.max)}</p>
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
          <Button variant="secondary" size="sm" icon={<Download className="w-4 h-4" />}>
            Download Thread Dump
          </Button>
        </CardHeader>
        <div className="grid grid-cols-4 gap-4">
          <div className="text-center p-4 bg-obsidian-800 rounded-lg">
            <p className="text-2xl font-bold text-obsidian-100">{threadInfo.total}</p>
            <p className="text-sm text-obsidian-500">Total</p>
          </div>
          <div className="text-center p-4 bg-obsidian-800 rounded-lg">
            <p className="text-2xl font-bold text-cyan-400">{threadInfo.daemon}</p>
            <p className="text-sm text-obsidian-500">Daemon</p>
          </div>
          <div className="text-center p-4 bg-obsidian-800 rounded-lg">
            <p className="text-2xl font-bold text-amber-400">{threadInfo.peak}</p>
            <p className="text-sm text-obsidian-500">Peak</p>
          </div>
          <div className="text-center p-4 bg-obsidian-800 rounded-lg">
            <p className="text-2xl font-bold text-violet-400">{threadInfo.started}</p>
            <p className="text-sm text-obsidian-500">Started</p>
          </div>
        </div>
      </Card>
    </div>
  );
}

function HTTPTraces() {
  // Mock HTTP traces
  const traces = [
    { method: 'GET', uri: '/api/dashboard', status: 200, time: 45, timestamp: new Date() },
    { method: 'GET', uri: '/api/services', status: 200, time: 23, timestamp: new Date() },
    { method: 'POST', uri: '/api/lifecycle/restart/1', status: 200, time: 1234, timestamp: new Date() },
    { method: 'GET', uri: '/api/services/1/metrics', status: 200, time: 89, timestamp: new Date() },
    { method: 'GET', uri: '/api/incidents/active', status: 500, time: 156, timestamp: new Date() },
  ];

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2">
          <Network className="w-5 h-5 text-emerald-400" />
          Recent HTTP Requests
        </CardTitle>
        <Button variant="ghost" size="sm" icon={<Trash2 className="w-4 h-4" />}>
          Clear
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
            </tr>
          </thead>
          <tbody className="divide-y divide-obsidian-800">
            {traces.map((trace, i) => (
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
                <td className="px-4 py-3 text-sm text-obsidian-400">{trace.time}ms</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function LoggerManagement() {
  const loggers = [
    { name: 'com.management.console', level: 'DEBUG', configuredLevel: 'DEBUG' },
    { name: 'org.springframework.web', level: 'INFO', configuredLevel: 'INFO' },
    { name: 'org.hibernate', level: 'WARN', configuredLevel: null },
    { name: 'org.springframework.security', level: 'INFO', configuredLevel: 'INFO' },
  ];

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
            {loggers.map((logger, i) => (
              <tr key={i} className="hover:bg-obsidian-800">
                <td className="px-4 py-3 text-sm text-obsidian-200 font-mono">{logger.name}</td>
                <td className="px-4 py-3">
                  <Badge
                    variant={
                      logger.level === 'ERROR' ? 'danger' :
                      logger.level === 'WARN' ? 'warning' :
                      logger.level === 'DEBUG' ? 'info' : 'outline'
                    }
                    size="sm"
                  >
                    {logger.level}
                  </Badge>
                </td>
                <td className="px-4 py-3 text-sm text-obsidian-400">
                  {logger.configuredLevel || 'Inherited'}
                </td>
                <td className="px-4 py-3">
                  <select className="px-2 py-1 text-xs bg-obsidian-800 border border-obsidian-700 rounded text-obsidian-200">
                    <option>TRACE</option>
                    <option>DEBUG</option>
                    <option>INFO</option>
                    <option>WARN</option>
                    <option>ERROR</option>
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function EnvironmentInfo() {
  const envProps = [
    { name: 'server.port', value: '8080', source: 'application.yml' },
    { name: 'spring.datasource.url', value: 'jdbc:h2:file:./data/management-console', source: 'application.yml' },
    { name: 'app.ai.ollama.enabled', value: 'true', source: 'application.yml' },
    { name: 'app.monitoring.health-check-interval', value: '10000', source: 'application.yml' },
    { name: 'JAVA_HOME', value: 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.9.9-hotspot', source: 'System Environment' },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <HardDrive className="w-5 h-5 text-emerald-400" />
          Environment Properties
        </CardTitle>
      </CardHeader>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-obsidian-800">
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Property</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Value</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-obsidian-500 uppercase">Source</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-obsidian-800">
            {envProps.map((prop, i) => (
              <tr key={i} className="hover:bg-obsidian-800">
                <td className="px-4 py-3 text-sm text-obsidian-200 font-mono">{prop.name}</td>
                <td className="px-4 py-3 text-sm text-obsidian-400 font-mono max-w-[300px] truncate">{prop.value}</td>
                <td className="px-4 py-3">
                  <Badge variant="outline" size="sm">{prop.source}</Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
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
