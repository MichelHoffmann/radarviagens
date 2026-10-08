/**
 * Synthesizes audio alert chimes using the Web Audio API without external audio assets.
 */

let audioCtx: AudioContext | null = null;

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (AudioContextClass) {
      audioCtx = new AudioContextClass();
    }
  }
  if (audioCtx && audioCtx.state === 'suspended') {
    audioCtx.resume();
  }
  return audioCtx;
}

export function playAlertSound(type: 'green' | 'yellow' | 'red') {
  try {
    const ctx = getAudioContext();
    if (!ctx) return;

    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.connect(gain);
    gain.connect(ctx.destination);

    if (type === 'green') {
      // Tom agudo ascendente duplo (Sucesso)
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(659.25, now); // E5
      osc.frequency.exponentialRampToValueAtTime(1046.5, now + 0.12); // C6
      gain.gain.setValueAtTime(0.18, now);
      gain.gain.exponentialRampToValueAtTime(0.01, now + 0.28);
      osc.start(now);
      osc.stop(now + 0.3);

      // Segundo tom
      setTimeout(() => {
        try {
          const osc2 = ctx.createOscillator();
          const gain2 = ctx.createGain();
          osc2.connect(gain2);
          gain2.connect(ctx.destination);
          const t2 = ctx.currentTime;
          osc2.type = 'sine';
          osc2.frequency.setValueAtTime(1318.51, t2); // E6
          gain2.gain.setValueAtTime(0.2, t2);
          gain2.gain.exponentialRampToValueAtTime(0.01, t2 + 0.3);
          osc2.start(t2);
          osc2.stop(t2 + 0.32);
        } catch {
          // Ignore
        }
      }, 100);
    } else if (type === 'yellow') {
      // Tom neutro duplo de atenção
      osc.type = 'sine';
      osc.frequency.setValueAtTime(587.33, now); // D5
      gain.gain.setValueAtTime(0.15, now);
      gain.gain.exponentialRampToValueAtTime(0.01, now + 0.2);
      osc.start(now);
      osc.stop(now + 0.22);

      setTimeout(() => {
        try {
          const osc2 = ctx.createOscillator();
          const gain2 = ctx.createGain();
          osc2.connect(gain2);
          gain2.connect(ctx.destination);
          const t2 = ctx.currentTime;
          osc2.type = 'sine';
          osc2.frequency.setValueAtTime(587.33, t2);
          gain2.gain.setValueAtTime(0.15, t2);
          gain2.gain.exponentialRampToValueAtTime(0.01, t2 + 0.2);
          osc2.start(t2);
          osc2.stop(t2 + 0.22);
        } catch {
          // Ignore
        }
      }, 140);
    } else {
      // Tom grave descendente de recusa (Vermelho)
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(280, now);
      osc.frequency.exponentialRampToValueAtTime(160, now + 0.3);
      gain.gain.setValueAtTime(0.18, now);
      gain.gain.exponentialRampToValueAtTime(0.01, now + 0.35);
      osc.start(now);
      osc.stop(now + 0.36);
    }
  } catch {
    // Audio context may not be ready or allowed
  }
}
