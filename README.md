# Intelligent Server Management Console

> ⚠️ **Note**: This project is currently **under active development**. The main branch contains only this README. Please navigate to the **`dev`** branch for the complete codebase.

A centralized control plane that can **observe**, **understand**, and **control** backend and frontend services, with AI-powered insights for operational assistance.

## 🎯 What This Does

- **Observe**: Monitor backend (Spring Boot + Actuator) and frontend (React/Nginx) services
- **Understand**: Aggregate health, metrics, and risk signals with AI-powered anomaly detection
- **Control**: Safe lifecycle operations (start/stop/restart/scale) with role-based access
- **Assist**: AI-generated incident summaries and action recommendations

## 🏗️ Architecture

```
┌───────────────────────┐
│ Backend Services      │
│ (Spring Boot + Act)   │
└──────────▲────────────┘
           │
┌──────────┴────────────┐
│ Frontend Services     │
│ (React / Nginx / FE)  │
└──────────▲────────────┘
           │ Metrics / Health / Control
           ▼
┌──────────────────────────────┐
│ Management Backend (Port 8080)│
│ - Service Registry           │
│ - Health & Metrics Engine    │
│ - Lifecycle Controller       │
│ - AI Intelligence (Gemini)   │
│ - Audit & Safety Engine      │
│ - WebSocket Server           │
└──────────▲───────────────────┘
           │ REST API + WebSocket
           │ /api/* → REST
           │ /ws/* → WebSocket
           ▼
┌──────────────────────────────┐
│ Frontend Dashboard (Port 3001)│
│ - React + TypeScript          │
│ - Vite Dev Server            │
│ - Real-time Updates (WS)      │
│ - Zustand State Management    │
└──────────────────────────────┘
```

## 🔌 Frontend-Backend Integration

### Communication Architecture

The frontend and backend communicate through **two channels**:

#### 1. REST API (HTTP)
- **Base URL**: `/api` (proxied to `http://localhost:8080/api`)
- **Protocol**: HTTP/HTTPS
- **Authentication**: JWT Bearer tokens
- **Client**: Axios with interceptors
- **Location**: `frontend/src/lib/api.ts`

#### 2. WebSocket (Real-time)
- **Endpoint**: `/ws/dashboard` (proxied to `ws://localhost:8080/ws/dashboard`)
- **Protocol**: WebSocket
- **Purpose**: Real-time dashboard updates, health checks, metrics
- **Client**: Native WebSocket with reconnection logic
- **Location**: `frontend/src/lib/websocket.ts`

### Proxy Configuration

The frontend Vite dev server proxies requests to the backend:

```typescript
// vite.config.ts
server: {
  port: 3001,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/ws': {
      target: 'ws://localhost:8080',
      ws: true,
    },
  },
}
```

**How it works:**
- Frontend makes requests to `/api/services` → Vite proxies to `http://localhost:8080/api/services`
- Frontend connects to `ws://localhost:3001/ws/dashboard` → Vite proxies to `ws://localhost:8080/ws/dashboard`
- This eliminates CORS issues during development

### Authentication Flow

1. **Login**: `POST /api/auth/login` → Returns JWT token
2. **Token Storage**: Stored in `localStorage` as `token`
3. **Auto-injection**: Axios interceptor adds `Authorization: Bearer <token>` to all requests
4. **Auto-logout**: On 401 response, clears token and redirects to login

```typescript
// Request interceptor
this.client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

### WebSocket Integration

**Connection Management:**
- Auto-connects on app load
- Auto-reconnects on disconnect (max 5 attempts)
- Heartbeat every 30 seconds
- Updates Zustand store with real-time data

**Message Types:**
- `DASHBOARD_UPDATE` - Full dashboard summary
- `HEALTH_UPDATE` - Service health status change
- `METRICS_UPDATE` - Service metrics update
- `INCIDENT_CREATED` - New incident notification
- `ACTION_EXECUTED` - Lifecycle action completion

**Usage:**
```typescript
// Connect
wsService.connect();

// Subscribe to service updates
wsService.subscribeToService(serviceId);

