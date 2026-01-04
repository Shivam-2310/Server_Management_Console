import axios, { AxiosInstance, AxiosError } from 'axios';
import type {
  Service, HealthCheck, Metrics, Incident, AuditLog, Dashboard,
  AIAnalysis, LifecycleActionRequest, LifecycleActionResponse,
  AuthRequest, RegisterRequest, AuthResponse, LogResponse,
  ProcessStatus, WallboardData, Page,
  JvmInfo, HeapInfo, ThreadDumpInfo,
  HttpTrace, LoggerInfo, LoggerChangeResult,
  EnvironmentInfo, PropertyValue
} from '@/types';

const API_BASE = '/api';

class ApiClient {
  private client: AxiosInstance;

  constructor() {
    try {
      this.client = axios.create({
        baseURL: API_BASE,
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!this.client) {
        throw new Error('Failed to create axios instance');
      }

      this.setupInterceptors();
    } catch (error) {
      console.error('Failed to initialize API client:', error);
      throw error;
    }
  }

  private setupInterceptors(): void {

    // Request interceptor - add auth token
    this.client.interceptors.request.use(
      (config) => {
        if (!config) {
          throw new Error('Request config is undefined');
        }
        const token = localStorage.getItem('token');
        if (token && config.headers) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => {
        return Promise.reject(error);
      }
    );

    // Response interceptor - handle 401
    this.client.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        if (error.response?.status === 401) {
          localStorage.removeItem('token');
          localStorage.removeItem('user');
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }
    );
  }

  // Auth
  async login(request: AuthRequest): Promise<AuthResponse> {
    const { data } = await this.client.post<AuthResponse>('/auth/login', request);
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify({
      username: data.username,
      email: data.email,
      role: data.role,
    }));
    return data;
  }

  async register(request: RegisterRequest): Promise<AuthResponse> {
    const { data } = await this.client.post<AuthResponse>('/auth/register', request);
    return data;
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }

  // Dashboard
  async getDashboard(): Promise<Dashboard> {
    const { data } = await this.client.get<Dashboard>('/dashboard');
    return data;
  }

  async getAIStatus(): Promise<{ available: boolean; model: string; provider: string }> {
    const { data } = await this.client.get('/dashboard/ai-status');
    return data;
  }

  // Services
  async getServices(): Promise<Service[]> {
    try {
      if (!this.client) {
        throw new Error('API client not initialized');
      }
      const { data } = await this.client.get<Service[]>('/services');
      return data || [];
    } catch (error) {
      console.error('Error fetching services:', error);
      throw error;
    }
  }

  async getEnabledServices(): Promise<Service[]> {
    const { data } = await this.client.get<Service[]>('/services/enabled');
    return data;
  }

  async getService(id: number): Promise<Service> {
    const { data } = await this.client.get<Service>(`/services/${id}`);
    return data;
  }

  async createService(service: Partial<Service>): Promise<Service> {
    const { data } = await this.client.post<Service>('/services', service);
    return data;
  }

  async updateService(id: number, service: Partial<Service>): Promise<Service> {
    const { data } = await this.client.put<Service>(`/services/${id}`, service);
    return data;
  }

  async deleteService(id: number): Promise<void> {
    await this.client.delete(`/services/${id}`);
  }

  async getServiceAnalysis(id: number): Promise<AIAnalysis> {
    const { data } = await this.client.get<AIAnalysis>(`/services/${id}/analysis`);
    return data;
  }

  async getServiceStabilityScore(id: number): Promise<number> {
    const { data } = await this.client.get<number>(`/services/${id}/stability-score`);
    return data;
  }

  // Health Checks
  async triggerHealthCheck(serviceId: number): Promise<HealthCheck> {
    const { data } = await this.client.post<HealthCheck>(`/services/${serviceId}/health-check`);
    return data;
  }

  async getHealthChecks(serviceId: number, hours = 24): Promise<HealthCheck[]> {
    const { data } = await this.client.get<HealthCheck[]>(`/services/${serviceId}/health-checks`, {
      params: { hours },
    });
    return data;
  }

  async getLatestHealthCheck(serviceId: number): Promise<HealthCheck | null> {
    const { data } = await this.client.get<HealthCheck>(`/services/${serviceId}/health-checks/latest`);
    return data;
  }

  // Metrics
  async triggerMetricsCollection(serviceId: number): Promise<Metrics> {
    const { data } = await this.client.post<Metrics>(`/services/${serviceId}/metrics`);
    return data;
  }

  async getMetrics(serviceId: number, hours = 24): Promise<Metrics[]> {
    const { data } = await this.client.get<Metrics[]>(`/services/${serviceId}/metrics`, {
      params: { hours },
    });
    return data;
  }

