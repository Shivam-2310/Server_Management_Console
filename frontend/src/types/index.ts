// Enums
export type HealthStatus = 'HEALTHY' | 'DEGRADED' | 'CRITICAL' | 'DOWN' | 'UNKNOWN';
export type ServiceType = 'BACKEND' | 'FRONTEND';
export type ServiceAction = 'START' | 'STOP' | 'RESTART' | 'SCALE_UP' | 'SCALE_DOWN';
export type ActionStatus = 'SUCCESS' | 'FAILED' | 'PENDING' | 'IN_PROGRESS';
export type IncidentSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type IncidentStatus = 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'CLOSED';
export type UserRole = 'ADMIN' | 'OPERATOR' | 'VIEWER';

// Service
export interface Service {
  id: number;
  name: string;
  description?: string;
  serviceType: ServiceType;
  healthStatus: HealthStatus;
  host: string;
  port: number;
  healthEndpoint?: string;
  metricsEndpoint?: string;
  baseUrl?: string;
  actuatorBasePath?: string;
  frontendTechnology?: string;
  servingTechnology?: string;
  startCommand?: string;
  stopCommand?: string;
  restartCommand?: string;
  workingDirectory?: string;
  processIdentifier?: string;
  isRunning: boolean;
  instanceCount: number;
  cpuUsage?: number;
  memoryUsage?: number;
  responseTime?: number;
  errorRate?: number;
  stabilityScore: number;
  riskScore: number;
  riskTrend?: string;
  tags: string[];
  environment?: string;
  enabled: boolean;
  authenticationToken?: string; // Only returned during registration
  instanceId?: string; // For horizontally scaled services
  createdAt: string;
  updatedAt?: string;
  lastHealthCheck?: string;
  lastMetricsCollection?: string;
  lastRestart?: string;
}

// Health Check
export interface HealthCheck {
  id: number;
  serviceId: number;
  serviceName?: string;
  timestamp: string;
  status: HealthStatus;
  responseTimeMs?: number;
  httpStatusCode?: number;
  statusMessage?: string;
  componentDetails?: string;
  diskSpaceFree?: number;
  diskSpaceTotal?: number;
  databaseHealthy?: boolean;
  databaseStatus?: string;
  errorMessage?: string;
  errorType?: string;
  checkType?: string;
}

// Metrics
export interface Metrics {
  id: number;
  serviceId: number;
  serviceName?: string;
  timestamp: string;
  cpuUsage?: number;
  systemCpuUsage?: number;
  cpuCount?: number;
  memoryUsed?: number;
  memoryMax?: number;
  memoryCommitted?: number;
  memoryUsagePercent?: number;
  heapUsed?: number;
  heapMax?: number;
  threadCount?: number;
  threadPeakCount?: number;
  threadDaemonCount?: number;
  gcPauseCount?: number;
  gcPauseTime?: number;
  httpRequestsTotal?: number;
  httpRequestsPerSecond?: number;
  averageResponseTime?: number;
  p95ResponseTime?: number;
  p99ResponseTime?: number;
  errorCount?: number;
  errorRate?: number;
  http4xxCount?: number;
  http5xxCount?: number;
  uptimeSeconds?: number;
  diskUsed?: number;
  diskFree?: number;
  diskTotal?: number;
}

// Incident
export interface Incident {
  id: number;
  serviceId: number;
  serviceName?: string;
  title: string;
  description?: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  detectionSource?: string;
  detectionRule?: string;
  aiSummary?: string;
  aiRecommendation?: string;
  aiConfidence?: number;
  createdAt: string;
  acknowledgedAt?: string;
  acknowledgedBy?: string;
  resolvedAt?: string;
  resolvedBy?: string;
  resolution?: string;
  closedAt?: string;
  cpuAtIncident?: number;
  memoryAtIncident?: number;
  errorRateAtIncident?: number;
  responseTimeAtIncident?: number;
  tags: string[];
}

// Audit Log
export interface AuditLog {
  id: number;
  serviceId?: number;
  serviceName?: string;
  timestamp: string;
  username: string;
  userRole?: string;
  ipAddress?: string;
  action: ServiceAction;
  actionDetails?: string;
  reason?: string;
  isAutomated?: boolean;
  automationSource?: string;
  status: ActionStatus;
  resultMessage?: string;
  errorDetails?: string;
  durationMs?: number;
  riskLevel?: number;
  aiRecommended?: boolean;
  aiConfidence?: number;
}

// Dashboard
export interface Dashboard {
  totalServices: number;
  backendServices: number;
  frontendServices: number;
  healthyCount: number;
  degradedCount: number;
  criticalCount: number;
  downCount: number;
  unknownCount: number;
  activeIncidents: number;
  criticalIncidents: number;
  incidentsToday: number;
  actionsToday: number;
  failedActionsToday: number;
  highRiskServices: number;
  averageStabilityScore: number;
  recentActions: AuditLog[];
  activeIncidentsList: Incident[];
  healthDistribution: Record<string, number>;
  serviceTypeDistribution: Record<string, number>;
}

// AI Analysis
export interface AIAnalysis {
  serviceId: number;
  serviceName: string;
  analysisTime: string;
  healthAssessment: string;
  confidence: number;
  riskScore: number;
  riskTrend: string;
  riskFactors: string[];
  anomalyDetected?: boolean;
  anomalyType?: string;
  anomalyDescription?: string;
  recommendations: AIRecommendation[];
}

export interface AIRecommendation {
  action: string;
  reason: string;
  urgency: string;
  confidence: number;
  requiresConfirmation: boolean;
}

