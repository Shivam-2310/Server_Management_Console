import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  Search,
  User,
  LogOut,
  ChevronDown,
  Sparkles,
} from 'lucide-react';
import { useAuthStore, useDashboardStore } from '@/lib/store';
import { cn } from '@/lib/utils';
import api from '@/lib/api';
import { NotificationDropdown } from '../NotificationDropdown';

export function Header() {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const { dashboard } = useDashboardStore();
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const handleLogout = () => {
    api.logout();
    logout();
    navigate('/login');
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/logs?search=${encodeURIComponent(searchQuery)}`);
    }
  };

  return (
    <header className="h-16 bg-obsidian-950 border-b border-obsidian-800 px-6 flex items-center justify-between">
      {/* Search */}
      <form onSubmit={handleSearch} className="relative w-96">
        <motion.div
          className="absolute left-3 top-1/2 -translate-y-1/2"
          whileHover={{ scale: 1.2, rotate: 90 }}
          transition={{ type: 'spring', stiffness: 400, damping: 17 }}
        >
          <Search className="w-4 h-4 text-obsidian-400" />
        </motion.div>
        <input
          type="text"
          placeholder="Search services, logs, incidents..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-10 pr-4 py-2 bg-obsidian-900 border border-obsidian-800 rounded-lg text-sm text-obsidian-200 placeholder:text-obsidian-500 focus:outline-none focus:border-emerald-500 transition-colors"
        />
        <kbd className="absolute right-3 top-1/2 -translate-y-1/2 px-1.5 py-0.5 text-[10px] text-obsidian-400 bg-obsidian-800 rounded border border-obsidian-700">
          ⌘K
        </kbd>
      </form>

      {/* Right Section */}
      <div className="flex items-center gap-4">
        {/* AI Status */}
        <div className="flex items-center gap-2 px-3 py-1.5 bg-violet-600 border border-violet-500 rounded-lg">
          <motion.div
            animate={{ rotate: [0, 360] }}
            transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
            whileHover={{ scale: 1.3, rotate: 0 }}
          >
            <Sparkles className="w-4 h-4 text-white" style={{ strokeWidth: 2.5 }} />
          </motion.div>
          <span className="text-xs font-medium text-white">AI Active</span>
        </div>

        {/* Notifications */}
        <div className="relative">
          <motion.button
            onClick={() => setShowNotifications(!showNotifications)}
            className="relative p-2 text-obsidian-300 hover:text-white hover:bg-obsidian-800 rounded-lg transition-colors"
            whileHover={{ scale: 1.1, rotate: [0, -10, 10, -10, 0] }}
            whileTap={{ scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 400, damping: 17 }}
          >
            <Bell className="w-5 h-5" style={{ strokeWidth: 2.5 }} />
            {((dashboard?.activeIncidentsList?.length || 0) > 0) && (
              <motion.span
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                className="absolute -top-0.5 -right-0.5 w-5 h-5 bg-rose-500 text-white text-[10px] font-bold flex items-center justify-center rounded-full"
              >
                {dashboard?.activeIncidentsList?.length || 0}
              </motion.span>
            )}
          </motion.button>
          <NotificationDropdown
            isOpen={showNotifications}
            onClose={() => setShowNotifications(false)}
          />
        </div>

        {/* User Menu */}
        <div className="relative">
          <button
            onClick={() => setShowUserMenu(!showUserMenu)}
            className="flex items-center gap-3 p-2 hover:bg-obsidian-800 rounded-lg transition-colors"
          >
            <motion.div
              className="w-8 h-8 bg-gradient-to-br from-emerald-500 to-cyan-500 rounded-lg flex items-center justify-center"
              whileHover={{ scale: 1.1, rotate: 360 }}
              whileTap={{ scale: 0.9 }}
              transition={{ type: 'spring', stiffness: 300, damping: 20 }}
            >
              <User className="w-4 h-4 text-white" style={{ strokeWidth: 2.5 }} />
            </motion.div>
            <div className="text-left hidden md:block">
              <p className="text-sm font-medium text-obsidian-200">{user?.username}</p>
              <p className="text-xs text-obsidian-400">{user?.role}</p>
            </div>
            <motion.div
              animate={{ rotate: showUserMenu ? 180 : 0 }}
              transition={{ type: 'spring', stiffness: 300, damping: 20 }}
            >
              <ChevronDown className="w-4 h-4 text-obsidian-300" style={{ strokeWidth: 2.5 }} />
            </motion.div>
          </button>

          <AnimatePresence>
            {showUserMenu && (
              <>
                <div
                  className="fixed inset-0 z-30"
                  onClick={() => setShowUserMenu(false)}
                />
                <div className="absolute right-0 mt-2 w-56 bg-obsidian-900 border border-obsidian-800 rounded-xl z-40 overflow-hidden">
                  <div className="p-4 border-b border-obsidian-800">
                    <p className="font-medium text-obsidian-200">{user?.username}</p>
                    <p className="text-sm text-obsidian-400">{user?.email}</p>
                  </div>
                  <div className="p-2">
                    <button
                      onClick={handleLogout}
                      className="w-full flex items-center gap-3 px-3 py-2 text-rose-400 hover:bg-rose-600 hover:text-white rounded-lg transition-colors"
                    >
                      <motion.div
                        whileHover={{ scale: 1.2, rotate: 15 }}
                        whileTap={{ scale: 0.9 }}
                      >
                        <LogOut className="w-4 h-4" style={{ strokeWidth: 2.5 }} />
                      </motion.div>
                      Sign Out
                    </button>
                  </div>
                </div>
              </>
            )}
          </AnimatePresence>
        </div>
      </div>
    </header>
  );
}