  async getLatestMetrics(serviceId: number): Promise<Metrics | null> {
    const { data } = await this.client.get<Metrics>(`/services/${serviceId}/metrics/latest`);
    return data;
  }

  // Lifecycle
  async executeLifecycleAction(request: LifecycleActionRequest): Promise<LifecycleActionResponse> {
    const { data } = await this.client.post<LifecycleActionResponse>('/lifecycle/action', request);
    return data;
  }

  async startService(serviceId: number, reason?: string): Promise<LifecycleActionResponse> {
    const { data } = await this.client.post<LifecycleActionResponse>(`/lifecycle/start/${serviceId}`, null, {
      params: { reason },
    });
    return data;
  }

  async stopService(serviceId: number, reason?: string, confirmed = false): Promise<LifecycleActionResponse> {
    const { data } = await this.client.post<LifecycleActionResponse>(`/lifecycle/stop/${serviceId}`, null, {
      params: { reason, confirmed },
    });
    return data;
  }

  async restartService(serviceId: number, reason?: string, confirmed = false): Promise<LifecycleActionResponse> {
    const { data } = await this.client.post<LifecycleActionResponse>(`/lifecycle/restart/${serviceId}`, null, {
      params: { reason, confirmed },
    });
    return data;
  }

  async scaleUp(serviceId: number, targetInstances?: number, reason?: string): Promise<LifecycleActionResponse> {
    const { data } = await this.client.post<LifecycleActionResponse>(`/lifecycle/scale-up/${serviceId}`, null, {
      params: { targetInstances, reason },
    });
    return data;
  }

  async scaleDown(serviceId: number, targetInstances?: number, reason?: string, confirmed = false): Promise<LifecycleActionResponse> {
    const { data } = await this.client.post<LifecycleActionResponse>(`/lifecycle/scale-down/${serviceId}`, null, {
      params: { targetInstances, reason, confirmed },
    });
    return data;
  }

  async getProcessStatus(serviceId: number): Promise<ProcessStatus> {
    const { data } = await this.client.get<ProcessStatus>(`/lifecycle/status/${serviceId}`);
    return data;
  }

  // Incidents
  async getIncidents(page = 0, size = 20): Promise<Page<Incident>> {
    const { data } = await this.client.get<Page<Incident>>('/incidents', {
      params: { page, size },
    });
    return data;
  }

  async getActiveIncidents(): Promise<Incident[]> {
    const { data } = await this.client.get<Incident[]>('/incidents/active');
    return data;
  }

  async getIncident(id: number): Promise<Incident> {
    const { data } = await this.client.get<Incident>(`/incidents/${id}`);
    return data;
  }

  async acknowledgeIncident(id: number): Promise<Incident> {
    const { data } = await this.client.post<Incident>(`/incidents/${id}/acknowledge`);
    return data;
  }

  async resolveIncident(id: number, resolution: string): Promise<Incident> {
    const { data } = await this.client.post<Incident>(`/incidents/${id}/resolve`, null, {
      params: { resolution },
    });
    return data;
  }

  async closeIncident(id: number): Promise<Incident> {
    const { data } = await this.client.post<Incident>(`/incidents/${id}/close`);
    return data;
  }

  // Audit Logs
  async getAuditLogs(page = 0, size = 20): Promise<Page<AuditLog>> {
    const { data } = await this.client.get<Page<AuditLog>>('/audit', {
      params: { page, size },
    });
    return data;
  }

  async getRecentActions(hours = 24): Promise<AuditLog[]> {
    const { data } = await this.client.get<AuditLog[]>('/audit/recent', {
      params: { hours },
    });
    return data;
  }

  // Logs
  async getUnifiedLogs(lines = 100, level = 'INFO', serviceFilter?: string): Promise<LogResponse> {
    const { data } = await this.client.get<LogResponse>('/logs/unified', {
      params: { lines, level, serviceFilter },
    });
    return data;
  }

  async getServiceLogs(serviceId: number, lines = 100, level = 'INFO'): Promise<LogResponse> {
    const { data } = await this.client.get<LogResponse>(`/logs/service/${serviceId}`, {
      params: { lines, level },
    });
    return data;
  }

  async getLogsByCategory(category: string, lines = 100): Promise<LogResponse> {
    const { data } = await this.client.get<LogResponse>(`/logs/category/${category}`, {
      params: { lines },
    });
    return data;
  }

  async searchLogs(query: string, maxResults = 100): Promise<LogResponse> {
    const { data } = await this.client.get<LogResponse>('/logs/search', {
      params: { query, maxResults },
    });
    return data;
  }

  // Wallboard
  async getWallboardData(): Promise<WallboardData> {
    const { data } = await this.client.get<WallboardData>('/wallboard');
    return data;
  }

