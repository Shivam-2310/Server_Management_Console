# Server Management Console - Frontend

A professional, minimalist React frontend for the Server Management Console with real-time updates, stunning animations, and AI-powered insights.

## ✨ Features

- **Real-time Dashboard** - Live health status, metrics, and incident tracking
- **Service Management** - Start/Stop/Restart services with lifecycle controls
- **Incident Management** - Acknowledge, investigate, and resolve incidents
- **Log Viewer** - Real-time log streaming with filtering
- **AI Analysis** - View AI-generated insights and recommendations
- **Audit Trail** - Complete action history with detailed logs
- **Diagnostics** - JVM metrics, HTTP traces, and environment info

## 🎨 Design

- **Dark Theme** - Professional obsidian-based color scheme
- **Glassmorphism** - Frosted glass effects with backdrop blur
- **Animations** - Smooth transitions powered by Framer Motion
- **Real-time** - WebSocket integration for live updates

## 🛠️ Tech Stack

- **React 18** + **TypeScript** - Type-safe UI development
- **Vite** - Fast build tool and dev server
- **Tailwind CSS 4** - Utility-first styling
- **Framer Motion** - Fluid animations
- **TanStack Query** - Server state management
- **Zustand** - Lightweight client state
- **Recharts** - Beautiful charts
- **Lucide Icons** - Modern icon set

## 🚀 Quick Start

### Prerequisites

- Node.js 18+
- Backend server running on port 8080

### Installation

```bash
cd frontend
npm install
npm run dev
```

The app will be available at `http://localhost:5173`

### Build for Production

```bash
npm run build
npm run preview
```

## 📁 Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── ui/         # Reusable UI components
│   │   └── layout/     # Layout components (Sidebar, Header)
│   ├── pages/          # Route pages
│   ├── lib/
│   │   ├── api.ts      # API client
│   │   ├── store.ts    # Zustand stores
│   │   ├── websocket.ts # WebSocket service
│   │   └── utils.ts    # Utility functions
│   ├── types/          # TypeScript types
│   ├── App.tsx         # Root component
│   ├── main.tsx        # Entry point
│   └── index.css       # Global styles
├── index.html
├── vite.config.ts
├── tailwind.config.ts
└── package.json
```

## 📱 Pages

| Route | Description |
|-------|-------------|
| `/` | Dashboard with real-time stats |
| `/services` | Service list with health status |
| `/services/:id` | Service details with metrics & controls |
| `/incidents` | Active incidents with severity tracking |
| `/audit` | Complete audit trail |
| `/logs` | Real-time log viewer |
| `/diagnostics` | JVM, HTTP, and environment info |
| `/settings` | User preferences |

## 🔐 Authentication

Default credentials:

| Username | Password | Role |
|----------|----------|------|
| admin | admin123 | Admin |
| operator | operator123 | Operator |
| viewer | viewer123 | Viewer |

## 🎯 Key Features

### Dashboard
- Service health overview with animated status dots
- Active incident count with severity breakdown
- Recent actions timeline
- Real-time metrics updates via WebSocket

### Service Management
- Grid/List view toggle
- Health status filtering
- Lifecycle controls (Start/Stop/Restart)
- AI-powered risk analysis
- Performance charts

### Incident Management
- Severity-based color coding
- AI-generated summaries
- Acknowledge/Resolve workflow
- Service linking

### Log Viewer
- Multi-category filtering
- Real-time auto-scroll
- Log level filtering
- Search functionality
- Syntax highlighting

## 🎨 Color Palette

```css
--obsidian-950: #0d0e10  /* Background */
--obsidian-900: #1a1b1f  /* Cards */
--obsidian-800: #34363d  /* Borders */
--emerald-500: #10b981   /* Success */
--amber-500: #f59e0b     /* Warning */
--rose-500: #f43f5e      /* Error */
--cyan-500: #06b6d4      /* Info */
--violet-500: #8b5cf6    /* AI/Special */
```

## 📦 Scripts

```bash
npm run dev      # Start dev server
npm run build    # Production build
npm run preview  # Preview production build
npm run lint     # Run ESLint
```

## 🔌 API Proxy

Development requests to `/api/*` are proxied to `http://localhost:8080` (backend server).

WebSocket connections to `/ws/*` are proxied to `ws://localhost:8080`.
