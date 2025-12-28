import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User, Dashboard, Service, Incident, HealthStatus } from '@/types';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  setAuth: (user: User, token: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isAuthenticated: false,
      setAuth: (user, token) => {
        localStorage.setItem('token', token);
        set({ user, token, isAuthenticated: true });
      },
      logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        set({ user: null, token: null, isAuthenticated: false });
      },
    }),
    {
      name: 'auth-storage',
    }
  )
);

interface DashboardState {
  dashboard: Dashboard | null;
  services: Service[];
  incidents: Incident[];
  lastUpdate: string | null;
  isConnected: boolean;
  setDashboard: (dashboard: Dashboard) => void;
  setServices: (services: Service[]) => void;
  setIncidents: (incidents: Incident[]) => void;
  updateServiceHealth: (serviceId: number, health: HealthStatus) => void;
  setConnected: (connected: boolean) => void;
}

export const useDashboardStore = create<DashboardState>()((set) => ({
  dashboard: null,
  services: [],
  incidents: [],
  lastUpdate: null,
  isConnected: false,
  setDashboard: (dashboard) => set({ dashboard, lastUpdate: new Date().toISOString() }),
  setServices: (services) => set({ services }),
  setIncidents: (incidents) => set({ incidents }),
  updateServiceHealth: (serviceId, health) =>
    set((state) => ({
      services: state.services.map((s) =>
        s.id === serviceId ? { ...s, healthStatus: health } : s
      ),
    })),
  setConnected: (connected) => set({ isConnected: connected }),
}));

interface Notification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message?: string;
  duration?: number;
}

interface UIState {
  sidebarCollapsed: boolean;
  theme: 'dark' | 'light';
  notifications: Notification[];
  toggleSidebar: () => void;
  setTheme: (theme: 'dark' | 'light') => void;
  addNotification: (notification: Omit<Notification, 'id'>) => void;
  removeNotification: (id: string) => void;
}

export const useUIStore = create<UIState>()((set) => ({
  sidebarCollapsed: false,
  theme: 'dark',
  notifications: [],
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
  setTheme: (theme) => set({ theme }),
  addNotification: (notification) =>
    set((state) => ({
      notifications: [
        ...state.notifications,
        { ...notification, id: crypto.randomUUID() },
      ],
    })),
  removeNotification: (id) =>
    set((state) => ({
      notifications: state.notifications.filter((n) => n.id !== id),
    })),
}));
