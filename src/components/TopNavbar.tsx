import React from 'react';
import { ShieldCheck, ShieldAlert, Smartphone, Sliders, History, Code2, Scan } from 'lucide-react';
import { DriverSettings } from '../types';

interface TopNavbarProps {
  settings: DriverSettings;
  onToggleEnabled: () => void;
  activeTab: 'simulator' | 'settings' | 'scanner' | 'history' | 'native-code';
  onSelectTab: (tab: 'simulator' | 'settings' | 'scanner' | 'history' | 'native-code') => void;
}

export const TopNavbar: React.FC<TopNavbarProps> = ({
  settings,
  onToggleEnabled,
  activeTab,
  onSelectTab,
}) => {
  return (
    <header className="sticky top-0 z-50 bg-slate-950/90 backdrop-blur-md border-b border-slate-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
        {/* Zone 1: Brand title */}
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-400 flex items-center justify-center shadow-lg shadow-emerald-500/20 text-white font-black text-lg">
            R
          </div>
          <div>
            <div className="font-bold text-slate-100 text-base sm:text-lg tracking-tight leading-tight">
              Radar de Corridas
            </div>
            <div className="text-[11px] text-slate-400 hidden sm:block">
              Calculadora Flutuante para Uber e 99
            </div>
          </div>
        </div>

        {/* Zone 2: Navigation Links */}
        <nav className="flex items-center gap-1 sm:gap-2">
          <button
            onClick={() => onSelectTab('simulator')}
            className={`px-3 py-2 text-xs sm:text-sm font-medium rounded-lg transition-colors flex items-center gap-1.5 ${
              activeTab === 'simulator'
                ? 'bg-slate-800 text-emerald-400 shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Smartphone className="w-4 h-4" />
            <span>Simulador</span>
          </button>

          <button
            onClick={() => onSelectTab('settings')}
            className={`px-3 py-2 text-xs sm:text-sm font-medium rounded-lg transition-colors flex items-center gap-1.5 ${
              activeTab === 'settings'
                ? 'bg-slate-800 text-emerald-400 shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Sliders className="w-4 h-4" />
            <span>Configurações</span>
          </button>

          <button
            onClick={() => onSelectTab('scanner')}
            className={`px-3 py-2 text-xs sm:text-sm font-medium rounded-lg transition-colors flex items-center gap-1.5 ${
              activeTab === 'scanner'
                ? 'bg-slate-800 text-emerald-400 shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Scan className="w-4 h-4" />
            <span className="hidden md:inline">Scanner de Tela</span>
            <span className="md:hidden">Scanner</span>
          </button>

          <button
            onClick={() => onSelectTab('history')}
            className={`px-3 py-2 text-xs sm:text-sm font-medium rounded-lg transition-colors flex items-center gap-1.5 ${
              activeTab === 'history'
                ? 'bg-slate-800 text-emerald-400 shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <History className="w-4 h-4" />
            <span className="hidden md:inline">Histórico</span>
          </button>

          <button
            onClick={() => onSelectTab('native-code')}
            className={`px-3 py-2 text-xs sm:text-sm font-medium rounded-lg transition-colors flex items-center gap-1.5 ${
              activeTab === 'native-code'
                ? 'bg-slate-800 text-emerald-400 shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Code2 className="w-4 h-4" />
            <span className="hidden md:inline">Código Android Nativo</span>
            <span className="md:hidden">Código</span>
          </button>
        </nav>

        {/* Zone 3: Primary Action (Toggle app enabled / disabled) */}
        <div className="flex items-center gap-2">
          <button
            onClick={onToggleEnabled}
            className={`px-3 sm:px-4 py-2 rounded-xl text-xs sm:text-sm font-semibold flex items-center gap-2 transition-all ${
              settings.isEnabled
                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-emerald-500/30 shadow-lg shadow-emerald-950/50'
                : 'bg-red-500/20 text-red-400 border border-red-500/40 hover:bg-red-500/30'
            }`}
            title={settings.isEnabled ? 'Clique para desativar o Radar' : 'Clique para ativar o Radar'}
          >
            {settings.isEnabled ? (
              <>
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span className="hidden sm:inline">Radar:</span>
                <span className="font-bold">ATIVADO</span>
              </>
            ) : (
              <>
                <ShieldAlert className="w-4 h-4 text-red-400" />
                <span className="hidden sm:inline">Radar:</span>
                <span className="font-bold">DESLIGADO</span>
              </>
            )}
          </button>
        </div>
      </div>
    </header>
  );
};