// Lifecycle
export interface LifecycleActionRequest {
  serviceId: number;
  action: ServiceAction;
  reason?: string;
  dryRun?: boolean;
  confirmed?: boolean;
  targetInstances?: number;
}

export interface LifecycleActionResponse {
  actionId?: number;
  serviceId: number;
  serviceName: string;
  action: ServiceAction;
  status: ActionStatus;
  message: string;
  timestamp: string;
  durationMs?: number;
  requiresConfirmation?: boolean;
  riskLevel?: number;
}

// Auth
export interface AuthRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  fullName?: string;
}

export interface AuthResponse {
  token: string;
  username: string;
  email: string;
  role: UserRole;
  expiresIn: number;
}

export interface User {
  username: string;
  email: string;
  role: UserRole;
  fullName?: string;
}

// Log Entry
export interface LogEntry {
  timestamp: string;
  level: string;
  logger: string;
  message: string;
  serviceName?: string;
  threadName?: string;
  exception?: string;
}

export interface LogResponse {
  entries: LogEntry[];
  totalCount: number;
  category: string;
  serviceName?: string;
  searchQuery?: string;
}

// WebSocket
export interface WebSocketMessage {
  type: string;
  serviceId?: number;
  data?: unknown;
}

// Process Status
export interface ProcessStatus {
  serviceId: number;
  isRunning: boolean;
  pid?: number;
  status: string;
}

// Wallboard
export interface WallboardData {
  services: Service[];
  incidents: Incident[];
  overallHealth: HealthStatus;
  healthyPercentage: number;
  activeIncidentCount: number;
  lastUpdated: string;
}

// Paginated Response
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// Diagnostics
export interface JvmInfo {
  timestamp: number;
  vmName: string;
  vmVersion: string;
  vmVendor: string;
  startTime: number;
  uptime: number;
  inputArguments?: string[];
  systemProperties?: Record<string, string>;
  osName: string;
  osArch: string;
  availableProcessors: number;
  systemLoadAverage: number;
  loadedClassCount: number;
  totalLoadedClassCount: number;
  unloadedClassCount: number;
}

export interface HeapInfo {
  timestamp: number;
  heapUsed: number;
  heapMax: number;
  heapCommitted: number;
  heapInit: number;
  nonHeapUsed: number;
  nonHeapMax: number;
  nonHeapCommitted: number;
  memoryPools?: MemoryPoolInfo[];
  garbageCollectors?: GCInfo[];
}

export interface MemoryPoolInfo {
  name: string;
  type: string;
  used: number;
  max: number;
  committed: number;
}

export interface GCInfo {
  name: string;
  collectionCount: number;
  collectionTime: number;
}

export interface ThreadDumpInfo {
  timestamp: number;
  totalThreads: number;
  peakThreadCount: number;
  totalStartedThreadCount: number;
  daemonThreadCount?: number;
  stateCounts?: Record<string, number>;
  threads?: ThreadDetail[];
}

export interface ThreadDetail {
  threadId: number;
  threadName: string;
  threadState: string;
  daemon: boolean;
  blocked: number;
  waited: number;
  lockName?: string;
  lockOwnerName?: string;
  stackTrace?: string[];
}

// HTTP Traces
export interface HttpTrace {
  timestamp: number;
  method: string;
  uri: string;
  status: number;
  timeTaken: number;
  requestHeaders?: Record<string, string[]>;
  responseHeaders?: Record<string, string[]>;
  remoteAddress?: string;
  principal?: string;
  sessionId?: string;
}

// Logger Management
export interface LoggerInfo {
  name: string;
  effectiveLevel: string;
  configuredLevel: string | null;
}

export interface LoggerChangeResult {
  success: boolean;
  loggerName: string;
  oldLevel?: string;
  newLevel?: string;
  effectiveLevel?: string;
  message?: string;
}

// Environment
export interface EnvironmentInfo {
  timestamp: number;
  activeProfiles?: string[];
  defaultProfiles?: string[];
  propertySources?: PropertySourceInfo[];
  systemEnvironment?: Record<string, string>;
  systemProperties?: Record<string, string>;
}

export interface PropertySourceInfo {
  name: string;
  type?: string;
  propertyCount?: number;
  properties?: Record<string, string>;
}

export interface PropertyValue {
  name: string;
  value: string;
  source: string;
  resolved: boolean;
}

// Remote Log Entry
export interface RemoteLogEntry {
  serviceId: number;
  serviceName: string;
  instanceId?: string;
  timestamp: string;
  level: string;
  logger?: string;
  message: string;
  thread?: string;
}

// Log Statistics
export interface LogStatistics {
  totalLogs: number;
  errorCount: number;
  levelCounts: Record<string, number>;
  firstLogTime?: string;
  lastLogTime?: string;
}

// Infrastructure Info
export interface InfrastructureInfo {
  serviceId?: number;
  serviceName?: string;
  instanceId?: string;
  osName?: string;
  osVersion?: string;
  osArch?: string;
  availableProcessors?: number;
  systemCpuLoad?: number;
  processCpuLoad?: number;
  totalPhysicalMemory?: number;
  freePhysicalMemory?: number;
  jvmName?: string;
  jvmVersion?: string;
  jvmVendor?: string;
  jvmUptime?: number;
  applicationVersion?: string;
  systemProperties?: Record<string, string>;
}

// Remote JVM Info
export interface RemoteJvmInfo {
  serviceId?: number;
  serviceName?: string;
  instanceId?: string;
  jvmName?: string;
  jvmVersion?: string;
  jvmVendor?: string;
  heapUsed?: number;
  heapMax?: number;
  nonHeapUsed?: number;
  threadCount?: number;
  uptime?: number;
}