  // Diagnostics
  async getJvmInfo(): Promise<JvmInfo> {
    const { data } = await this.client.get<JvmInfo>('/diagnostics/jvm/info');
    return data;
  }

  async getHeapInfo(): Promise<HeapInfo> {
    const { data } = await this.client.get<HeapInfo>('/diagnostics/jvm/heap');
    return data;
  }

  async getThreadDump(): Promise<ThreadDumpInfo> {
    const { data } = await this.client.get<ThreadDumpInfo>('/diagnostics/jvm/threads');
    return data;
  }

  async requestGC(): Promise<{ success: boolean; message: string; memoryFreedBytes?: number }> {
    const { data } = await this.client.post('/diagnostics/jvm/gc');
    return data;
  }

  async downloadThreadDump(): Promise<Blob> {
    const { data } = await this.client.get('/diagnostics/jvm/threads/download', {
      responseType: 'blob',
    });
    return data;
  }

  // HTTP Traces
  async getHttpTraces(limit = 100): Promise<HttpTrace[]> {
    const { data } = await this.client.get<HttpTrace[]>('/diagnostics/http/traces', {
      params: { limit },
    });
    return data;
  }

  async clearHttpTraces(): Promise<void> {
    await this.client.delete('/diagnostics/http/traces');
  }

  // Loggers
  async getLoggers(): Promise<LoggerInfo[]> {
    const { data } = await this.client.get<LoggerInfo[]>('/diagnostics/loggers');
    return data;
  }

  async setLoggerLevel(loggerName: string, level: string): Promise<LoggerChangeResult> {
    const { data } = await this.client.post<LoggerChangeResult>(
      `/diagnostics/loggers/${encodeURIComponent(loggerName)}/level`,
      null,
      { params: { level } }
    );
    return data;
  }

  async resetLoggerLevel(loggerName: string): Promise<LoggerChangeResult> {
    const { data } = await this.client.delete<LoggerChangeResult>(
      `/diagnostics/loggers/${encodeURIComponent(loggerName)}/level`
    );
    return data;
  }

  async getAvailableLogLevels(): Promise<string[]> {
    const { data } = await this.client.get<string[]>('/diagnostics/loggers/levels');
    return data;
  }

  // Environment
  async getEnvironment(): Promise<EnvironmentInfo> {
    const { data } = await this.client.get<EnvironmentInfo>('/diagnostics/env');
    return data;
  }

  async searchEnvironmentProperties(pattern: string): Promise<PropertyValue[]> {
    const { data } = await this.client.get<PropertyValue[]>('/diagnostics/env/search', {
      params: { pattern },
    });
    return data;
  }

  // Remote Service Logs (from registered services)
  async getServiceRemoteLogs(
    serviceId: number,
    lines = 100,
    level?: string,
    instanceId?: string
  ): Promise<import('@/types').RemoteLogEntry[]> {
    const { data } = await this.client.get<import('@/types').RemoteLogEntry[]>(
      `/services/${serviceId}/logs`,
      {
        params: { lines, level, instanceId },
      }
    );
    return data;
  }

  async searchServiceLogs(
    serviceId: number,
    query?: string,
    startTime?: string,
    endTime?: string,
    level?: string,
    maxResults = 100
  ): Promise<import('@/types').RemoteLogEntry[]> {
    const { data } = await this.client.get<import('@/types').RemoteLogEntry[]>(
      `/services/${serviceId}/logs/search`,
      {
        params: { query, startTime, endTime, level, maxResults },
      }
    );
    return data;
  }

  async getServiceLogStatistics(
    serviceId: number,
    startTime?: string,
    endTime?: string
  ): Promise<import('@/types').LogStatistics> {
    const { data } = await this.client.get<import('@/types').LogStatistics>(
      `/services/${serviceId}/logs/statistics`,
      {
        params: { startTime, endTime },
      }
    );
    return data;
  }

  // Service Configuration
  async getServiceConfiguration(serviceId: number): Promise<EnvironmentInfo> {
    try {
      console.log(`[API] Fetching configuration for service ${serviceId}`);
      const response = await this.client.get<EnvironmentInfo>(
        `/services/${serviceId}/configuration`
      );
      
      // Validate response
      if (!response || !response.data) {
        console.warn(`[API] Empty response for configuration service ${serviceId}`);
        return {
          timestamp: Date.now(),
          activeProfiles: [],
          propertySources: [],
        };
      }
      
      const data = response.data;
      
      // Check if response is actually empty
      const hasData = (data.propertySources && data.propertySources.length > 0) ||
                      (data.activeProfiles && data.activeProfiles.length > 0);
      
      if (!hasData) {
        console.warn(`[API] Configuration response appears empty for service ${serviceId}`, data);
      } else {
        console.log(`[API] Configuration fetched successfully for service ${serviceId}:`, {
          propertySources: data.propertySources?.length || 0,
          activeProfiles: data.activeProfiles?.length || 0
        });
      }
      
      // Ensure required fields are present
      if (!data.timestamp) data.timestamp = Date.now();
      if (!data.activeProfiles) data.activeProfiles = [];
      if (!data.propertySources) data.propertySources = [];
      
      return data;
    } catch (error) {
      console.error(`[API] Error fetching configuration for service ${serviceId}:`, error);
      
      // Return empty configuration instead of throwing
      return {
        timestamp: Date.now(),
        activeProfiles: [],
        propertySources: [],
      };
    }
  }

