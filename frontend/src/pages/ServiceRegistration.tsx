import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Server,
  ArrowLeft,
  AlertCircle,
  CheckCircle,
  Loader2,
  Info,
  Code,
  Globe,
  Settings,
  Terminal,
  Tag,
} from 'lucide-react';
import { Button, Input, Card } from '@/components/ui';
import api from '@/lib/api';
import type { ServiceType } from '@/types';

export function ServiceRegistration() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  
  const [activeTab, setActiveTab] = useState<'basic' | 'monitoring' | 'lifecycle' | 'advanced'>('basic');
  const [form, setForm] = useState({
    // Basic Info
    name: '',
    description: '',
    serviceType: 'BACKEND' as ServiceType,
    host: '',
    port: '',
    environment: 'DEV',
    
    // Monitoring
    baseUrl: '',
    healthEndpoint: '',
    metricsEndpoint: '',
    actuatorBasePath: '/actuator',
    
    // Frontend specific
    frontendTechnology: '',
    servingTechnology: '',
    
    // Lifecycle
    startCommand: '',
    stopCommand: '',
    restartCommand: '',
    workingDirectory: '',
    processIdentifier: '',
    
    // Advanced
    tags: '',
  });

  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setForm({ ...form, [name]: value });
    setError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Validation
    if (!form.name.trim()) {
      setError('Service name is required');
      return;
    }
    if (!form.host.trim()) {
      setError('Host is required');
      return;
    }
    if (!form.port || isNaN(Number(form.port)) || Number(form.port) <= 0) {
      setError('Valid port number is required');
      return;
    }

    setLoading(true);

    try {
      // Parse tags
      const tagsArray = form.tags
        ? form.tags.split(',').map(tag => tag.trim()).filter(tag => tag.length > 0)
        : [];

      const serviceData: any = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        serviceType: form.serviceType,
        host: form.host.trim(),
        port: Number(form.port),
        environment: form.environment,
        tags: tagsArray.length > 0 ? tagsArray : undefined,
      };

      // Add optional fields only if they have values
      if (form.baseUrl.trim()) serviceData.baseUrl = form.baseUrl.trim();
      if (form.healthEndpoint.trim()) serviceData.healthEndpoint = form.healthEndpoint.trim();
      if (form.metricsEndpoint.trim()) serviceData.metricsEndpoint = form.metricsEndpoint.trim();
      
      if (form.serviceType === 'BACKEND' && form.actuatorBasePath.trim()) {
        serviceData.actuatorBasePath = form.actuatorBasePath.trim();
      }
      
      if (form.serviceType === 'FRONTEND') {
        if (form.frontendTechnology.trim()) serviceData.frontendTechnology = form.frontendTechnology.trim();
        if (form.servingTechnology.trim()) serviceData.servingTechnology = form.servingTechnology.trim();
      }

      if (form.startCommand.trim()) serviceData.startCommand = form.startCommand.trim();
      if (form.stopCommand.trim()) serviceData.stopCommand = form.stopCommand.trim();
      if (form.restartCommand.trim()) serviceData.restartCommand = form.restartCommand.trim();
      if (form.workingDirectory.trim()) serviceData.workingDirectory = form.workingDirectory.trim();
      if (form.processIdentifier.trim()) serviceData.processIdentifier = form.processIdentifier.trim();

      const newService = await api.createService(serviceData);
      
      // Invalidate and refetch services query to refresh the list
      await queryClient.invalidateQueries({ queryKey: ['services'] });
      await queryClient.refetchQueries({ queryKey: ['services'] });
      
      setSuccess(true);
      setTimeout(() => navigate('/services'), 1500);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setError(error.response?.data?.message || 'Failed to register service');
    } finally {
      setLoading(false);
    }
  };

  const tabs = [
    { id: 'basic', label: 'Basic Info', icon: Server },
    { id: 'monitoring', label: 'Monitoring', icon: Globe },
    { id: 'lifecycle', label: 'Lifecycle', icon: Terminal },
    { id: 'advanced', label: 'Advanced', icon: Settings },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => navigate('/services')}
          icon={<ArrowLeft className="w-4 h-4" />}
        >
          Back
        </Button>
        <div>
          <h1 className="text-2xl font-bold text-obsidian-100">Register New Service</h1>
          <p className="text-obsidian-400 mt-1">Add a new service or application to monitor</p>
        </div>
      </div>

      {/* Success Message */}
      {success && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-emerald-500/10 border border-emerald-500/50 rounded-lg p-4 flex items-center gap-3"
        >
          <CheckCircle className="w-5 h-5 text-emerald-400" />
          <div>
            <p className="text-emerald-400 font-medium">Service registered successfully!</p>
            <p className="text-emerald-400/70 text-sm">Redirecting to services list...</p>
          </div>
        </motion.div>
      )}

      {/* Error Message */}
      {error && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-rose-500/10 border border-rose-500/50 rounded-lg p-4 flex items-center gap-3"
        >
          <AlertCircle className="w-5 h-5 text-rose-400" />
          <p className="text-rose-400">{error}</p>
        </motion.div>
      )}

      <form onSubmit={handleSubmit}>
        <Card className="p-6">
          {/* Tabs */}
          <div className="flex items-center gap-2 border-b border-obsidian-800 pb-4 mb-6 overflow-x-auto">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id as typeof activeTab)}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap ${
                    activeTab === tab.id
                      ? 'bg-emerald-600 text-white'
                      : 'text-obsidian-400 hover:text-obsidian-200 hover:bg-obsidian-800'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  {tab.label}
                </button>
              );
            })}
          </div>

          {/* Tab Content */}
          <div className="space-y-6">
            {/* Basic Info Tab */}
            {activeTab === 'basic' && (
              <motion.div
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                className="space-y-4"
              >
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Service Name <span className="text-rose-400">*</span>
                    </label>
                    <Input
                      name="name"
                      value={form.name}
                      onChange={handleChange}
                      placeholder="e.g., order-service"
                      required
                    />
                    <p className="text-xs text-obsidian-500 mt-1">Unique identifier for this service</p>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Service Type <span className="text-rose-400">*</span>
                    </label>
                    <select
                      name="serviceType"
                      value={form.serviceType}
                      onChange={handleChange}
                      className="w-full px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500"
                      required
                    >
                      <option value="BACKEND">Backend</option>
                      <option value="FRONTEND">Frontend</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-obsidian-300 mb-2">
                    Description
                  </label>
                  <textarea
                    name="description"
                    value={form.description}
                    onChange={handleChange}
                    placeholder="Brief description of the service"
                    rows={3}
                    className="w-full px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 placeholder:text-obsidian-500 focus:outline-none focus:border-emerald-500 resize-none"
                  />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Host <span className="text-rose-400">*</span>
                    </label>
                    <Input
                      name="host"
                      value={form.host}
                      onChange={handleChange}
                      placeholder="localhost or IP"
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Port <span className="text-rose-400">*</span>
                    </label>
                    <Input
                      name="port"
                      type="number"
                      value={form.port}
                      onChange={handleChange}
                      placeholder="8080"
                      min="1"
                      max="65535"
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Environment
                    </label>
                    <select
                      name="environment"
                      value={form.environment}
                      onChange={handleChange}
                      className="w-full px-4 py-2.5 bg-obsidian-900 border border-obsidian-800 rounded-lg text-obsidian-200 focus:outline-none focus:border-emerald-500"
                    >
                      <option value="DEV">Development</option>
                      <option value="STAGING">Staging</option>
                      <option value="PROD">Production</option>
                    </select>
                  </div>
                </div>

                {form.serviceType === 'FRONTEND' && (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4 border-t border-obsidian-800">
                    <div>
                      <label className="block text-sm font-medium text-obsidian-300 mb-2">
                        Frontend Technology
                      </label>
                      <Input
                        name="frontendTechnology"
                        value={form.frontendTechnology}
                        onChange={handleChange}
                        placeholder="e.g., React, Angular, Vue"
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium text-obsidian-300 mb-2">
                        Serving Technology
                      </label>
                      <Input
                        name="servingTechnology"
                        value={form.servingTechnology}
                        onChange={handleChange}
                        placeholder="e.g., Nginx, Node, Static"
                      />
                    </div>
                  </div>
                )}
              </motion.div>
            )}

            {/* Monitoring Tab */}
            {activeTab === 'monitoring' && (
              <motion.div
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                className="space-y-4"
              >
                <div className="bg-cyan-500/10 border border-cyan-500/30 rounded-lg p-4 flex items-start gap-3">
                  <Info className="w-5 h-5 text-cyan-400 mt-0.5 flex-shrink-0" />
                  <div className="text-sm text-obsidian-300">
                    <p className="font-medium text-cyan-400 mb-1">Monitoring Configuration</p>
                    <p>Configure how the system monitors your service. Leave empty to use defaults.</p>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-obsidian-300 mb-2">
                    Base URL (Optional)
                  </label>
                  <Input
                    name="baseUrl"
                    value={form.baseUrl}
                    onChange={handleChange}
                    placeholder="http://localhost:8080"
                  />
                  <p className="text-xs text-obsidian-500 mt-1">Full base URL (overrides host:port)</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Health Endpoint
                    </label>
                    <Input
                      name="healthEndpoint"
                      value={form.healthEndpoint}
                      onChange={handleChange}
                      placeholder={form.serviceType === 'BACKEND' ? '/actuator/health' : '/'}
                    />
                    <p className="text-xs text-obsidian-500 mt-1">
                      {form.serviceType === 'BACKEND'
                        ? 'Spring Boot Actuator health endpoint'
                        : 'Health check endpoint path'}
                    </p>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Metrics Endpoint
                    </label>
                    <Input
                      name="metricsEndpoint"
                      value={form.metricsEndpoint}
                      onChange={handleChange}
                      placeholder={form.serviceType === 'BACKEND' ? '/actuator/metrics' : '/metrics'}
                    />
                  </div>
                </div>

                {form.serviceType === 'BACKEND' && (
                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Actuator Base Path
                    </label>
                    <Input
                      name="actuatorBasePath"
                      value={form.actuatorBasePath}
                      onChange={handleChange}
                      placeholder="/actuator"
                    />
                    <p className="text-xs text-obsidian-500 mt-1">
                      Spring Boot Actuator base path (default: /actuator)
                    </p>
                  </div>
                )}
              </motion.div>
            )}

            {/* Lifecycle Tab */}
            {activeTab === 'lifecycle' && (
              <motion.div
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                className="space-y-4"
              >
                <div className="bg-amber-500/10 border border-amber-500/30 rounded-lg p-4 flex items-start gap-3">
                  <Info className="w-5 h-5 text-amber-400 mt-0.5 flex-shrink-0" />
                  <div className="text-sm text-obsidian-300">
                    <p className="font-medium text-amber-400 mb-1">Lifecycle Commands (Optional)</p>
                    <p>Configure commands to start, stop, and restart your service. These are optional - you can register for monitoring only.</p>
                    <p className="mt-2 text-xs text-obsidian-400">
                      <strong>Allowed prefixes:</strong> java, node, npm, yarn, python, mvn, gradle, docker, systemctl, pm2, nginx, etc.
                    </p>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-obsidian-300 mb-2">
                    Start Command
                  </label>
                  <Input
                    name="startCommand"
                    value={form.startCommand}
                    onChange={handleChange}
                    placeholder="e.g., java -jar app.jar"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-obsidian-300 mb-2">
                    Stop Command
                  </label>
                  <Input
                    name="stopCommand"
                    value={form.stopCommand}
                    onChange={handleChange}
                    placeholder="e.g., pkill -f app.jar"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-obsidian-300 mb-2">
                    Restart Command
                  </label>
                  <Input
                    name="restartCommand"
                    value={form.restartCommand}
                    onChange={handleChange}
                    placeholder="e.g., systemctl restart my-service"
                  />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Working Directory
                    </label>
                    <Input
                      name="workingDirectory"
                      value={form.workingDirectory}
                      onChange={handleChange}
                      placeholder="/opt/services/myapp"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-obsidian-300 mb-2">
                      Process Identifier
                    </label>
                    <Input
                      name="processIdentifier"
                      value={form.processIdentifier}
                      onChange={handleChange}
                      placeholder="app.jar or /var/run/app.pid"
                    />
                  </div>
                </div>
              </motion.div>
            )}

            {/* Advanced Tab */}
            {activeTab === 'advanced' && (
              <motion.div
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                className="space-y-4"
              >
                <div>
                  <label className="block text-sm font-medium text-obsidian-300 mb-2">
                    Tags
                  </label>
                  <Input
                    name="tags"
                    value={form.tags}
                    onChange={handleChange}
                    placeholder="microservice, api, backend (comma-separated)"
                  />
                  <p className="text-xs text-obsidian-500 mt-1">
                    Comma-separated tags for organizing services
                  </p>
                </div>
              </motion.div>
            )}
          </div>

          {/* Form Actions */}
          <div className="flex items-center justify-end gap-3 pt-6 mt-6 border-t border-obsidian-800">
            <Button
              type="button"
              variant="ghost"
              onClick={() => navigate('/services')}
              disabled={loading}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={loading}
              icon={loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Server className="w-4 h-4" />}
            >
              {loading ? 'Registering...' : 'Register Service'}
            </Button>
          </div>
        </Card>
      </form>
    </div>
  );
}

