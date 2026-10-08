import React from 'react';
import {
  Sliders,
  ShieldCheck,
  ShieldAlert,
  Fuel,
  Volume2,
  RotateCcw,
  Smartphone,
  Save,
  CheckCircle2,
  AlertTriangle,
  XCircle
} from 'lucide-react';
import { DriverSettings } from '../types';
import { playAlertSound } from '../utils/audioAlerts';

interface ConfigPanelProps {
  settings: DriverSettings;
  onUpdateSettings: (newSettings: Partial<DriverSettings>) => void;
  onResetDefaults: () => void;
}

export const ConfigPanel: React.FC<ConfigPanelProps> = ({
  settings,
  onUpdateSettings,
  onResetDefaults,
}) => {
  const [saveFeedback, setSaveFeedback] = React.useState(false);

  const handleSave = () => {
    setSaveFeedback(true);
    setTimeout(() => setSaveFeedback(false), 2000);
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Cabeçalho */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-800">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-white flex items-center gap-2.5">
            <Sliders className="w-6 h-6 text-emerald-400" />
            <span>Configurações do Motorista</span>
          </h1>
          <p className="text-xs sm:text-sm text-slate-400 mt-1">
            Defina seus critérios mínimos de aceitação de corrida e gerencie o radar em tempo real.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={onResetDefaults}
            className="px-3 py-2 text-xs font-semibold rounded-xl text-slate-400 hover:text-slate-200 hover:bg-slate-900 border border-slate-800 transition-colors flex items-center gap-1.5"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Padrões</span>
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-2 text-xs font-bold rounded-xl bg-emerald-500 text-emerald-950 hover:bg-emerald-400 transition-colors shadow-lg shadow-emerald-950 flex items-center gap-1.5"
          >
            {saveFeedback ? (
              <>
                <CheckCircle2 className="w-4 h-4" />
                <span>Salvo!</span>
              </>
            ) : (
              <>
                <Save className="w-4 h-4" />
                <span>Salvar Ajustes</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* 1. Ativação Geral do Aplicativo */}
      <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6">
        <div className="flex items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="text-base font-bold text-white">Status do Radar de Corridas</span>
              <span
                className={`text-[11px] font-extrabold px-2 py-0.5 rounded-full ${
                  settings.isEnabled
                    ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                    : 'bg-rose-500/20 text-rose-400 border border-rose-500/40'
                }`}
              >
                {settings.isEnabled ? 'EM OPERAÇÃO' : 'DESATIVADO'}
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Quando habilitado, o app monitora ativamente as chamadas da Uber e 99 e exibe o pop-up
              flutuante instantâneo.
            </p>
          </div>

          <button
            onClick={() => onUpdateSettings({ isEnabled: !settings.isEnabled })}
            className={`w-14 h-8 rounded-full transition-colors relative p-1 shrink-0 ${
              settings.isEnabled ? 'bg-emerald-500' : 'bg-slate-700'
            }`}
          >
            <div
              className={`w-6 h-6 rounded-full bg-white shadow-md transition-transform ${
                settings.isEnabled ? 'translate-x-6' : 'translate-x-0'
              }`}
            />
          </button>
        </div>
      </div>

      {/* 2. Critérios Principais: R$/km e R$/hora */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Card Meta R$/km */}
        <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-emerald-400 uppercase tracking-wider">Critério 1</span>
              <h3 className="text-base font-bold text-white mt-0.5">Meta Mínima R$ por KM</h3>
            </div>
            <div className="text-2xl font-black text-emerald-400 tabular-nums">
              R$ {settings.minPricePerKm.toFixed(2)}
              <span className="text-xs font-normal text-slate-400">/km</span>
            </div>
          </div>

          <p className="text-xs text-slate-400">
            Corridas que pagarem menos do que este valor por quilômetro total não atingirão este critério.
          </p>

          <div className="space-y-2 pt-2">
            <input
              type="range"
              min="1.00"
              max="4.50"
              step="0.05"
              value={settings.minPricePerKm}
              onChange={(e) => onUpdateSettings({ minPricePerKm: parseFloat(e.target.value) })}
              className="w-full accent-emerald-500 cursor-pointer h-2 bg-slate-800 rounded-lg"
            />
            <div className="flex justify-between text-[11px] text-slate-500 font-mono">
              <span>R$ 1,00/km</span>
              <span>R$ 2,00/km (Médio)</span>
              <span>R$ 4,50/km</span>
            </div>
          </div>

          {/* Atalhos rápidos */}
          <div className="flex gap-2 pt-1">
            {[1.80, 2.00, 2.20, 2.50, 3.00].map((val) => (
              <button
                key={val}
                onClick={() => onUpdateSettings({ minPricePerKm: val })}
                className={`px-2.5 py-1 text-xs rounded-lg font-medium transition-colors ${
                  settings.minPricePerKm === val
                    ? 'bg-emerald-500 text-emerald-950 font-bold'
                    : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                }`}
              >
                R$ {val.toFixed(2)}
              </button>
            ))}
          </div>
        </div>

        {/* Card Meta R$/hora */}
        <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-teal-400 uppercase tracking-wider">Critério 2</span>
              <h3 className="text-base font-bold text-white mt-0.5">Meta Mínima R$ por HORA</h3>
            </div>
            <div className="text-2xl font-black text-teal-400 tabular-nums">
              R$ {settings.minPricePerHour.toFixed(2)}
              <span className="text-xs font-normal text-slate-400">/h</span>
            </div>
          </div>

          <p className="text-xs text-slate-400">
            Projeção por hora com base no tempo estimado (busca + viagem). Protege contra engarrafamentos.
          </p>

          <div className="space-y-2 pt-2">
            <input
              type="range"
              min="20.00"
              max="90.00"
              step="1.00"
              value={settings.minPricePerHour}
              onChange={(e) => onUpdateSettings({ minPricePerHour: parseFloat(e.target.value) })}
              className="w-full accent-teal-500 cursor-pointer h-2 bg-slate-800 rounded-lg"
            />
            <div className="flex justify-between text-[11px] text-slate-500 font-mono">
              <span>R$ 20/h</span>
              <span>R$ 40/h (Recomendado)</span>
              <span>R$ 90/h</span>
            </div>
          </div>

          {/* Atalhos rápidos */}
          <div className="flex gap-2 pt-1">
            {[30, 35, 40, 45, 50].map((val) => (
              <button
                key={val}
                onClick={() => onUpdateSettings({ minPricePerHour: val })}
                className={`px-2.5 py-1 text-xs rounded-lg font-medium transition-colors ${
                  settings.minPricePerHour === val
                    ? 'bg-teal-500 text-teal-950 font-bold'
                    : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                }`}
              >
                R$ {val}/h
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* 3. Tabela de Classificação do Pop-up (Os 3 Níveis) */}
      <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 space-y-4">
        <h3 className="text-base font-bold text-white">Como Funciona a Avaliação das Cores</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="p-4 rounded-2xl bg-emerald-950/40 border border-emerald-500/40 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-extrabold text-emerald-400 text-sm flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4" />
                <span>🟢 VERDE</span>
              </span>
              <button
                onClick={() => playAlertSound('green')}
                className="text-xs text-emerald-400 hover:text-emerald-200 flex items-center gap-1"
                title="Ouvir som do alerta verde"
              >
                <Volume2 className="w-3.5 h-3.5" />
                <span>Testar Som</span>
              </button>
            </div>
            <div className="text-xs font-semibold text-slate-200">Atende todos os requisitos</div>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              Tanto o R$/km &ge; R$ {settings.minPricePerKm.toFixed(2)} quanto o R$/hora &ge; R$ {settings.minPricePerHour.toFixed(2)}. Corrida altamente lucrativa.
            </p>
          </div>

          <div className="p-4 rounded-2xl bg-amber-950/40 border border-amber-500/40 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-extrabold text-amber-400 text-sm flex items-center gap-1.5">
                <AlertTriangle className="w-4 h-4" />
                <span>🟡 AMARELO</span>
              </span>
              <button
                onClick={() => playAlertSound('yellow')}
                className="text-xs text-amber-400 hover:text-amber-200 flex items-center gap-1"
                title="Ouvir som do alerta amarelo"
              >
                <Volume2 className="w-3.5 h-3.5" />
                <span>Testar Som</span>
              </button>
            </div>
            <div className="text-xs font-semibold text-slate-200">Atende apenas um critério</div>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              Exemplo: Paga bom valor por km, mas o tempo é muito longo por trânsito pesado (R$/h baixo), ou vice-versa.
            </p>
          </div>

          <div className="p-4 rounded-2xl bg-rose-950/40 border border-rose-500/40 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-extrabold text-rose-400 text-sm flex items-center gap-1.5">
                <XCircle className="w-4 h-4" />
                <span>🔴 VERMELHO</span>
              </span>
              <button
                onClick={() => playAlertSound('red')}
                className="text-xs text-rose-400 hover:text-rose-200 flex items-center gap-1"
                title="Ouvir som do alerta vermelho"
              >
                <Volume2 className="w-3.5 h-3.5" />
                <span>Testar Som</span>
              </button>
            </div>
            <div className="text-xs font-semibold text-slate-200">Não atende nenhum critério</div>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              Ambos R$/km e R$/h estão abaixo das suas metas mínimas. Corrida gera prejuízo para o motorista.
            </p>
          </div>
        </div>
      </div>

      {/* 4. Custos Operacionais e Veículo */}
      <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 space-y-5">
        <div className="flex items-center gap-2">
          <Fuel className="w-5 h-5 text-amber-400" />
          <h3 className="text-base font-bold text-white">Custos do Veículo (Cálculo do Lucro Real)</h3>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Preço do Combustível (R$/L)</label>
            <input
              type="number"
              step="0.05"
              value={settings.fuelPricePerLiter}
              onChange={(e) => onUpdateSettings({ fuelPricePerLiter: parseFloat(e.target.value) || 0 })}
              className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-sm focus:border-emerald-500 outline-none"
            />
            <span className="text-[10px] text-slate-500">Gasolina / Etanol / GNV</span>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Consumo Médio (km/L)</label>
            <input
              type="number"
              step="0.5"
              value={settings.vehicleConsumptionKmPerLiter}
              onChange={(e) => onUpdateSettings({ vehicleConsumptionKmPerLiter: parseFloat(e.target.value) || 1 })}
              className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-sm focus:border-emerald-500 outline-none"
            />
            <span className="text-[10px] text-slate-500">Média cidade com ar-condicionado</span>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Custo Manutenção (R$/km)</label>
            <input
              type="number"
              step="0.05"
              value={settings.additionalCostPerKm}
              onChange={(e) => onUpdateSettings({ additionalCostPerKm: parseFloat(e.target.value) || 0 })}
              className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-sm focus:border-emerald-500 outline-none"
            />
            <span className="text-[10px] text-slate-500">Pneus, óleo, pastilhas, depreciação</span>
          </div>
        </div>
      </div>

      {/* 5. Aplicativos Alvo & Preferências */}
      <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 space-y-4">
        <h3 className="text-base font-bold text-white flex items-center gap-2">
          <Smartphone className="w-5 h-5 text-slate-400" />
          <span>Aplicativos Alvo & Comportamento</span>
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <label className="flex items-center gap-3 p-3 rounded-2xl bg-slate-950/60 border border-slate-800 cursor-pointer hover:border-slate-700 transition-colors">
            <input
              type="checkbox"
              checked={settings.targetApps.uber}
              onChange={(e) =>
                onUpdateSettings({
                  targetApps: { ...settings.targetApps, uber: e.target.checked },
                })
              }
              className="w-4 h-4 accent-emerald-500 rounded"
            />
            <div>
              <div className="text-xs font-bold text-white">Uber Driver</div>
              <div className="text-[10px] text-slate-500">com.ubercab.driver</div>
            </div>
          </label>

          <label className="flex items-center gap-3 p-3 rounded-2xl bg-slate-950/60 border border-slate-800 cursor-pointer hover:border-slate-700 transition-colors">
            <input
              type="checkbox"
              checked={settings.targetApps.ninetyNine}
              onChange={(e) =>
                onUpdateSettings({
                  targetApps: { ...settings.targetApps, ninetyNine: e.target.checked },
                })
              }
              className="w-4 h-4 accent-emerald-500 rounded"
            />
            <div>
              <div className="text-xs font-bold text-white">99 Motorista</div>
              <div className="text-[10px] text-slate-500">com.taxis99</div>
            </div>
          </label>

          <label className="flex items-center gap-3 p-3 rounded-2xl bg-slate-950/60 border border-slate-800 cursor-pointer hover:border-slate-700 transition-colors">
            <input
              type="checkbox"
              checked={settings.targetApps.inDrive}
              onChange={(e) =>
                onUpdateSettings({
                  targetApps: { ...settings.targetApps, inDrive: e.target.checked },
                })
              }
              className="w-4 h-4 accent-emerald-500 rounded"
            />
            <div>
              <div className="text-xs font-bold text-white">inDrive Motorista</div>
              <div className="text-[10px] text-slate-500">sinet.startup.inDriver</div>
            </div>
          </label>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
          <label className="flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800">
            <div className="space-y-0.5">
              <span className="text-xs font-bold text-white">Alertas Sonoros</span>
              <p className="text-[10px] text-slate-400">Tocar bip diferenciado para cada nível</p>
            </div>
            <input
              type="checkbox"
              checked={settings.soundAlertsEnabled}
              onChange={(e) => onUpdateSettings({ soundAlertsEnabled: e.target.checked })}
              className="w-4 h-4 accent-emerald-500 rounded"
            />
          </label>

          <div className="p-3 rounded-2xl bg-slate-950/60 border border-slate-800 flex items-center justify-between">
            <div className="space-y-0.5">
              <span className="text-xs font-bold text-white">Duração do Pop-up</span>
              <p className="text-[10px] text-slate-400">Tempo antes do fechamento automático</p>
            </div>
            <select
              value={settings.autoDismissSeconds}
              onChange={(e) => onUpdateSettings({ autoDismissSeconds: parseInt(e.target.value, 10) })}
              className="bg-slate-900 border border-slate-700 text-white text-xs rounded-xl px-2.5 py-1.5 outline-none"
            >
              <option value={8}>8 segundos</option>
              <option value={12}>12 segundos</option>
              <option value={15}>15 segundos</option>
              <option value={20}>20 segundos</option>
            </select>
          </div>
        </div>
      </div>
    </div>
  );
};
