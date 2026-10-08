import React, { useState } from 'react';
import {
  Scan,
  UploadCloud,
  FileText,
  Sparkles,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Play,
  ClipboardPaste,
  Image as ImageIcon
} from 'lucide-react';
import { DriverSettings, RideData } from '../types';
import { parseRideText } from '../utils/ocrParser';
import { evaluateRide, formatCurrencyBRL } from '../utils/rideCalculator';

interface ScreenScannerUploaderProps {
  settings: DriverSettings;
  onSendToPhone: (ride: RideData) => void;
}

const SAMPLE_OCR_TEXTS = [
  {
    label: 'Print UberX Aeroporto (Ótima)',
    text: `UberX
R$ 44,80
14,2 km no total
A 2,1 km (5 min) do embarque
Viagem: 12,1 km (22 min)
Av. Brigadeiro Luis Antonio -> Aeroporto de Congonhas
Passageiro 4.95 ★`,
  },
  {
    label: 'Print 99Pop Horário de Pico (Parcial)',
    text: `99Pop
R$ 19,00
11,0 km total (32 min)
Embarque a 1,5 km
Destino: Shopping Morumbi
Apenas 1 critério atende`,
  },
  {
    label: 'Print UberX Chuva & Trânsito (Prejuízo)',
    text: `UberX
R$ 15,20
13,8 km total
Tempo estimado: 48 min
Embarque a 3,5 km (12 min)
Trânsito intenso na Marginal Pinheiros`,
  },
];

