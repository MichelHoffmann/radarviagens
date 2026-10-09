import React, { useState, useEffect } from 'react';
import { DriverSettings, RideData, RideEvaluation, ScanHistoryItem } from './types';
import { SAMPLE_RIDES } from './data/sampleRides';
import { evaluateRide } from './utils/rideCalculator';
import { TopNavbar } from './components/TopNavbar';
import { PhoneSimulator } from './components/PhoneSimulator';
import { ConfigPanel } from './components/ConfigPanel';
import { ManualRideTester } from './components/ManualRideTester';
import { ScreenScannerUploader } from './components/ScreenScannerUploader';
import { RideHistoryView } from './components/RideHistoryView';
import { NativeAndroidCodeView } from './components/NativeAndroidCodeView';
import { playAlertSound } from './utils/audioAlerts';

const DEFAULT_SETTINGS: DriverSettings = {
  isEnabled: false,
  minPricePerKm: 2.00,
  minPricePerHour: 35.00,
  fuelPricePerLiter: 5.85,
  vehicleConsumptionKmPerLiter: 11.5,
  additionalCostPerKm: 0.20,
  soundAlertsEnabled: true,
  vibrationAlertsEnabled: true,
  autoDismissSeconds: 12,
  targetApps: {
    uber: true,
    ninetyNine: true,
    inDrive: true,
  },
  overlayPosition: { x: 20, y: 120 },
  compactMode: false,
};

export default function App() {
  const [activeTab, setActiveTab] = useState<'simulator' | 'settings' | 'scanner' | 'history' | 'native-code'>('simulator');
  const [settings, setSettings] = useState<DriverSettings>(() => {
    try {
      const saved = localStorage.getItem('radar_driver_settings');
      if (saved) return JSON.parse(saved);
    } catch {
      // Ignore
    }
    return DEFAULT_SETTINGS;
  });

  // Corrida padrão da 99 para simulação
  const [currentRide, setCurrentRide] = useState<RideData>(SAMPLE_RIDES[1]);

  // Histórico de corridas analisadas
  const [history, setHistory] = useState<ScanHistoryItem[]>(() => {
    // Inicializa com alguns exemplos avaliados com os 3 níveis
    const initial1 = evaluateRide(SAMPLE_RIDES[0], DEFAULT_SETTINGS);
    const initial2 = evaluateRide(SAMPLE_RIDES[1], DEFAULT_SETTINGS);
    const initial3 = evaluateRide(SAMPLE_RIDES[2], DEFAULT_SETTINGS);
    return [
      { ...initial1, evaluatedAt: 'Há 5 min', actionTaken: 'accepted' },
      { ...initial2, evaluatedAt: 'Há 18 min', actionTaken: 'ignored' },
      { ...initial3, evaluatedAt: 'Há 32 min', actionTaken: 'declined' },
    ];
  });

  useEffect(() => {
    try {
      localStorage.setItem('radar_driver_settings', JSON.stringify(settings));
    } catch {
      // Ignore
    }
  }, [settings]);

  const handleToggleEnabled = () => {
    const updated = !settings.isEnabled;
    setSettings((prev) => ({ ...prev, isEnabled: updated }));
    if (updated && settings.soundAlertsEnabled) {
      playAlertSound('green');
    }
  };

  const handleUpdateSettings = (partial: Partial<DriverSettings>) => {
    setSettings((prev) => ({ ...prev, ...partial }));
  };

  const handleResetDefaults = () => {
    setSettings(DEFAULT_SETTINGS);
  };

  const handleRideAction = (evaluation: RideEvaluation, action: 'accepted' | 'declined' | 'ignored') => {
    const historyItem: ScanHistoryItem = {
      ...evaluation,
      actionTaken: action,
      evaluatedAt: 'Agora mesmo',
    };
    setHistory((prev) => [historyItem, ...prev]);

    // Próxima corrida sugerida após ação
    const currentIndex = SAMPLE_RIDES.findIndex((r) => r.id === currentRide.id);
    const nextIndex = (currentIndex + 1) % SAMPLE_RIDES.length;
    setCurrentRide(SAMPLE_RIDES[nextIndex]);
  };

  const handleSelectRide = (ride: RideData) => {
    setCurrentRide(ride);
    const evalItem = evaluateRide(ride, settings);
    const historyItem: ScanHistoryItem = {
      ...evalItem,
      evaluatedAt: 'Agora mesmo',
    };
    setHistory((prev) => [historyItem, ...prev]);
  };

  const handleSendToPhoneFromOtherTabs = (ride: RideData) => {
    setCurrentRide(ride);
    setActiveTab('simulator');
    if (settings.isEnabled && settings.soundAlertsEnabled) {
      const evalItem = evaluateRide(ride, settings);
      playAlertSound(evalItem.verdict);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-emerald-500 selection:text-white">
      {/* Top Navbar Contract */}
      <TopNavbar
        settings={settings}
        onToggleEnabled={handleToggleEnabled}
        activeTab={activeTab}
        onSelectTab={setActiveTab}
      />

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6 sm:py-8">
        {activeTab === 'simulator' && (
          <div className="space-y-8">
            <PhoneSimulator
              settings={settings}
              currentRide={currentRide}
              onSelectRide={handleSelectRide}
              onRideAction={handleRideAction}
              onOpenSettings={() => setActiveTab('settings')}
            />

            {/* Calculadora Manual Rápida no rodapé do simulador */}
            <ManualRideTester
              settings={settings}
              onSendToPhone={handleSendToPhoneFromOtherTabs}
            />
          </div>
        )}

        {activeTab === 'settings' && (
          <ConfigPanel
            settings={settings}
            onUpdateSettings={handleUpdateSettings}
            onResetDefaults={handleResetDefaults}
          />
        )}

        {activeTab === 'scanner' && (
          <ScreenScannerUploader
            settings={settings}
            onSendToPhone={handleSendToPhoneFromOtherTabs}
          />
        )}

        {activeTab === 'history' && (
          <RideHistoryView
            history={history}
            onClearHistory={() => setHistory([])}
          />
        )}

        {activeTab === 'native-code' && <NativeAndroidCodeView />}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-900 bg-slate-950 py-6 px-4 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
            <span className="text-slate-400 font-semibold">Radar de Corridas Android</span>
            <span>·</span>
            <span>Escaneamento em tempo real para motoristas de app</span>
          </div>

          <div className="flex items-center gap-4 text-slate-400">
            <span>🟢 Verde (2 critérios)</span>
            <span>·</span>
            <span>🟡 Amarelo (1 critério)</span>
            <span>·</span>
            <span>🔴 Vermelho (0 critérios)</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
