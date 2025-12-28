import axios, { AxiosInstance, AxiosError } from 'axios';
import type {
  Service, HealthCheck, Metrics, Incident, AuditLog, Dashboard,
  AIAnalysis, LifecycleActionRequest, LifecycleActionResponse,
  AuthRequest, RegisterRequest, AuthResponse, LogResponse,
  ProcessStatus, WallboardData, Page
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
}

// Create singleton instance
const apiInstance = new ApiClient();

// Verify the instance was created correctly
if (!apiInstance) {
  console.error('Failed to create API client instance');
}

export const api = apiInstance;
export default api;

