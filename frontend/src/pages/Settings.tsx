import { motion } from 'framer-motion';
import {
  Settings as SettingsIcon,
  User,
  Bell,
  Shield,
  Palette,
  Sparkles,
  Globe,
  Key,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, Button, Badge, Input } from '@/components/ui';
import { useAuthStore } from '@/lib/store';
import { cn } from '@/lib/utils';

export function Settings() {
  const { user } = useAuthStore();

  return (
    <div className="space-y-6 max-w-4xl">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-obsidian-100">Settings</h1>
        <p className="text-obsidian-400 mt-1">
          Manage your account and preferences
        </p>
      </div>

      {/* Profile */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <User className="w-5 h-5 text-emerald-400" />
            Profile
          </CardTitle>
        </CardHeader>
        <div className="space-y-6">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center text-2xl font-bold text-white">
              {user?.username?.charAt(0).toUpperCase()}
            </div>
            <div>
              <h3 className="text-lg font-semibold text-obsidian-100">{user?.username}</h3>
              <p className="text-obsidian-400">{user?.email}</p>
              <Badge variant="info" className="mt-2">{user?.role}</Badge>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Input label="Username" value={user?.username || ''} disabled />
            <Input label="Email" value={user?.email || ''} disabled />
          </div>
        </div>
      </Card>

      {/* Notifications */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Bell className="w-5 h-5 text-amber-400" />
            Notifications
          </CardTitle>
        </CardHeader>
        <div className="space-y-4">
          <SettingRow
            title="Email Notifications"
            description="Receive email alerts for critical incidents"
            enabled={true}
          />
          <SettingRow
            title="Browser Notifications"
            description="Get push notifications in your browser"
            enabled={false}
          />
          <SettingRow
            title="Sound Alerts"
            description="Play sound for critical alerts"
            enabled={true}
          />
        </div>
      </Card>

      {/* Security */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-rose-400" />
            Security
          </CardTitle>
        </CardHeader>
        <div className="space-y-4">
          <div className="flex items-center justify-between p-4 bg-obsidian-800 rounded-lg">
            <div>
              <p className="font-medium text-obsidian-200">Password</p>
              <p className="text-sm text-obsidian-400">Last changed 30 days ago</p>
            </div>
            <Button variant="outline" size="sm" icon={<Key className="w-4 h-4" />}>
              Change
            </Button>
          </div>
          <div className="flex items-center justify-between p-4 bg-obsidian-800 rounded-lg">
            <div>
              <p className="font-medium text-obsidian-200">Two-Factor Authentication</p>
              <p className="text-sm text-obsidian-400">Add an extra layer of security</p>
            </div>
            <Badge variant="outline">Not Enabled</Badge>
          </div>
        </div>
      </Card>

      {/* Appearance */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Palette className="w-5 h-5 text-violet-400" />
            Appearance
          </CardTitle>
        </CardHeader>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-obsidian-200">Theme</p>
              <p className="text-sm text-obsidian-400">Choose your preferred theme</p>
            </div>
            <div className="flex items-center gap-2">
              <button className="w-10 h-10 rounded-lg bg-obsidian-950 border-2 border-emerald-500 flex items-center justify-center">
                <span className="text-xs">🌙</span>
              </button>
              <button className="w-10 h-10 rounded-lg bg-white border border-obsidian-700 flex items-center justify-center">
                <span className="text-xs">☀️</span>
              </button>
            </div>
          </div>
          <SettingRow
            title="Compact Mode"
            description="Reduce spacing for more content"
            enabled={false}
          />
          <SettingRow
            title="Animations"
            description="Enable UI animations and transitions"
            enabled={true}
          />
        </div>
      </Card>

      {/* AI Settings */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-violet-400" />
            AI Assistant
          </CardTitle>
        </CardHeader>
        <div className="space-y-4">
          <SettingRow
            title="AI Analysis"
            description="Enable AI-powered service analysis"
            enabled={true}
          />
          <SettingRow
            title="Auto Recommendations"
            description="Show AI recommendations automatically"
            enabled={true}
          />
          <div className="p-4 bg-violet-600 border border-violet-500 rounded-lg">
            <div className="flex items-center gap-2 mb-2">
              <Sparkles className="w-4 h-4 text-white" />
              <span className="text-sm font-medium text-white">Ollama Status</span>
            </div>
            <p className="text-sm text-white">Connected to local Ollama instance</p>
            <p className="text-xs text-violet-200 mt-1">Model: llama3.2:1b</p>
          </div>
        </div>
      </Card>
    </div>
  );
}

function SettingRow({
  title,
  description,
  enabled,
}: {
  title: string;
  description: string;
  enabled: boolean;
}) {
  return (
    <div className="flex items-center justify-between p-4 bg-obsidian-800 rounded-lg">
      <div>
        <p className="font-medium text-obsidian-200">{title}</p>
        <p className="text-sm text-obsidian-400">{description}</p>
      </div>
      <button
        className={cn(
          'w-12 h-6 rounded-full transition-colors relative',
          enabled ? 'bg-emerald-500' : 'bg-obsidian-700'
        )}
      >
        <motion.div
          className="w-5 h-5 bg-white rounded-full absolute top-0.5"
          animate={{ left: enabled ? 26 : 2 }}
          transition={{ type: 'spring', stiffness: 500, damping: 30 }}
        />
      </button>
    </div>
  );
}
