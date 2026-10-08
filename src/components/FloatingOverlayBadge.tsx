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

      {/* Conteúdo Principal do Pop-up */}
      <div className="p-3.5 space-y-3">
        {/* Cabeçalho com App e Valor Total */}
        <div className="flex items-start justify-between">
          <div>
            <div className="text-[11px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <span className={`w-2 h-2 rounded-full ${ride.app === 'uber' ? 'bg-black border border-white/40' : 'bg-amber-400'}`}></span>
              <span>{ride.category}</span>
              <span className="text-slate-500">·</span>
              <span className="text-slate-300">{ride.timestamp}</span>
            </div>
            <div className="text-2xl font-black text-white tabular-nums tracking-tight mt-0.5">
              {formatCurrencyBRL(ride.price)}
            </div>
          </div>

          <div className="text-right">
            <div className="text-xs text-slate-300 font-semibold flex items-center gap-1 justify-end">
              <Navigation className="w-3.5 h-3.5 text-slate-400" />
              <span>{ride.totalDistanceKm} km</span>
            </div>
            <div className="text-xs text-slate-400 flex items-center gap-1 justify-end mt-0.5">
              <Clock className="w-3.5 h-3.5 text-slate-500" />
              <span>{ride.totalDurationMin} min</span>
            </div>
          </div>
        </div>

        {/* Indicadores Principais Solicitados: R$/km e R$/hora */}
        <div className="grid grid-cols-2 gap-2">
          {/* Card R$/km */}
          <div
            className={`p-2.5 rounded-xl border ${
              meetsKmRequirement
                ? 'bg-emerald-950/50 border-emerald-500/40'
                : 'bg-rose-950/40 border-rose-500/40'
            }`}
          >
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-slate-400 font-medium">R$ por KM</span>
              <span className={`font-bold ${meetsKmRequirement ? 'text-emerald-400' : 'text-rose-400'}`}>
                {meetsKmRequirement ? '✓ Atende' : '✗ Abaixo'}
              </span>
            </div>
            <div className="text-lg font-extrabold text-white tabular-nums mt-0.5">
              R$ {pricePerKm.toFixed(2)}
              <span className="text-xs font-normal text-slate-400">/km</span>
            </div>
            <div className="text-[10px] text-slate-400 mt-0.5">
              Meta: R$ {settings.minPricePerKm.toFixed(2)}
            </div>
          </div>

          {/* Card R$/hora */}
          <div
            className={`p-2.5 rounded-xl border ${
              meetsHourRequirement
                ? 'bg-emerald-950/50 border-emerald-500/40'
                : 'bg-rose-950/40 border-rose-500/40'
            }`}
          >
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-slate-400 font-medium">R$ por Hora</span>
              <span className={`font-bold ${meetsHourRequirement ? 'text-emerald-400' : 'text-rose-400'}`}>
                {meetsHourRequirement ? '✓ Atende' : '✗ Abaixo'}
              </span>
            </div>
            <div className="text-lg font-extrabold text-white tabular-nums mt-0.5">
              R$ {pricePerHour.toFixed(2)}
              <span className="text-xs font-normal text-slate-400">/h</span>
            </div>
            <div className="text-[10px] text-slate-400 mt-0.5">
              Meta: R$ {settings.minPricePerHour.toFixed(2)}
            </div>
          </div>
        </div>

        {/* Motivo do Veredito */}
        <div className="text-xs text-slate-300 leading-relaxed bg-black/30 p-2.5 rounded-xl border border-white/5">
          <p className="font-medium text-[11px] text-slate-200">{verdictReason}</p>
        </div>

        {/* Detalhes Expansíveis de Lucro Líquido e Combustível */}
        {isExpanded && (
          <div className="space-y-2 pt-1 border-t border-white/10 text-xs">
            <div className="flex items-center justify-between text-slate-300">
              <span className="flex items-center gap-1.5 text-slate-400">
                <Fuel className="w-3.5 h-3.5 text-amber-400" />
                Combustível est. ({settings.vehicleConsumptionKmPerLiter} km/l)
              </span>
              <span className="font-semibold text-rose-300 tabular-nums">
                - {formatCurrencyBRL(evaluation.fuelCost)}
              </span>
            </div>

            <div className="flex items-center justify-between text-slate-300">
              <span className="flex items-center gap-1.5 text-slate-400">
                <TrendingUp className="w-3.5 h-3.5 text-emerald-400" />
                Lucro Líquido Real
              </span>
              <span className="font-bold text-emerald-400 tabular-nums">
                {formatCurrencyBRL(netProfit)}
              </span>
            </div>

            <div className="flex items-center justify-between text-slate-400 text-[11px]">
              <span>Busca até cliente: {ride.pickupDistanceKm} km ({ride.pickupDurationMin} min)</span>
              <span>Viagem: {ride.tripDistanceKm} km ({ride.tripDurationMin} min)</span>
            </div>
          </div>
        )}

        {/* Barra Inferior com Controles */}
        <div className="pt-2 flex items-center justify-between gap-2 border-t border-white/10">
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="text-[11px] text-slate-400 hover:text-slate-200 flex items-center gap-1 py-1"
          >
            {isExpanded ? (
              <>
                <ChevronUp className="w-3.5 h-3.5" />
                Menos dados
              </>
            ) : (
              <>
                <ChevronDown className="w-3.5 h-3.5" />
                Ver custos & lucro
              </>
            )}
          </button>

          <div className="flex items-center gap-1.5">
            {onDecline && (
              <button
                onClick={onDecline}
                className="px-2.5 py-1 text-xs font-semibold rounded-lg bg-rose-500/20 text-rose-300 border border-rose-500/30 hover:bg-rose-500/30 transition-colors"
              >
                Recusar
              </button>
            )}
            {onAccept && (
              <button
                onClick={onAccept}
                className="px-3 py-1 text-xs font-semibold rounded-lg bg-emerald-500 text-emerald-950 hover:bg-emerald-400 transition-colors shadow-md"
              >
                Aceitar
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