// Request dashboard update
wsService.requestDashboard();
```

## 🚀 Quick Start

### Prerequisites
- **Java 17+** (for backend)
- **Node.js 18+** (for frontend)
- **Maven 3.8+** (for backend)
- **Google Gemini API Key** (configured in application.yml, for AI features)

### Step 1: Start Backend

```bash
cd backend
mvn spring-boot:run
```

Backend will start on **http://localhost:8080**

**Verify:**
- API: http://localhost:8080/api/dashboard
- H2 Console: http://localhost:8080/h2-console
- Actuator: http://localhost:8080/actuator/health

### Step 2: Start Frontend

```bash
cd frontend
npm install  # First time only
npm run dev
```

Frontend will start on **http://localhost:3001**

**Note:** The frontend dev server automatically proxies `/api` and `/ws` to the backend.

### Step 3: Access the Application

**Note:** AI features use Google Gemini API (configured in `application.yml`). The API key is already set up.

1. Open browser: **http://localhost:3001**
2. Login with default credentials:
   - **Admin**: `admin` / `admin123`
   - **Operator**: `operator` / `operator123`
   - **Viewer**: `viewer` / `viewer123`

## 📡 API Integration

### REST API Endpoints

All endpoints are prefixed with `/api` and require authentication (except `/auth/*`).

#### Authentication
```typescript
POST /api/auth/login
POST /api/auth/register
```

#### Services
```typescript
GET    /api/services              // List all services
GET    /api/services/enabled      // List enabled services only
POST   /api/services              // Register new service
GET    /api/services/{id}         // Get service details
PUT    /api/services/{id}         // Update service
DELETE /api/services/{id}         // Delete service
GET    /api/services/{id}/analysis // AI analysis
POST   /api/services/{id}/health-check // Trigger health check
GET    /api/services/{id}/health-checks?hours=24 // Health check history
POST   /api/services/{id}/metrics // Trigger metrics collection
GET    /api/services/{id}/metrics?hours=24 // Metrics history
```

#### Lifecycle Actions
```typescript
POST /api/lifecycle/action        // Generic action endpoint
POST /api/lifecycle/start/{id}?reason=...
POST /api/lifecycle/stop/{id}?confirmed=true&reason=...
POST /api/lifecycle/restart/{id}?confirmed=true&reason=...
POST /api/lifecycle/scale-up/{id}?targetInstances=3&reason=...
POST /api/lifecycle/scale-down/{id}?targetInstances=2&confirmed=true&reason=...
GET  /api/lifecycle/status/{id}   // Get process status
```

#### Dashboard & Analytics
```typescript
GET /api/dashboard                // Full dashboard summary
GET /api/dashboard/ai-status      // AI availability check
```

#### Incidents
```typescript
GET    /api/incidents?page=0&size=20
GET    /api/incidents/active
GET    /api/incidents/{id}
POST   /api/incidents/{id}/acknowledge
POST   /api/incidents/{id}/resolve?resolution=...
POST   /api/incidents/{id}/close
```

#### Logs
```typescript
GET /api/logs/unified?lines=100&level=INFO&serviceFilter=...
GET /api/logs/service/{id}?lines=100&level=INFO
GET /api/logs/category/{category}?lines=100
GET /api/logs/search?query=...&maxResults=100
```

#### Audit
```typescript
GET /api/audit?page=0&size=20
GET /api/audit/recent?hours=24
```

### Frontend API Client Usage

```typescript
import { api } from '@/lib/api';

// Get services
const services = await api.getServices();

// Get dashboard
const dashboard = await api.getDashboard();

// Execute lifecycle action
const result = await api.restartService(serviceId, 'Maintenance', true);

// Get service metrics
const metrics = await api.getMetrics(serviceId, 24);
```

## 🔄 Real-time Updates (WebSocket)

### Connection

The WebSocket automatically connects when the app loads:

```typescript
// frontend/src/lib/websocket.ts
wsService.connect();
```

### Message Handling

Messages are automatically handled and update the Zustand store:

```typescript
// Dashboard updates
case 'DASHBOARD_UPDATE':
  store.setDashboard(message.data);

// Health status changes
case 'HEALTH_UPDATE':
  store.updateServiceHealth(serviceId, healthCheck.status);
```

### Manual Subscription

```typescript
// Subscribe to specific service updates
wsService.subscribeToService(serviceId);

// Unsubscribe
wsService.unsubscribeFromService(serviceId);
```

## 📁 Project Structure

```
server-management-console/
├── backend/                    # Spring Boot backend
│   ├── src/main/java/com/management/console/
│   │   ├── config/            # Configuration classes
│   │   ├── controller/        # REST API controllers
│   │   ├── domain/            # Entities and enums
│   │   ├── dto/               # Data transfer objects
│   │   ├── service/           # Business logic
│   │   │   └── ai/            # Gemini AI integration
│   │   ├── scheduler/         # Scheduled tasks
│   │   ├── security/          # JWT and auth
│   │   └── websocket/         # WebSocket handlers
│   └── src/main/resources/
│       └── application.yml    # Configuration
│
└── frontend/                   # React + TypeScript frontend
    ├── src/
    │   ├── components/         # React components
    │   │   ├── layout/        # Layout components
    │   │   └── ui/            # UI components
    │   ├── lib/               # Core libraries
    │   │   ├── api.ts         # REST API client
    │   │   ├── websocket.ts   # WebSocket client
    │   │   ├── store.ts       # Zustand state management
    │   │   └── utils.ts       # Utilities
    │   ├── pages/              # Page components
    │   └── types/              # TypeScript types
    ├── vite.config.ts         # Vite configuration (proxy setup)
    └── package.json
```

## 🔧 Configuration

### Backend Configuration (`backend/src/main/resources/application.yml`)

```yaml
server:
  port: 8080

app:
  # Monitoring intervals (milliseconds)
  monitoring:
    health-check-interval: 10000      # 10s for backends
    metrics-poll-interval: 15000      # 15s for metrics
    frontend-check-interval: 30000    # 30s for frontends
  
  # AI Configuration
  ai:
    gemini:
      api-key: AIzaSyCId4zpV3OlqMY1MqNtjeRtYgE12TSzdn0
      model: gemini-1.5-flash
      enabled: true
      timeout: 120000
  
  # Lifecycle settings
  lifecycle:
    max-restart-attempts: 3
    restart-cooldown-seconds: 60
    graceful-shutdown-timeout: 30
  
  # Logging
  logging:
    base-dir: ./logs
    max-lines-per-read: 1000
    max-file-size-mb: 100
```

### Frontend Configuration (`frontend/vite.config.ts`)

```typescript
export default defineConfig({
  server: {
    port: 3001,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
})
```

**Important:** The proxy configuration allows the frontend to communicate with the backend without CORS issues during development.

## 🔒 Security

### Authentication

- **JWT Tokens**: Stored in `localStorage`
- **Auto-injection**: Axios interceptor adds token to all requests
- **Auto-logout**: On 401, clears token and redirects to login

### Roles

| Role | Permissions |
|------|-------------|
| **VIEWER** | Read-only access to all data |
| **OPERATOR** | Can restart, scale services |
| **ADMIN** | Full control including configuration |

### Default Users

```
admin / admin123     - ADMIN
operator / operator123 - OPERATOR
viewer / viewer123    - VIEWER
```

### Audit Trail

Every action logs:
- **Who**: Username and role
- **What**: Action type and details
- **When**: Timestamp with duration
- **Why**: Reason (required for destructive actions)
- **Outcome**: Success/failure with error details

## 🧪 Development Workflow

### Running in Development

1. **Terminal 1 - Backend:**
   ```bash
   cd backend
   mvn spring-boot:run
   ```

2. **Terminal 2 - Frontend:**
   ```bash
   cd frontend
   npm run dev
   ```

**Note:** AI features use Google Gemini API. No additional setup required.

### Building for Production

**Backend:**
```bash
cd backend
mvn clean package
java -jar target/server-management-console-*.jar
```

**Frontend:**
```bash
cd frontend
npm run build
# Output in frontend/dist/
```

**Note:** In production, you'll need to:
1. Configure CORS in backend for your frontend domain
2. Serve frontend build files (via Nginx, Apache, or CDN)
3. Update API base URL in frontend if not using proxy

## 📊 Key Features

### Service Monitoring
- **Backend**: Spring Boot Actuator integration (health, metrics, loggers, threaddump)
- **Frontend**: HTTP status checks, response time, synthetic monitoring
- **Health Model**: Not binary - HEALTHY, DEGRADED, CRITICAL, DOWN based on multiple signals

### Lifecycle Control
| Action | Backend | Frontend |
|--------|---------|----------|
| START | Start process/container | Start server |
| STOP | Graceful shutdown | Stop serving |
| RESTART | Controlled restart | Reload |
| SCALE | Increase/decrease replicas | Increase instances |

All actions are:
- ✅ Whitelisted (MVP safety)
- ✅ Audited (complete trail)
- ✅ Role-protected (VIEWER/OPERATOR/ADMIN)
- ✅ Confirmation required for destructive actions

### AI Intelligence (Google Gemini API + gemini-1.5-flash)
- **Anomaly Detection**: Memory leaks, CPU saturation, error spikes
- **Risk Scoring**: 0-100 stability and risk scores per service
- **Incident Summaries**: AI-generated summaries with recommendations
- **Smart Recommendations**: Restart, scale, investigate, or ignore transient spikes

⚠️ **Human-in-the-loop is mandatory** - AI does not auto-execute destructive actions

## 🐛 Troubleshooting

### Frontend can't connect to backend

1. **Check backend is running:**
   ```bash
   curl http://localhost:8080/api/dashboard
   ```

2. **Check proxy configuration:**
   - Verify `vite.config.ts` has correct proxy settings
   - Ensure backend port is 8080

3. **Check CORS (if not using proxy):**
   - Backend should allow `http://localhost:3001` in CORS config

### WebSocket connection fails

1. **Check WebSocket endpoint:**
   ```bash
   # Should return 101 Switching Protocols
   curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" http://localhost:8080/ws/dashboard
   ```

2. **Check proxy configuration:**
   - Ensure `/ws` is proxied in `vite.config.ts`

3. **Check browser console:**
   - Look for WebSocket connection errors

### Authentication issues

1. **Check token in localStorage:**
   ```javascript
   localStorage.getItem('token')
   ```

2. **Check token expiration:**
   - Default JWT expiration: 24 hours
   - Re-login if token expired

3. **Check backend security config:**
   - Verify JWT secret matches
   - Check security filter chain

## 📚 Additional Resources

### Backend API Documentation
- Swagger/OpenAPI: Available at `/swagger-ui.html` (if enabled)
- Actuator endpoints: `/actuator`

### Frontend State Management
- **Zustand Store**: `frontend/src/lib/store.ts`
- **API Client**: `frontend/src/lib/api.ts`
- **WebSocket Service**: `frontend/src/lib/websocket.ts`

### Type Definitions
- Shared types: `frontend/src/types/index.ts`
- DTOs: `backend/src/main/java/com/management/console/dto/`

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test frontend-backend integration
5. Submit a pull request