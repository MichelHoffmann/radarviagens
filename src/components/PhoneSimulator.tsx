import React, { useState } from 'react';
import {
  Wifi,
  Battery,
  Signal,
  MapPin,
  Navigation,
  Clock,
  Sparkles,
  RefreshCw,
  Sliders,
  ShieldAlert,
  Play,
  Star
} from 'lucide-react';
import { RideData, RideEvaluation, DriverSettings } from '../types';
import { SAMPLE_RIDES } from '../data/sampleRides';
import { evaluateRide, formatCurrencyBRL } from '../utils/rideCalculator';
import { FloatingOverlayBadge } from './FloatingOverlayBadge';
import { playAlertSound } from '../utils/audioAlerts';

interface PhoneSimulatorProps {
  settings: DriverSettings;
  currentRide: RideData;
  onSelectRide: (ride: RideData) => void;
  onRideAction: (evaluation: RideEvaluation, action: 'accepted' | 'declined' | 'ignored') => void;
  onOpenSettings: () => void;
}

export const PhoneSimulator: React.FC<PhoneSimulatorProps> = ({
  settings,
  currentRide,
  onSelectRide,
  onRideAction,
  onOpenSettings,
}) => {
  const [overlayPos, setOverlayPos] = useState({ x: 14, y: 48 });
  const [isOverlayDismissed, setIsOverlayDismissed] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });

  const evaluation = evaluateRide(currentRide, settings);

  const handleSelectPreset = (ride: RideData) => {
    setIsOverlayDismissed(false);
    onSelectRide(ride);
    if (settings.isEnabled && settings.soundAlertsEnabled) {
      const evalItem = evaluateRide(ride, settings);
      playAlertSound(evalItem.verdict);
    }
  };

  const handleStartDrag = (e: React.MouseEvent | React.TouchEvent) => {
    setIsDragging(true);
    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX;
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY;
    setDragOffset({
      x: clientX - overlayPos.x,
      y: clientY - overlayPos.y,
    });
  };

  const handleDrag = (e: React.MouseEvent | React.TouchEvent) => {
    if (!isDragging) return;
    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX;
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY;

    // Restringe dentro das dimensões da tela do mockup (360px x 680px)
    const newX = Math.max(10, Math.min(60, clientX - dragOffset.x));
    const newY = Math.max(60, Math.min(380, clientY - dragOffset.y));
    setOverlayPos({ x: newX, y: newY });
  };

  const handleEndDrag = () => {
    setIsDragging(false);
  };

  return (
    <div className="w-full grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
      {/* Coluna da Esquerda: Smartphone Android Mockup */}
      <div className="lg:col-span-6 xl:col-span-5 flex flex-col items-center">
        {/* Frame do Smartphone */}
        <div
          className="relative w-[360px] sm:w-[380px] h-[740px] bg-slate-900 rounded-[48px] p-3 shadow-2xl border-4 border-slate-700/80 ring-1 ring-white/10"
          onMouseMove={handleDrag}
          onTouchMove={handleDrag}
          onMouseUp={handleEndDrag}
          onTouchEnd={handleEndDrag}
        >
          {/* Câmera Frontal (Punch-hole) */}
          <div className="absolute top-5 left-1/2 -translate-x-1/2 w-4 h-4 bg-black rounded-full z-40 border border-slate-800"></div>

          {/* Tela interna do celular */}
          <div className="relative w-full h-full bg-slate-950 rounded-[38px] overflow-hidden flex flex-col justify-between select-none">
            {/* 1. Android Status Bar */}
            <div className="h-9 px-6 pt-2 flex items-center justify-between text-xs text-slate-300 font-semibold z-30 bg-gradient-to-b from-black/80 to-transparent">
              <span className="tabular-nums font-mono text-[11px]">18:24</span>
              <div className="flex items-center gap-2">
                <Signal className="w-3.5 h-3.5 text-slate-300" />
                <Wifi className="w-3.5 h-3.5 text-slate-300" />
                <div className="flex items-center gap-1 text-[10px]">
                  <span>88%</span>
                  <Battery className="w-3.5 h-3.5 text-emerald-400" />
                </div>
              </div>
            </div>

            {/* 2. Tela Simulada do Aplicativo de Motorista (Uber Driver / 99) */}
            <div className="relative flex-1 flex flex-col justify-between overflow-hidden">
              {/* Mapa de Fundo Fictício Estilizado */}
              <div className="absolute inset-0 bg-slate-900 overflow-hidden pointer-events-none">
                <svg className="w-full h-full opacity-30" viewBox="0 0 400 700">
                  <path d="M-50,150 L450,220" stroke="#334155" strokeWidth="18" fill="none" />
                  <path d="M120,-50 L200,750" stroke="#334155" strokeWidth="22" fill="none" />
                  <path d="M300,-50 L100,750" stroke="#1e293b" strokeWidth="14" fill="none" />
                  <path d="M-50,450 L450,400" stroke="#334155" strokeWidth="16" fill="none" />
                  <circle cx="180" cy="320" r="14" fill="#10b981" fillOpacity="0.3" />
                  <circle cx="180" cy="320" r="7" fill="#10b981" />
                  {/* Rota traçada */}
                  <path
                    d="M180,320 Q240,400 210,500"
                    stroke="#3b82f6"
                    strokeWidth="4"
                    strokeDasharray="6,6"
                    fill="none"
                  />
                  <circle cx="210" cy="500" r="8" fill="#ef4444" />
                </svg>

                {/* Tag de topo com aplicativo simulado */}
                <div className="absolute top-3 left-4 right-4 flex items-center justify-between text-xs">
                  <div className="px-3 py-1 rounded-full bg-black/80 backdrop-blur-md border border-white/10 text-white font-bold flex items-center gap-1.5 shadow-lg">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                    <span>{currentRide.app === 'uber' ? 'Uber Driver' : currentRide.app === '99' ? '99 Motorista' : 'inDrive'}</span>
                  </div>
                  <div className="px-2.5 py-1 rounded-full bg-black/60 text-slate-300 text-[11px] font-medium backdrop-blur-md">
                    GPS Conectado
                  </div>
                </div>
              </div>

              {/* Pop-up Flutuante do Radar de Corridas sobre a tela */}
              {settings.isEnabled && !isOverlayDismissed && (
                <div
                  style={{
                    position: 'absolute',
                    top: `${overlayPos.y}px`,
                    left: `${overlayPos.x}px`,
                    zIndex: 45,
                  }}
                  onMouseDown={handleStartDrag}
                  onTouchStart={handleStartDrag}
                  className="cursor-move"
                >
                  <FloatingOverlayBadge
                    evaluation={evaluation}
                    settings={settings}
                    onClose={() => setIsOverlayDismissed(true)}
                    onAccept={() => onRideAction(evaluation, 'accepted')}
                    onDecline={() => onRideAction(evaluation, 'declined')}
                  />
                </div>
              )}

              {/* Alerta se o app estiver desativado */}
              {!settings.isEnabled && (
                <div className="absolute top-12 left-4 right-4 z-40 bg-slate-900/90 border border-red-500/50 p-3 rounded-2xl shadow-xl backdrop-blur-md text-center">
                  <div className="flex items-center justify-center gap-1.5 text-xs text-red-400 font-bold mb-1">
                    <ShieldAlert className="w-4 h-4" />
                    <span>Radar de Corridas Desativado</span>
                  </div>
                  <p className="text-[11px] text-slate-300">
                    O escaneamento automático está pausado pelo usuário.
                  </p>
                  <button
                    onClick={onOpenSettings}
                    className="mt-2 text-xs font-semibold px-3 py-1 bg-red-500/20 text-red-300 rounded-lg hover:bg-red-500/30"
                  >
                    Ativar no Painel
                  </button>
                </div>
              )}

              {/* Botão flutuante para restaurar o pop-up caso o usuário tenha fechado */}
              {settings.isEnabled && isOverlayDismissed && (
                <div className="absolute top-14 right-4 z-40">
                  <button
                    onClick={() => {
                      setIsOverlayDismissed(false);
                      if (settings.soundAlertsEnabled) playAlertSound(evaluation.verdict);
                    }}
                    className="p-2.5 rounded-full bg-emerald-600 hover:bg-emerald-500 text-white shadow-xl shadow-emerald-900/50 flex items-center gap-1.5 text-xs font-bold transition-all"
                    title="Reabrir Pop-up Flutuante"
                  >
                    <Sparkles className="w-4 h-4" />
                    <span>Ver Radar</span>
                  </button>
                </div>
              )}

              {/* Cartão de Chamada da Corrida (Fiel ao App da Uber / 99) */}
              <div className="mt-auto m-3 p-4 bg-slate-900/95 border border-slate-700/80 rounded-3xl shadow-2xl backdrop-blur-lg z-20 space-y-3">
                {/* Cabeçalho da corrida */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded-xl bg-slate-800 flex items-center justify-center font-bold text-white text-xs border border-white/10">
                      {currentRide.app === 'uber' ? 'UB' : '99'}
                    </div>
                    <div>
                      <div className="text-xs font-bold text-white flex items-center gap-1.5">
                        <span>{currentRide.category}</span>
                        {currentRide.passengerRating && (
                          <span className="text-[11px] text-amber-400 font-normal flex items-center">
                            ★ {currentRide.passengerRating}
                          </span>
                        )}
                      </div>
                      <div className="text-[10px] text-slate-400">Oferta de viagem recebida</div>
                    </div>
                  </div>

                  {/* Valor Total na tela do aplicativo */}
                  <div className="text-right">
                    <div className="text-xl font-black text-white tabular-nums">
                      {formatCurrencyBRL(currentRide.price)}
                    </div>
                  </div>
                </div>

                {/* Métricas brutas da corrida conforme aparecem na Uber/99 */}
                <div className="grid grid-cols-2 gap-2 text-xs bg-slate-950/60 p-2.5 rounded-2xl border border-white/5">
                  <div className="space-y-0.5">
                    <div className="text-[10px] text-slate-400 uppercase font-semibold">Distância Total</div>
                    <div className="font-bold text-slate-100 flex items-center gap-1">
                      <Navigation className="w-3 h-3 text-slate-400" />
                      <span>{currentRide.totalDistanceKm} km</span>
                    </div>
                    <div className="text-[10px] text-slate-500">
                      ({currentRide.pickupDistanceKm}km busca + {currentRide.tripDistanceKm}km viagem)
                    </div>
                  </div>

                  <div className="space-y-0.5">
                    <div className="text-[10px] text-slate-400 uppercase font-semibold">Tempo Estimado</div>
                    <div className="font-bold text-slate-100 flex items-center gap-1">
                      <Clock className="w-3 h-3 text-slate-400" />
                      <span>{currentRide.totalDurationMin} minutos</span>
                    </div>
                    <div className="text-[10px] text-slate-500">
                      ({currentRide.pickupDurationMin}m até local + {currentRide.tripDurationMin}m trajeto)
                    </div>
                  </div>
                </div>

                {/* Endereços da chamada */}
                <div className="space-y-1.5 text-[11px] text-slate-300">
                  <div className="flex items-center gap-1.5 truncate">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 shrink-0"></span>
                    <span className="truncate">{currentRide.pickupAddress}</span>
                  </div>
                  <div className="flex items-center gap-1.5 truncate">
                    <span className="w-2 h-2 rounded-full bg-rose-400 shrink-0"></span>
                    <span className="truncate">{currentRide.destinationAddress}</span>
                  </div>
                </div>

                {/* Botão de Aceitar da Uber/99 */}
                <div className="pt-1 flex gap-2">
                  <button
                    onClick={() => onRideAction(evaluation, 'declined')}
                    className="w-1/3 py-2.5 rounded-xl bg-slate-800 text-slate-300 font-semibold text-xs hover:bg-slate-700 transition-colors"
                  >
                    Recusar
                  </button>
                  <button
                    onClick={() => onRideAction(evaluation, 'accepted')}
                    className="w-2/3 py-2.5 rounded-xl bg-emerald-500 text-emerald-950 font-bold text-xs hover:bg-emerald-400 transition-colors shadow-lg shadow-emerald-950 flex items-center justify-center gap-1.5"
                  >
                    <span>Aceitar Corrida</span>
                  </button>
                </div>
              </div>
            </div>

            {/* 3. Android Home Gesture Bar */}
            <div className="h-6 flex items-center justify-center">
              <div className="w-32 h-1 bg-slate-600 rounded-full"></div>
            </div>
          </div>
        </div>

        <div className="mt-3 text-xs text-slate-400 text-center flex items-center gap-1.5">
          <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
          <span>Arraste o balão flutuante na tela para reposicioná-lo</span>
        </div>
      </div>

      {/* Coluna da Direita: Seleção de Chamadas e Explicação dos 3 Níveis */}
      <div className="lg:col-span-6 xl:col-span-7 space-y-6">
        {/* Banner Explicativo dos Critérios Solicitados */}
        <div className="bg-slate-900 border border-slate-800 p-5 rounded-3xl space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              <Play className="w-4 h-4 text-emerald-400" />
              <span>Simulador de Chamadas de Aplicativo</span>
            </h2>
            <div className="text-xs text-slate-400">
              Metas: <span className="text-emerald-400 font-semibold">R$ {settings.minPricePerKm.toFixed(2)}/km</span> · <span className="text-teal-400 font-semibold">R$ {settings.minPricePerHour.toFixed(2)}/h</span>
            </div>
          </div>

          <p className="text-xs text-slate-300 leading-relaxed">
            Selecione uma das corridas reais abaixo para testar instantaneamente a reação do pop-up
            flutuante. O sistema analisa se a corrida atende aos seus critérios de <strong>R$/km</strong> e <strong>R$/hora</strong>:
          </p>

          {/* Os 3 Níveis Exatos Pedidos pelo Usuário */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="p-3 rounded-2xl bg-emerald-950/40 border border-emerald-500/40">
              <div className="flex items-center gap-1.5 text-xs font-bold text-emerald-400 mb-1">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
                <span>NÍVEL VERDE</span>
              </div>
              <div className="text-[11px] text-slate-300 font-medium">Atende todos os requisitos</div>
              <div className="text-[10px] text-slate-400 mt-1">
                R$/km &ge; meta <strong className="text-emerald-400">E</strong> R$/h &ge; meta
              </div>
            </div>

            <div className="p-3 rounded-2xl bg-amber-950/40 border border-amber-500/40">
              <div className="flex items-center gap-1.5 text-xs font-bold text-amber-400 mb-1">
                <span className="w-2.5 h-2.5 rounded-full bg-amber-400"></span>
                <span>NÍVEL AMARELO</span>
              </div>
              <div className="text-[11px] text-slate-300 font-medium">Atende apenas um critério</div>
              <div className="text-[10px] text-slate-400 mt-1">
                Bom R$/km com trânsito lento <strong className="text-amber-400">OU</strong> bom R$/h com muitos km
              </div>
            </div>

            <div className="p-3 rounded-2xl bg-rose-950/40 border border-rose-500/40">
              <div className="flex items-center gap-1.5 text-xs font-bold text-rose-400 mb-1">
                <span className="w-2.5 h-2.5 rounded-full bg-rose-400"></span>
                <span>NÍVEL VERMELHO</span>
              </div>
              <div className="text-[11px] text-slate-300 font-medium">Não atende nenhum critério</div>
              <div className="text-[10px] text-slate-400 mt-1">
                R$/km abaixo <strong className="text-rose-400">E</strong> R$/h abaixo da meta (prejuízo)
              </div>
            </div>
          </div>
        </div>

        {/* Lista de Chamadas Prontas para Testar */}
        <div className="bg-slate-900 border border-slate-800 p-5 rounded-3xl space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-white flex items-center gap-2">
              <RefreshCw className="w-4 h-4 text-slate-400" />
              <span>Cenários do Dia a Dia para Simulação</span>
            </h3>
            <span className="text-xs text-slate-500">6 chamadas disponíveis</span>
          </div>

          <div className="space-y-2.5">
            {SAMPLE_RIDES.map((ride) => {
              const evalItem = evaluateRide(ride, settings);
              const isSelected = currentRide.id === ride.id;

              const badgeColor = {
                green: 'border-emerald-500/50 bg-emerald-500/10 text-emerald-400',
                yellow: 'border-amber-500/50 bg-amber-500/10 text-amber-400',
                red: 'border-rose-500/50 bg-rose-500/10 text-rose-400',
              }[evalItem.verdict];

              const badgeLabel = {
                green: '🟢 Verde',
                yellow: '🟡 Amarelo',
                red: '🔴 Vermelho',
              }[evalItem.verdict];

              return (
                <button
                  key={ride.id}
                  onClick={() => handleSelectPreset(ride)}
                  className={`w-full p-3.5 rounded-2xl text-left transition-all border flex items-center justify-between gap-3 ${
                    isSelected
                      ? 'bg-slate-800/90 border-emerald-500 shadow-md ring-1 ring-emerald-500/50'
                      : 'bg-slate-950/60 border-slate-800 hover:bg-slate-800/40 hover:border-slate-700'
                  }`}
                >
                  <div className="space-y-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-white">{ride.category}</span>
                      <span className="text-xs text-slate-400">·</span>
                      <span className="text-xs font-black text-emerald-400 tabular-nums">
                        {formatCurrencyBRL(ride.price)}
                      </span>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md border ${badgeColor}`}>
                        {badgeLabel}
                      </span>
                    </div>
                    <div className="text-[11px] text-slate-400 truncate">
                      {ride.pickupAddress} ➔ {ride.destinationAddress}
                    </div>
                  </div>

                  <div className="text-right shrink-0">
                    <div className="text-xs font-bold text-slate-200 tabular-nums">
                      R$ {evalItem.pricePerKm.toFixed(2)}/km
                    </div>
                    <div className="text-[11px] text-slate-400 tabular-nums">
                      R$ {evalItem.pricePerHour.toFixed(2)}/h
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Resumo da Análise Atual */}
        <div className="bg-slate-900 border border-slate-800 p-5 rounded-3xl space-y-3">
          <h3 className="text-sm font-bold text-white">Análise Detalhada da Corrida Selecionada</h3>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
            <div className="p-3 bg-slate-950/70 rounded-2xl border border-white/5">
              <div className="text-[10px] text-slate-400 uppercase font-semibold">Valor por KM</div>
              <div className="text-base font-extrabold text-white mt-0.5">
                R$ {evaluation.pricePerKm.toFixed(2)}
              </div>
              <div className={`text-[10px] font-bold ${evaluation.meetsKmRequirement ? 'text-emerald-400' : 'text-rose-400'}`}>
                {evaluation.meetsKmRequirement ? 'Meta batida' : 'Abaixo da meta'}
              </div>
            </div>

            <div className="p-3 bg-slate-950/70 rounded-2xl border border-white/5">
              <div className="text-[10px] text-slate-400 uppercase font-semibold">Valor por Hora</div>
              <div className="text-base font-extrabold text-white mt-0.5">
                R$ {evaluation.pricePerHour.toFixed(2)}
              </div>
              <div className={`text-[10px] font-bold ${evaluation.meetsHourRequirement ? 'text-emerald-400' : 'text-rose-400'}`}>
                {evaluation.meetsHourRequirement ? 'Meta batida' : 'Abaixo da meta'}
              </div>
            </div>

            <div className="p-3 bg-slate-950/70 rounded-2xl border border-white/5">
              <div className="text-[10px] text-slate-400 uppercase font-semibold">Gasto Combustível</div>
              <div className="text-base font-extrabold text-rose-300 mt-0.5">
                {formatCurrencyBRL(evaluation.fuelCost)}
              </div>
              <div className="text-[10px] text-slate-400">
                {settings.vehicleConsumptionKmPerLiter} km/l
              </div>
            </div>

            <div className="p-3 bg-slate-950/70 rounded-2xl border border-white/5">
              <div className="text-[10px] text-slate-400 uppercase font-semibold">Lucro Líquido Real</div>
              <div className="text-base font-extrabold text-emerald-400 mt-0.5">
                {formatCurrencyBRL(evaluation.netProfit)}
              </div>
              <div className="text-[10px] text-slate-400">
                R$ {evaluation.netPerHour.toFixed(2)}/h líq.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
