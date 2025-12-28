# Server Management Console - Backend

A centralized management console that monitors and controls backend and frontend services, aggregates health, metrics, and logs, enables safe lifecycle operations, and uses AI to detect anomalies and assist operators with intelligent, risk-aware decisions.

## Features

### Core Capabilities
- **Service Registry**: Track and manage backend (Spring Boot) and frontend (React/Nginx) services
- **Health Monitoring**: Real-time health checks with multi-signal status derivation (HEALTHY, DEGRADED, CRITICAL, DOWN)
- **Metrics Collection**: CPU, memory, threads, GC, HTTP request rates, error rates
- **Lifecycle Control**: Safe start/stop/restart/scale operations with confirmation and audit
- **Incident Management**: Automatic incident creation, AI-generated summaries, severity tracking

### AI Intelligence (Ollama Integration)
- **Anomaly Detection**: AI-driven detection of memory leaks, CPU saturation, error spikes
- **Risk Scoring**: Real-time stability and risk scores (0-100) for each service
- **Incident Summaries**: AI-generated summaries explaining what happened and recommended actions
- **Action Recommendations**: Smart suggestions for restart, scale, investigate based on patterns

### Security & Audit
- **Role-Based Access**: VIEWER (read-only), OPERATOR (restart/scale), ADMIN (full control)
- **JWT Authentication**: Secure token-based authentication
- **Complete Audit Trail**: Every action logged with who, what, when, why, and outcome

## Prerequisites

- Java 17+
- Maven 3.8+
- Ollama (for AI features) with llama3.2:1b model

## Quick Start

### 1. Start Ollama (Optional, for AI features)
```bash
ollama run llama3.2:1b
```

### 2. Build and Run
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### 3. Access the Application
- API: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console
- WebSocket: ws://localhost:8080/ws/dashboard

## Default Users

| Username | Password | Role |
|----------|----------|------|
| admin | admin123 | ADMIN |
| operator | operator123 | OPERATOR |
| viewer | viewer123 | VIEWER |

## API Endpoints

### Authentication
```
POST /api/auth/register - Register new user
POST /api/auth/login    - Login and get JWT token
```

### Dashboard
```
GET /api/dashboard              - Get dashboard summary
GET /api/dashboard/ai-status    - Check AI availability
```

### Services
```
GET    /api/services              - List all services
POST   /api/services              - Register new service
GET    /api/services/{id}         - Get service details
PUT    /api/services/{id}         - Update service
DELETE /api/services/{id}         - Delete service
GET    /api/services/{id}/analysis - AI analysis for service
POST   /api/services/{id}/health-check - Trigger health check
GET    /api/services/{id}/metrics - Get service metrics
```

### Lifecycle Control
```
POST /api/lifecycle/action           - Execute lifecycle action
POST /api/lifecycle/start/{id}       - Start service
POST /api/lifecycle/stop/{id}        - Stop service (requires confirmation)
POST /api/lifecycle/restart/{id}     - Restart service (requires confirmation)
POST /api/lifecycle/scale-up/{id}    - Scale up instances
POST /api/lifecycle/scale-down/{id}  - Scale down instances
```

### Incidents
```
GET  /api/incidents                  - List all incidents
GET  /api/incidents/active           - List active incidents
POST /api/incidents/{id}/acknowledge - Acknowledge incident
POST /api/incidents/{id}/resolve     - Resolve incident
POST /api/incidents/{id}/close       - Close incident
```

### Audit Logs
```
GET /api/audit              - List all audit logs
GET /api/audit/recent       - Recent actions
GET /api/audit/failed       - Failed actions
GET /api/audit/service/{id} - Actions for specific service
```

## WebSocket Events

Connect to `ws://localhost:8080/ws/dashboard` for real-time updates:

### Incoming Events
- `DASHBOARD_UPDATE` - Full dashboard data refresh
- `HEALTH_UPDATE` - Service health status change
- `METRICS_UPDATE` - New metrics collected
- `INCIDENT_CREATED` - New incident detected
- `ACTION_EXECUTED` - Lifecycle action completed

### Outgoing Commands
- `SUBSCRIBE_SERVICE` - Subscribe to specific service updates
- `UNSUBSCRIBE_SERVICE` - Unsubscribe from service
- `GET_DASHBOARD` - Request dashboard update

## Configuration

Key configuration in `application.yml`:

```yaml
app:
  monitoring:
    health-check-interval: 10000   # Backend health checks
    metrics-poll-interval: 15000   # Metrics collection
    frontend-check-interval: 30000 # Frontend health checks
  
  ai:
    ollama:
      base-url: http://localhost:11434
      model: llama3.2:1b
      enabled: true
  
  lifecycle:
    max-restart-attempts: 3
    restart-cooldown-seconds: 60
    graceful-shutdown-timeout: 30
```

## Architecture

```
Management Backend
 ├── Service Registry         - Track managed services
 ├── Health & Metrics Engine  - Monitor health and collect metrics
 ├── Lifecycle Controller     - Execute safe lifecycle operations
 ├── Frontend Monitor         - HTTP/synthetic checks for frontends
 ├── Scheduler                - Polling and background jobs
 ├── AI Intelligence Layer    - Ollama integration for insights
 ├── Audit & Safety Engine    - RBAC and complete audit trail
 └── API Layer                - REST + WebSocket endpoints
```

## MVP Constraints

This is an MVP with explicit boundaries:

- ❌ No Kubernetes orchestration (yet)
- ❌ No full auto-remediation
- ❌ No arbitrary shell execution
- ❌ No AI-driven killing of services without confirmation

All destructive actions require explicit confirmation and are fully audited.

## Technology Stack

- **Framework**: Spring Boot 3.2
- **Database**: H2 (embedded) / PostgreSQL (production)
- **Security**: Spring Security + JWT
- **AI**: Ollama with llama3.2:1b
- **Real-time**: WebSocket
- **Build**: Maven