  // Infrastructure Monitoring
  async getServiceInfrastructure(serviceId: number): Promise<import('@/types').InfrastructureInfo> {
    try {
      console.log(`[API] Fetching infrastructure for service ${serviceId}`);
      const response = await this.client.get<import('@/types').InfrastructureInfo>(
        `/services/${serviceId}/infrastructure`
      );
      
      // Validate response
      if (!response || !response.data) {
        console.warn(`[API] Empty response for infrastructure service ${serviceId}`);
        return this.createEmptyInfrastructureInfo(serviceId);
      }
      
      const data = response.data;
      
      // Check if response is actually empty (empty object or all nulls)
      const hasData = data.serviceId != null || 
                      data.serviceName != null || 
                      data.osName != null || 
                      data.jvmName != null;
      
      if (!hasData) {
        console.warn(`[API] Infrastructure response appears empty for service ${serviceId}`, data);
        // Still return the data, but log a warning
      } else {
        console.log(`[API] Infrastructure fetched successfully for service ${serviceId}:`, {
          serviceName: data.serviceName,
          osName: data.osName,
          jvmName: data.jvmName,
          cpuLoad: data.systemCpuLoad
        });
      }
      
      // Ensure at least service info is present
      if (!data.serviceId) data.serviceId = serviceId;
      
      return data;
    } catch (error) {
      console.error(`[API] Error fetching infrastructure for service ${serviceId}:`, error);
      
      // Return empty infrastructure info instead of throwing
      // This allows the UI to show "No data" instead of an error
      return this.createEmptyInfrastructureInfo(serviceId);
    }
  }
  
  private createEmptyInfrastructureInfo(serviceId: number): import('@/types').InfrastructureInfo {
    return {
      serviceId,
      serviceName: 'Unknown',
      osName: 'Unknown',
      osVersion: 'Unknown',
      osArch: 'Unknown',
      jvmName: 'Unknown',
      jvmVersion: 'Unknown',
      jvmVendor: 'Unknown',
      availableProcessors: 0,
      systemCpuLoad: 0,
      processCpuLoad: 0,
    };
  }

  async getServiceJvmInfo(serviceId: number): Promise<import('@/types').RemoteJvmInfo> {
    try {
      console.log(`[API] Fetching JVM info for service ${serviceId}`);
      const response = await this.client.get<import('@/types').RemoteJvmInfo>(
        `/services/${serviceId}/jvm`
      );
      
      // Validate response
      if (!response || !response.data) {
        console.warn(`[API] Empty response for JVM info service ${serviceId}`);
        return this.createEmptyJvmInfo(serviceId);
      }
      
      const data = response.data;
      
      // Check if response is actually empty
      const hasData = data.serviceId != null || 
                      data.serviceName != null || 
                      data.jvmName != null || 
                      data.heapUsed != null;
      
      if (!hasData) {
        console.warn(`[API] JVM info response appears empty for service ${serviceId}`, data);
      } else {
        console.log(`[API] JVM info fetched successfully for service ${serviceId}:`, {
          serviceName: data.serviceName,
          jvmName: data.jvmName,
          heapUsed: data.heapUsed
        });
      }
      
      // Ensure at least service info is present
      if (!data.serviceId) data.serviceId = serviceId;
      
      return data;
    } catch (error) {
      console.error(`[API] Error fetching JVM info for service ${serviceId}:`, error);
      
      // Return empty JVM info instead of throwing
      return this.createEmptyJvmInfo(serviceId);
    }
  }
  
  private createEmptyJvmInfo(serviceId: number): import('@/types').RemoteJvmInfo {
    return {
      serviceId,
      serviceName: 'Unknown',
      jvmName: 'Unknown',
      jvmVersion: 'Unknown',
      jvmVendor: 'Unknown',
      heapUsed: 0,
      heapMax: 0,
      nonHeapUsed: 0,
      threadCount: 0,
      uptime: 0,
    };
  }
}

// Create singleton instance
const apiInstance = new ApiClient();

// Verify the instance was created correctly
if (!apiInstance) {
  console.error('Failed to create API client instance');
}

export const api = apiInstance;
export default api;