export const ScreenScannerUploader: React.FC<ScreenScannerUploaderProps> = ({
  settings,
  onSendToPhone,
}) => {
  const [inputText, setInputText] = useState(SAMPLE_OCR_TEXTS[0].text);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [isScanning, setIsScanning] = useState(false);

  const scannedRide = parseRideText(inputText);
  const fullRide: RideData | null = scannedRide
    ? {
        id: scannedRide.id || `scanned-${Date.now()}`,
        app: scannedRide.app || 'uber',
        category: scannedRide.category || 'UberX',
        price: scannedRide.price || 30.0,
        totalDistanceKm: scannedRide.totalDistanceKm || 10.0,
        pickupDistanceKm: scannedRide.pickupDistanceKm || 2.0,
        tripDistanceKm: scannedRide.tripDistanceKm || 8.0,
        totalDurationMin: scannedRide.totalDurationMin || 22,
        pickupDurationMin: scannedRide.pickupDurationMin || 5,
        tripDurationMin: scannedRide.tripDurationMin || 17,
        pickupAddress: scannedRide.pickupAddress || 'Ponto de Partida Escaneado',
        destinationAddress: scannedRide.destinationAddress || 'Destino Escaneado',
        passengerRating: 4.92,
        timestamp: 'Escaneado da Tela',
      }
    : null;

  const evaluation = fullRide ? evaluateRide(fullRide, settings) : null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsScanning(true);
    const reader = new FileReader();
    reader.onload = () => {
      setImagePreview(reader.result as string);
      // Simula OCR inteligente extraindo valores da imagem
      setTimeout(() => {
        setIsScanning(false);
        setInputText(`Corrida Identificada no Print da Tela
${file.name.toLowerCase().includes('99') ? '99Pop' : 'UberX'}
R$ 38,90
Distância total: 11,4 km
Tempo estimado: 21 min
Embarque a 1,8 km (4 min)
Trajeto até destino: 9,6 km (17 min)`);
      }, 700);
    };
    reader.readAsDataURL(file);
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-4 border-b border-slate-800">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-white flex items-center gap-2.5">
            <Scan className="w-6 h-6 text-emerald-400" />
            <span>Scanner & Leitor de Tela de Corridas</span>
          </h1>
          <p className="text-xs sm:text-sm text-slate-400 mt-1">
            Simule o escaneamento de um print ou texto de notificação de corrida da Uber e 99.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Lado Esquerdo: Input de Imagem ou Texto */}
        <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 space-y-4">
          <h2 className="text-sm font-bold text-white flex items-center gap-2">
            <UploadCloud className="w-4 h-4 text-emerald-400" />
            <span>Upload de Print da Tela ou Texto</span>
          </h2>

          {/* Área de Upload de Imagem */}
          <div className="relative border-2 border-dashed border-slate-700 hover:border-emerald-500 rounded-2xl p-4 text-center cursor-pointer transition-colors bg-slate-950/40">
            <input
              type="file"
              accept="image/*"
              onChange={handleFileUpload}
              className="absolute inset-0 opacity-0 cursor-pointer w-full h-full"
            />
            {imagePreview ? (
              <div className="flex items-center gap-3">
                <img
                  src={imagePreview}
                  alt="Print da corrida"
                  className="w-16 h-16 object-cover rounded-xl border border-white/10"
                />
                <div className="text-left text-xs">
                  <div className="text-emerald-400 font-bold flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>Print Carregado</span>
                  </div>
                  <span className="text-slate-400 text-[11px]">Clique para substituir a imagem</span>
                </div>
              </div>
            ) : (
              <div className="space-y-1.5 py-3">
                <ImageIcon className="w-8 h-8 text-slate-500 mx-auto" />
                <div className="text-xs font-semibold text-slate-300">
                  Arraste ou selecione o print da corrida
                </div>
                <div className="text-[10px] text-slate-500">JPG, PNG ou captura de tela</div>
              </div>
            )}
          </div>

          {/* Presets Rápidos de OCR */}
          <div className="space-y-1.5">
            <span className="text-xs font-semibold text-slate-400 flex items-center gap-1">
              <ClipboardPaste className="w-3.5 h-3.5" />
              <span>Ou escolha um exemplo real escaneado:</span>
            </span>
            <div className="flex flex-wrap gap-2">
              {SAMPLE_OCR_TEXTS.map((sample, idx) => (
                <button
                  key={idx}
                  onClick={() => setInputText(sample.text)}
                  className="text-xs px-2.5 py-1 rounded-xl bg-slate-800 text-slate-300 hover:bg-slate-700 hover:text-white transition-colors border border-white/5"
                >
                  {sample.label}
                </button>
              ))}
            </div>
          </div>

          {/* Campo de Texto Reconhecido pelo Scanner */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
              <span>Texto Detectado na Tela (OCR):</span>
              <span className="text-[10px] text-slate-500">Editável para testes</span>
            </label>
            <textarea
              rows={6}
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              className="w-full px-3 py-2 text-xs font-mono rounded-xl bg-slate-950 border border-slate-700 text-slate-200 focus:border-emerald-500 outline-none leading-relaxed"
            />
          </div>
        </div>

        {/* Lado Direito: Resultado da Avaliação */}
        <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 space-y-5">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-white flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-emerald-400" />
              <span>Resultado do Scanner em Tempo Real</span>
            </h2>
            {isScanning && (
              <span className="text-xs text-amber-400 font-semibold animate-pulse">
                Escaneando...
              </span>
            )}
          </div>

          {evaluation && fullRide ? (
            <div className="space-y-4">
              {/* Veredito Visual */}
              <div
                className={`p-4 rounded-2xl border ${
                  evaluation.verdict === 'green'
                    ? 'bg-emerald-950/60 border-emerald-500/60 text-emerald-400'
                    : evaluation.verdict === 'yellow'
                    ? 'bg-amber-950/60 border-amber-500/60 text-amber-400'
                    : 'bg-rose-950/60 border-rose-500/60 text-rose-400'
                }`}
              >
                <div className="flex items-center gap-2 font-black text-sm tracking-wide">
                  {evaluation.verdict === 'green' && <CheckCircle2 className="w-5 h-5 shrink-0" />}
                  {evaluation.verdict === 'yellow' && <AlertTriangle className="w-5 h-5 shrink-0" />}
                  {evaluation.verdict === 'red' && <XCircle className="w-5 h-5 shrink-0" />}
                  <span>
                    {evaluation.verdict === 'green'
                      ? '🟢 NÍVEL VERDE: CORRIDA VALE A PENA'
                      : evaluation.verdict === 'yellow'
                      ? '🟡 NÍVEL AMARELO: ATENÇÃO (1 CRITÉRIO)'
                      : '🔴 NÍVEL VERMELHO: RECUSAR (PREJUÍZO)'}
                  </span>
                </div>
                <p className="text-xs text-slate-300 mt-1.5 leading-relaxed">
                  {evaluation.verdictReason}
                </p>
              </div>

              {/* Dados extraídos */}
              <div className="grid grid-cols-2 gap-3">
                <div className="p-3 bg-slate-950/60 rounded-2xl border border-white/5">
                  <div className="text-[10px] text-slate-400 uppercase font-semibold">Valor da Corrida</div>
                  <div className="text-xl font-black text-white mt-0.5">
                    {formatCurrencyBRL(fullRide.price)}
                  </div>
                  <div className="text-[10px] text-slate-500">{fullRide.category}</div>
                </div>

                <div className="p-3 bg-slate-950/60 rounded-2xl border border-white/5">
                  <div className="text-[10px] text-slate-400 uppercase font-semibold">Distância / Tempo</div>
                  <div className="text-base font-extrabold text-white mt-0.5">
                    {fullRide.totalDistanceKm} km · {fullRide.totalDurationMin} min
                  </div>
                  <div className="text-[10px] text-slate-500">Total calculado</div>
                </div>

                <div
                  className={`p-3 rounded-2xl border ${
                    evaluation.meetsKmRequirement
                      ? 'bg-emerald-950/40 border-emerald-500/40'
                      : 'bg-rose-950/40 border-rose-500/40'
                  }`}
                >
                  <div className="text-[10px] text-slate-400 uppercase font-semibold">R$ por KM</div>
                  <div className="text-lg font-black text-white mt-0.5">
                    R$ {evaluation.pricePerKm.toFixed(2)}/km
                  </div>
                  <div className={`text-[10px] font-bold ${evaluation.meetsKmRequirement ? 'text-emerald-400' : 'text-rose-400'}`}>
                    Meta: R$ {settings.minPricePerKm.toFixed(2)}
                  </div>
                </div>

                <div
                  className={`p-3 rounded-2xl border ${
                    evaluation.meetsHourRequirement
                      ? 'bg-emerald-950/40 border-emerald-500/40'
                      : 'bg-rose-950/40 border-rose-500/40'
                  }`}
                >
                  <div className="text-[10px] text-slate-400 uppercase font-semibold">R$ por Hora</div>
                  <div className="text-lg font-black text-white mt-0.5">
                    R$ {evaluation.pricePerHour.toFixed(2)}/h
                  </div>
                  <div className={`text-[10px] font-bold ${evaluation.meetsHourRequirement ? 'text-emerald-400' : 'text-rose-400'}`}>
                    Meta: R$ {settings.minPricePerHour.toFixed(2)}
                  </div>
                </div>
              </div>

              {/* Ação para testar no celular */}
              <button
                onClick={() => onSendToPhone(fullRide)}
                className="w-full py-3 rounded-2xl bg-emerald-500 text-emerald-950 font-bold text-xs hover:bg-emerald-400 transition-colors shadow-lg shadow-emerald-950 flex items-center justify-center gap-2"
              >
                <Play className="w-4 h-4 fill-current" />
                <span>Exibir Pop-up Flutuante no Smartphone</span>
              </button>
            </div>
          ) : (
            <div className="py-12 text-center text-slate-500 text-xs">
              Insira texto ou envie uma imagem para escanear a corrida.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
