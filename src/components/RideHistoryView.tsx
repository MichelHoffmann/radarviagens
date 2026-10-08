import React, { useState } from 'react';
import {
  History,
  TrendingUp,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Trash2,
  Filter,
  DollarSign,
  Fuel
} from 'lucide-react';
import { ScanHistoryItem } from '../types';
import { formatCurrencyBRL } from '../utils/rideCalculator';

interface RideHistoryViewProps {
  history: ScanHistoryItem[];
  onClearHistory: () => void;
}

export const RideHistoryView: React.FC<RideHistoryViewProps> = ({
  history,
  onClearHistory,
}) => {
  const [filterVerdict, setFilterVerdict] = useState<'all' | 'green' | 'yellow' | 'red'>('all');

  const filteredHistory = history.filter((item) => {
    if (filterVerdict === 'all') return true;
    return item.verdict === filterVerdict;
  });

  const greenCount = history.filter((h) => h.verdict === 'green').length;
  const yellowCount = history.filter((h) => h.verdict === 'yellow').length;
  const redCount = history.filter((h) => h.verdict === 'red').length;

  const totalAcceptedGross = history
    .filter((h) => h.actionTaken === 'accepted')
    .reduce((acc, h) => acc + h.ride.price, 0);

  const totalAcceptedNet = history
    .filter((h) => h.actionTaken === 'accepted')
    .reduce((acc, h) => acc + h.netProfit, 0);

  const totalLossAvoided = history
    .filter((h) => h.verdict === 'red')
    .reduce((acc, h) => acc + h.fuelCost, 0);

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Cabeçalho */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-800">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-white flex items-center gap-2.5">
            <History className="w-6 h-6 text-emerald-400" />
            <span>Histórico de Corridas Analisadas</span>
          </h1>
          <p className="text-xs sm:text-sm text-slate-400 mt-1">
            Registro de todas as chamadas detectadas pelo Radar e resultados dos cálculos.
          </p>
        </div>

        {history.length > 0 && (
          <button
            onClick={onClearHistory}
            className="px-3.5 py-2 text-xs font-semibold rounded-xl text-rose-400 hover:text-rose-200 hover:bg-rose-950/40 border border-rose-900/60 transition-colors flex items-center gap-1.5 self-start sm:self-auto"
          >
            <Trash2 className="w-4 h-4" />
            <span>Limpar Histórico</span>
          </button>
        )}
      </div>

      {/* Cards de Métricas e Impacto Financeiro */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-slate-900 border border-slate-800 p-4 rounded-2xl">
          <div className="text-[11px] text-slate-400 uppercase font-semibold">Total Analisadas</div>
          <div className="text-2xl font-black text-white mt-1 tabular-nums">{history.length}</div>
          <div className="text-[11px] text-slate-500 mt-1">Corridas capturadas</div>
        </div>

        <div className="bg-slate-900 border border-slate-800 p-4 rounded-2xl">
          <div className="text-[11px] text-slate-400 uppercase font-semibold flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
            <span>Verdes (Aprovadas)</span>
          </div>
          <div className="text-2xl font-black text-emerald-400 mt-1 tabular-nums">{greenCount}</div>
          <div className="text-[11px] text-slate-500 mt-1">100% dos critérios</div>
        </div>

        <div className="bg-slate-900 border border-slate-800 p-4 rounded-2xl">
          <div className="text-[11px] text-slate-400 uppercase font-semibold flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-rose-400"></span>
            <span>Vermelhas (Prejuízo)</span>
          </div>
          <div className="text-2xl font-black text-rose-400 mt-1 tabular-nums">{redCount}</div>
          <div className="text-[11px] text-slate-500 mt-1">Nenhum critério atendido</div>
        </div>

        <div className="bg-slate-900 border border-slate-800 p-4 rounded-2xl">
          <div className="text-[11px] text-slate-400 uppercase font-semibold flex items-center gap-1">
            <TrendingUp className="w-3.5 h-3.5 text-teal-400" />
            <span>Lucro Líquido Aceito</span>
          </div>
          <div className="text-2xl font-black text-teal-400 mt-1 tabular-nums">
            {formatCurrencyBRL(totalAcceptedNet)}
          </div>
          <div className="text-[11px] text-slate-500 mt-1">Das corridas aceitas</div>
        </div>
      </div>

      {/* Filtros por Nível (Verde / Amarelo / Vermelho) */}
      <div className="flex items-center gap-2">
        <span className="text-xs text-slate-400 flex items-center gap-1.5 mr-2">
          <Filter className="w-3.5 h-3.5" />
          <span>Filtrar:</span>
        </span>
        <button
          onClick={() => setFilterVerdict('all')}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors ${
            filterVerdict === 'all'
              ? 'bg-slate-800 text-white'
              : 'text-slate-400 hover:text-white bg-slate-950/60'
          }`}
        >
          Todas ({history.length})
        </button>
        <button
          onClick={() => setFilterVerdict('green')}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors ${
            filterVerdict === 'green'
              ? 'bg-emerald-950 text-emerald-400 border border-emerald-500/50'
              : 'text-slate-400 hover:text-white bg-slate-950/60'
          }`}
        >
          🟢 Verdes ({greenCount})
        </button>
        <button
          onClick={() => setFilterVerdict('yellow')}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors ${
            filterVerdict === 'yellow'
              ? 'bg-amber-950 text-amber-400 border border-amber-500/50'
              : 'text-slate-400 hover:text-white bg-slate-950/60'
          }`}
        >
          🟡 Amarelas ({yellowCount})
        </button>
        <button
          onClick={() => setFilterVerdict('red')}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors ${
            filterVerdict === 'red'
              ? 'bg-rose-950 text-rose-400 border border-rose-500/50'
              : 'text-slate-400 hover:text-white bg-slate-950/60'
          }`}
        >
          🔴 Vermelhas ({redCount})
        </button>
      </div>

      {/* Lista das Corridas */}
      <div className="space-y-3">
        {filteredHistory.length === 0 ? (
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-12 text-center text-slate-400 text-sm">
            Nenhuma corrida registrada neste filtro. Teste ou escaneie chamadas para visualizá-las aqui!
          </div>
        ) : (
          filteredHistory.map((item, index) => {
            const badge = {
              green: {
                label: '🟢 Verde: Vale a Pena',
                color: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/40',
              },
              yellow: {
                label: '🟡 Amarelo: Atenção (1 critério)',
                color: 'bg-amber-500/10 text-amber-400 border-amber-500/40',
              },
              red: {
                label: '🔴 Vermelho: Recusar',
                color: 'bg-rose-500/10 text-rose-400 border-rose-500/40',
              },
            }[item.verdict];

            return (
              <div
                key={index}
                className="bg-slate-900 border border-slate-800 rounded-2xl p-4 flex flex-col md:flex-row md:items-center justify-between gap-4 hover:border-slate-700 transition-colors"
              >
                <div className="space-y-1.5">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-xs font-bold text-white uppercase">{item.ride.category}</span>
                    <span className="text-slate-500">·</span>
                    <span className="text-xs font-bold text-slate-300">{item.evaluatedAt}</span>
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md border ${badge.color}`}>
                      {badge.label}
                    </span>
                    {item.actionTaken && (
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded-md ${
                          item.actionTaken === 'accepted'
                            ? 'bg-emerald-500/20 text-emerald-400'
                            : 'bg-rose-500/20 text-rose-400'
                        }`}
                      >
                        {item.actionTaken === 'accepted' ? 'Aceita pelo Motorista' : 'Recusada'}
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-slate-300">
                    {item.ride.pickupAddress} ➔ {item.ride.destinationAddress}
                  </div>
                </div>

                <div className="flex items-center gap-4 sm:gap-6 justify-between md:justify-end border-t md:border-t-0 pt-2 md:pt-0 border-slate-800">
                  <div className="text-left md:text-right">
                    <div className="text-[10px] text-slate-400 uppercase">Valor Total</div>
                    <div className="text-base font-extrabold text-white tabular-nums">
                      {formatCurrencyBRL(item.ride.price)}
                    </div>
                    <div className="text-[10px] text-slate-500">
                      {item.ride.totalDistanceKm}km · {item.ride.totalDurationMin}m
                    </div>
                  </div>

                  <div className="text-left md:text-right">
                    <div className="text-[10px] text-slate-400 uppercase">R$/KM</div>
                    <div
                      className={`text-base font-extrabold tabular-nums ${
                        item.meetsKmRequirement ? 'text-emerald-400' : 'text-rose-400'
                      }`}
                    >
                      R$ {item.pricePerKm.toFixed(2)}
                    </div>
                    <div className="text-[10px] text-slate-500">
                      {item.meetsKmRequirement ? '✓ Atingiu' : '✗ Baixo'}
                    </div>
                  </div>

                  <div className="text-left md:text-right">
                    <div className="text-[10px] text-slate-400 uppercase">R$/HORA</div>
                    <div
                      className={`text-base font-extrabold tabular-nums ${
                        item.meetsHourRequirement ? 'text-emerald-400' : 'text-rose-400'
                      }`}
                    >
                      R$ {item.pricePerHour.toFixed(2)}
                    </div>
                    <div className="text-[10px] text-slate-500">
                      {item.meetsHourRequirement ? '✓ Atingiu' : '✗ Baixo'}
                    </div>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
