import { useDashboardStore } from './store';
import type { WebSocketMessage, Dashboard, HealthCheck } from '@/types';

class WebSocketService {
  private ws: WebSocket | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private heartbeatInterval: ReturnType<typeof setInterval> | null = null;

  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/dashboard`;

    try {
      this.ws = new WebSocket(wsUrl);

      this.ws.onopen = () => {
        console.log('WebSocket connected');
        useDashboardStore.getState().setConnected(true);
        this.reconnectAttempts = 0;
        this.startHeartbeat();
        this.requestDashboard();
      };

      this.ws.onmessage = (event) => {
        try {
          const message: WebSocketMessage = JSON.parse(event.data);
          this.handleMessage(message);
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error);
        }
      };

      this.ws.onclose = () => {
        console.log('WebSocket disconnected');
        useDashboardStore.getState().setConnected(false);
        this.stopHeartbeat();
        this.attemptReconnect();
      };

      this.ws.onerror = (error) => {
        console.error('WebSocket error:', error);
      };
    } catch (error) {
      console.error('Failed to create WebSocket:', error);
      this.attemptReconnect();
    }
  }

  private handleMessage(message: WebSocketMessage): void {
    const store = useDashboardStore.getState();

    switch (message.type) {
      case 'DASHBOARD_UPDATE':
        if (message.data) {
          store.setDashboard(message.data as Dashboard);
        }
        break;

      case 'HEALTH_UPDATE':
        if (message.serviceId && message.data) {
          const healthCheck = message.data as HealthCheck;
          store.updateServiceHealth(message.serviceId, healthCheck.status);
        }
        break;

      case 'METRICS_UPDATE':
        // Handle metrics update if needed
        break;

      case 'INCIDENT_CREATED':
        // Could trigger a notification
        if (message.data) {
          console.log('New incident:', message.data);
        }
        break;

      case 'ACTION_EXECUTED':
        // Could trigger a notification
        if (message.data) {
          console.log('Action executed:', message.data);
        }
        break;

      default:
        console.log('Unknown message type:', message.type);
    }
  }

  private attemptReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
      setTimeout(() => this.connect(), this.reconnectDelay);
    } else {
      console.error('Max reconnect attempts reached');
    }
  }

  private startHeartbeat(): void {
    this.heartbeatInterval = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.send({ type: 'GET_DASHBOARD' });
      }
    }, 30000);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }

  send(message: WebSocketMessage): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    }
  }

  requestDashboard(): void {
    this.send({ type: 'GET_DASHBOARD' });
  }

  subscribeToService(serviceId: number): void {
    this.send({ type: 'SUBSCRIBE_SERVICE', serviceId });
  }

  unsubscribeFromService(serviceId: number): void {
    this.send({ type: 'UNSUBSCRIBE_SERVICE', serviceId });
  }

  disconnect(): void {
    this.stopHeartbeat();
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
}

export const wsService = new WebSocketService();
export default wsService;

