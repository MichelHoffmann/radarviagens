import React, { useState } from 'react';
import { Calculator, Play, Sparkles, Navigation, Clock, DollarSign } from 'lucide-react';
import { DriverSettings, RideData } from '../types';
import { evaluateRide, formatCurrencyBRL } from '../utils/rideCalculator';

interface ManualRideTesterProps {
  settings: DriverSettings;
  onSendToPhone: (ride: RideData) => void;
}

export const ManualRideTester: React.FC<ManualRideTesterProps> = ({
  settings,
  onSendToPhone,
}) => {
  const [app, setApp] = useState<'uber' | '99' | 'indrive'>('uber');
  const [price, setPrice] = useState<number>(32.0);
  const [totalDistanceKm, setTotalDistanceKm] = useState<number>(11.5);
  const [totalDurationMin, setTotalDurationMin] = useState<number>(24);

  const testRide: RideData = {
    id: `custom-${Date.now()}`,
    app,
    category: app === 'uber' ? 'UberX' : app === '99' ? '99Pop' : 'inDrive',
    price,
    totalDistanceKm,
    pickupDistanceKm: Number((totalDistanceKm * 0.2).toFixed(1)),
    tripDistanceKm: Number((totalDistanceKm * 0.8).toFixed(1)),
    totalDurationMin,
    pickupDurationMin: Math.max(2, Math.round(totalDurationMin * 0.2)),
    tripDurationMin: Math.max(1, Math.round(totalDurationMin * 0.8)),
    pickupAddress: 'Ponto de Partida Personalizado',
    destinationAddress: 'Destino da Viagem Personalizado',
    timestamp: 'Teste Manual',
  };

  const evaluation = evaluateRide(testRide, settings);

  const verdictBadge = {
    green: {
      bg: 'bg-emerald-950/60 border-emerald-500/50 text-emerald-400',
      label: '🟢 NÍVEL VERDE: VALE A PENA',
      desc: 'Atende ambos os requisitos: R$/km e R$/hora estão acima da meta.',
    },
    yellow: {
      bg: 'bg-amber-950/60 border-amber-500/50 text-amber-400',
      label: '🟡 NÍVEL AMARELO: ATENÇÃO',
      desc: 'Atende apenas um dos requisitos configurados.',
    },
    red: {
      bg: 'bg-rose-950/60 border-rose-500/50 text-rose-400',
      label: '🔴 NÍVEL VERMELHO: RECUSAR',
      desc: 'Não atende nenhum critério definido (gera prejuízo).',
    },
  }[evaluation.verdict];

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div className="flex items-center gap-2.5">
          <Calculator className="w-5 h-5 text-emerald-400" />
          <h2 className="text-base font-bold text-white">Calculadora Rápida & Simulador Livre</h2>
        </div>
        <div className="text-xs text-slate-400">
          Metas ativas: <span className="text-emerald-400 font-bold">R$ {settings.minPricePerKm.toFixed(2)}/km</span> e{' '}
          <span className="text-teal-400 font-bold">R$ {settings.minPricePerHour.toFixed(2)}/h</span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {/* Controle 1: Preço */}
        <div className="space-y-2 bg-slate-950/50 p-4 rounded-2xl border border-white/5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
              <DollarSign className="w-3.5 h-3.5 text-emerald-400" />
              Valor Total (R$)
            </span>
            <span className="text-base font-black text-white tabular-nums">
              {formatCurrencyBRL(price)}
            </span>
          </div>
          <input
            type="range"
            min="6.00"
            max="120.00"
            step="0.50"
            value={price}
            onChange={(e) => setPrice(parseFloat(e.target.value))}
            className="w-full accent-emerald-500 h-2 bg-slate-800 rounded-lg cursor-pointer"
          />
          <div className="flex justify-between text-[10px] text-slate-500">
            <span>R$ 6,00</span>
            <span>R$ 50,00</span>
            <span>R$ 120,00</span>
          </div>
        </div>

        {/* Controle 2: Distância Total */}
        <div className="space-y-2 bg-slate-950/50 p-4 rounded-2xl border border-white/5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
              <Navigation className="w-3.5 h-3.5 text-teal-400" />
              Distância Total (km)
            </span>
            <span className="text-base font-black text-white tabular-nums">
              {totalDistanceKm.toFixed(1)} km
            </span>
          </div>
          <input
            type="range"
            min="1.0"
            max="45.0"
            step="0.5"
            value={totalDistanceKm}
            onChange={(e) => setTotalDistanceKm(parseFloat(e.target.value))}
            className="w-full accent-teal-500 h-2 bg-slate-800 rounded-lg cursor-pointer"
          />
          <div className="flex justify-between text-[10px] text-slate-500">
            <span>1 km</span>
            <span>20 km</span>
            <span>45 km</span>
          </div>
        </div>

        {/* Controle 3: Tempo Total */}
        <div className="space-y-2 bg-slate-950/50 p-4 rounded-2xl border border-white/5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
              <Clock className="w-3.5 h-3.5 text-amber-400" />
              Duração Estimada (min)
            </span>
            <span className="text-base font-black text-white tabular-nums">
              {totalDurationMin} min
            </span>
          </div>
          <input
            type="range"
            min="4"
            max="90"
            step="1"
            value={totalDurationMin}
            onChange={(e) => setTotalDurationMin(parseInt(e.target.value, 10))}
            className="w-full accent-amber-500 h-2 bg-slate-800 rounded-lg cursor-pointer"
          />
          <div className="flex justify-between text-[10px] text-slate-500">
            <span>4 min</span>
            <span>45 min</span>
            <span>90 min</span>
          </div>
        </div>
      </div>

      {/* Resultado Instantâneo com os 3 Níveis */}
      <div className={`p-4 rounded-2xl border ${verdictBadge.bg} flex flex-col sm:flex-row sm:items-center justify-between gap-4`}>
        <div className="space-y-1">
          <div className="text-sm font-extrabold tracking-wide">{verdictBadge.label}</div>
          <p className="text-xs text-slate-300">{verdictBadge.desc}</p>
          <div className="flex items-center gap-4 text-xs font-semibold pt-1">
            <span className={evaluation.meetsKmRequirement ? 'text-emerald-400' : 'text-rose-400'}>
              R$/km: R$ {evaluation.pricePerKm.toFixed(2)} {evaluation.meetsKmRequirement ? '(✓)' : '(✗)'}
            </span>
            <span className={evaluation.meetsHourRequirement ? 'text-emerald-400' : 'text-rose-400'}>
              R$/hora: R$ {evaluation.pricePerHour.toFixed(2)} {evaluation.meetsHourRequirement ? '(✓)' : '(✗)'}
            </span>
          </div>
        </div>

        <button
          onClick={() => onSendToPhone(testRide)}
          className="px-4 py-2.5 rounded-xl bg-white text-slate-950 font-bold text-xs hover:bg-slate-200 transition-colors shadow-lg flex items-center justify-center gap-2 shrink-0"
        >
          <Play className="w-4 h-4 fill-current" />
          <span>Ver no Smartphone</span>
        </button>
      </div>
    </div>
  );
};
