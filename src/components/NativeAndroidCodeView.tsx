import React, { useState } from 'react';
import {
  Code2,
  Copy,
  Check,
  FileCode,
  Download,
  Terminal,
  Layers,
  Sparkles,
  Info
} from 'lucide-react';
import { ANDROID_PROJECT_FILES } from '../data/androidNativeCode';

export const NativeAndroidCodeView: React.FC = () => {
  const [selectedFileIndex, setSelectedFileIndex] = useState(0);
  const [copied, setCopied] = useState(false);

  const currentFile = ANDROID_PROJECT_FILES[selectedFileIndex];

  const handleCopy = () => {
    navigator.clipboard.writeText(currentFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadAll = () => {
    // Cria um arquivo de texto com todos os arquivos do projeto para o usuário
    let fullProjectExport = `=================================================================\n`;
    fullProjectExport += `PROJETO ANDROID NATIVO: RADAR DE CORRIDAS (UBER & 99)\n`;
    fullProjectExport += `Calculadora Flutuante com AccessibilityService e WindowManager\n`;
    fullProjectExport += `=================================================================\n\n`;

    ANDROID_PROJECT_FILES.forEach((f) => {
      fullProjectExport += `\n/* =============================================================\n`;
      fullProjectExport += ` * ARQUIVO: ${f.filename}\n`;
      fullProjectExport += ` * CAMINHO: ${f.path}\n`;
      fullProjectExport += ` * DESCRIÇÃO: ${f.description}\n`;
      fullProjectExport += ` * ============================================================= */\n\n`;
      fullProjectExport += f.content;
      fullProjectExport += `\n\n`;
    });

    const blob = new Blob([fullProjectExport], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'projeto_android_radar_corridas.txt';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      {/* Cabeçalho */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-800">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-white flex items-center gap-2.5">
            <Code2 className="w-6 h-6 text-emerald-400" />
            <span>Código-Fonte & Projeto Android Nativo (Kotlin)</span>
          </h1>
          <p className="text-xs sm:text-sm text-slate-400 mt-1">
            Arquitetura nativa completa para compilação direta no Android Studio.
          </p>
        </div>

        <button
          onClick={handleDownloadAll}
          className="px-4 py-2.5 rounded-xl bg-emerald-500 text-emerald-950 font-bold text-xs hover:bg-emerald-400 transition-colors shadow-lg shadow-emerald-950 flex items-center gap-2 self-start sm:self-auto"
        >
          <Download className="w-4 h-4" />
          <span>Baixar Arquivos do Projeto</span>
        </button>
      </div>

      {/* Explicação da Arquitetura Nativa no Android */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="p-4 bg-slate-900 border border-slate-800 rounded-2xl space-y-2">
          <div className="flex items-center gap-2 text-emerald-400 font-bold text-xs">
            <Layers className="w-4 h-4" />
            <span>1. AccessibilityService</span>
          </div>
          <h4 className="text-sm font-semibold text-white">Escaneamento Instantâneo</h4>
          <p className="text-xs text-slate-400 leading-relaxed">
            Lê nós de acessibilidade diretamente de <code className="text-emerald-300">com.ubercab.driver</code> e <code className="text-emerald-300">com.taxis99</code> em microssegundos, sem esquentar o celular ou gastar bateria com OCR pesado.
          </p>
        </div>

        <div className="p-4 bg-slate-900 border border-slate-800 rounded-2xl space-y-2">
          <div className="flex items-center gap-2 text-teal-400 font-bold text-xs">
            <Terminal className="w-4 h-4" />
            <span>2. WindowManager Overlay</span>
          </div>
          <h4 className="text-sm font-semibold text-white">Pop-up Flutuante com Cores</h4>
          <p className="text-xs text-slate-400 leading-relaxed">
            Utiliza <code className="text-teal-300">TYPE_APPLICATION_OVERLAY</code> para exibir o balão sobre os apps de corrida com arraste suave pelo dedo e cores Verde, Amarelo e Vermelho.
          </p>
        </div>

        <div className="p-4 bg-slate-900 border border-slate-800 rounded-2xl space-y-2">
          <div className="flex items-center gap-2 text-amber-400 font-bold text-xs">
            <Sparkles className="w-4 h-4" />
            <span>3. Motor de Decisão R$/km e R$/h</span>
          </div>
          <h4 className="text-sm font-semibold text-white">Algoritmo dos 3 Níveis</h4>
          <p className="text-xs text-slate-400 leading-relaxed">
            Cruza o R$/km e o R$/h com as metas salvas no SharedPreferences. Aplica verde para 2/2 requisitos, amarelo para 1 requisito e vermelho quando ambos falham.
          </p>
        </div>
      </div>

      {/* Navegador de Arquivos do Projeto */}
      <div className="bg-slate-900 border border-slate-800 rounded-3xl overflow-hidden shadow-2xl">
        {/* Barra de Seleção de Abas de Arquivo */}
        <div className="flex items-center gap-1 p-2 bg-slate-950/80 border-b border-slate-800 overflow-x-auto">
          {ANDROID_PROJECT_FILES.map((file, idx) => (
            <button
              key={idx}
              onClick={() => setSelectedFileIndex(idx)}
              className={`px-3 py-1.5 rounded-xl text-xs font-medium whitespace-nowrap transition-colors flex items-center gap-1.5 shrink-0 ${
                selectedFileIndex === idx
                  ? 'bg-slate-800 text-emerald-400 shadow-sm border border-emerald-500/30'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
              }`}
            >
              <FileCode className="w-3.5 h-3.5" />
              <span>{file.filename}</span>
            </button>
          ))}
        </div>

        {/* Informações do Arquivo Selecionado */}
        <div className="p-4 bg-slate-900 border-b border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <div className="text-xs font-mono text-emerald-400">{currentFile.path}</div>
            <p className="text-xs text-slate-300 mt-1">{currentFile.description}</p>
          </div>

          <button
            onClick={handleCopy}
            className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold flex items-center gap-1.5 transition-colors shrink-0 self-start sm:self-auto"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-emerald-400" />
                <span className="text-emerald-400">Copiado!</span>
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" />
                <span>Copiar Código</span>
              </>
            )}
          </button>
        </div>

        {/* Visualizador de Código com Numeração de Linhas */}
        <div className="p-4 bg-slate-950 font-mono text-xs text-slate-300 overflow-x-auto max-h-[520px] select-text">
          <pre className="leading-relaxed">
            <code>{currentFile.content}</code>
          </pre>
        </div>
      </div>

      {/* Tutorial de Instalação e Execução */}
      <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 space-y-4">
        <h3 className="text-base font-bold text-white flex items-center gap-2">
          <Info className="w-5 h-5 text-emerald-400" />
          <span>Como Compilar e Rodar no Celular via Android Studio</span>
        </h3>

        <div className="space-y-3 text-xs text-slate-300 leading-relaxed">
          <div className="p-3 bg-slate-950/60 rounded-2xl border border-white/5 space-y-1">
            <span className="font-bold text-emerald-400">Passo 1: Criar o Projeto</span>
            <p>
              Abra o Android Studio, escolha <strong>New Project</strong> ➔ <strong>Empty Views Activity</strong>. Defina a linguagem como <strong>Kotlin</strong> e nome do pacote como <code className="text-slate-200">com.radardecorridas</code>.
            </p>
          </div>

          <div className="p-3 bg-slate-950/60 rounded-2xl border border-white/5 space-y-1">
            <span className="font-bold text-emerald-400">Passo 2: Copiar os Arquivos</span>
            <p>
              Copie os arquivos de serviço (<code className="text-slate-200">RideScannerAccessibilityService.kt</code> e <code className="text-slate-200">FloatingOverlayService.kt</code>) e o layout XML para a pasta <code className="text-slate-200">res/layout/</code>. Configure as permissões no <code className="text-slate-200">AndroidManifest.xml</code>.
            </p>
          </div>

          <div className="p-3 bg-slate-950/60 rounded-2xl border border-white/5 space-y-1">
            <span className="font-bold text-emerald-400">Passo 3: Conceder Permissões no Celular Android</span>
            <p>
              Ao abrir o aplicativo pela primeira vez no seu celular, clique para conceder:
              <br />
              • <strong>Sobrepor a outros aplicativos</strong> (para desenhar o balão sobre a Uber/99).
              <br />
              • <strong>Acessibilidade</strong> (Configurações ➔ Acessibilidade ➔ Radar de Corridas ➔ Ativar).
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
