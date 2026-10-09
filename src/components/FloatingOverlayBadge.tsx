import React, { useState } from 'react';
import {
  X,
  ChevronDown,
  ChevronUp,
  Fuel,
  TrendingUp,
  Clock,
  Navigation,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Move,
  Volume2
} from 'lucide-react';
import { RideEvaluation, DriverSettings } from '../types';
import { formatCurrencyBRL } from '../utils/rideCalculator';
import { playAlertSound } from '../utils/audioAlerts';

interface FloatingOverlayBadgeProps {
  evaluation: RideEvaluation;
  settings: DriverSettings;
  onClose: () => void;
  onAccept?: () => void;
  onDecline?: () => void;
  isDraggable?: boolean;
}

export const FloatingOverlayBadge: React.FC<FloatingOverlayBadgeProps> = ({
  evaluation,
  settings,
  onClose,
  onAccept,
  onDecline,
}) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const { ride, pricePerKm, pricePerHour, netProfit, verdict, verdictReason, meetsKmRequirement, meetsHourRequirement } = evaluation;

  // Cores conforme o nível exato solicitado pelo usuário
  const verdictConfig = {
    green: {
      bg: 'bg-emerald-950/95 border-emerald-500/80',
      badgeBg: 'bg-emerald-500 text-emerald-950',
      badgeText: '🟢 VALE A PENA (2/2)',
      title: 'Corrida Excelente',
      textColor: 'text-emerald-400',
      icon: CheckCircle2,
      glow: 'shadow-[0_10px_35px_-5px_rgba(16,185,129,0.35)]',
    },
    yellow: {
      bg: 'bg-amber-950/95 border-amber-500/80',
      badgeBg: 'bg-amber-500 text-amber-950',
      badgeText: '🟡 ATENÇÃO (1/2)',
      title: 'Atende Apenas 1 Critério',
      textColor: 'text-amber-400',
      icon: AlertTriangle,
      glow: 'shadow-[0_10px_35px_-5px_rgba(245,158,11,0.35)]',
    },
    red: {
      bg: 'bg-rose-950/95 border-rose-500/80',
      badgeBg: 'bg-rose-600 text-white',
      badgeText: '🔴 RECUSAR (0/2)',
      title: 'Não Vale a Pena',
      textColor: 'text-rose-400',
      icon: XCircle,
      glow: 'shadow-[0_10px_35px_-5px_rgba(239,68,68,0.35)]',
    },
  }[verdict];

  const VerdictIcon = verdictConfig.icon;

  return (
    <div
      className={`w-full max-w-[340px] rounded-2xl border-2 backdrop-blur-xl ${verdictConfig.bg} ${verdictConfig.glow} transition-all duration-200 select-none overflow-hidden shadow-2xl`}
    >
      {/* Barra de Status com o Nível de Cor */}
      <div className={`px-3 py-2 flex items-center justify-between font-bold text-xs ${verdictConfig.badgeBg}`}>
        <div className="flex items-center gap-1.5 font-extrabold tracking-wide">
          <VerdictIcon className="w-4 h-4 shrink-0" />
          <span>{verdictConfig.badgeText}</span>
        </div>
        <div className="flex items-center gap-2">
          {settings.soundAlertsEnabled && (
            <button
              onClick={() => playAlertSound(verdict)}
              title="Tocar som do alerta"
              className="p-1 hover:opacity-75 transition-opacity"
            >
              <Volume2 className="w-3.5 h-3.5" />
            </button>
          )}
          <button
            onClick={onClose}
            className="p-1 rounded-md hover:bg-black/20 transition-colors"
            title="Fechar Pop-up"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Conteúdo Principal do Pop-up: apenas R$/km e R$/hora com as cores */}
      <div className="p-3 space-y-2">
        {/* Indicadores Principais Solicitados: R$/km e R$/hora */}
        <div className="grid grid-cols-2 gap-2">
          {/* Card R$/km */}
          <div
            className={`p-2.5 rounded-xl border ${
              meetsKmRequirement
                ? 'bg-emerald-950/60 border-emerald-500/50'
                : 'bg-rose-950/50 border-rose-500/50'
            }`}
          >
            <div className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">
              R$ / KM
            </div>
            <div className={`text-xl font-black tabular-nums mt-0.5 ${meetsKmRequirement ? 'text-emerald-400' : 'text-rose-400'}`}>
              R$ {pricePerKm.toFixed(2)}
              <span className="text-xs font-normal text-slate-400">/km</span>
            </div>
          </div>

          {/* Card R$/hora */}
          <div
            className={`p-2.5 rounded-xl border ${
              meetsHourRequirement
                ? 'bg-emerald-950/60 border-emerald-500/50'
                : 'bg-rose-950/50 border-rose-500/50'
            }`}
          >
            <div className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">
              R$ / HORA
            </div>
            <div className={`text-xl font-black tabular-nums mt-0.5 ${meetsHourRequirement ? 'text-emerald-400' : 'text-rose-400'}`}>
              R$ {pricePerHour.toFixed(2)}
              <span className="text-xs font-normal text-slate-400">/h</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
