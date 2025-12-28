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

