import { RideData } from '../types';

export function parseRideText(rawText: string): Partial<RideData> | null {
  if (!rawText || rawText.trim().length === 0) return null;

  const text = rawText.replace(/\r\n/g, '\n');
  const normalized = text.toLowerCase();

  // Detect App
  let app: 'uber' | '99' | 'indrive' = 'uber';
  let category = 'UberX';
  if (normalized.includes('99') || normalized.includes('pop') || normalized.includes('99plus') || normalized.includes('corrida 99')) {
    app = '99';
    category = normalized.includes('plus') ? '99Plus' : '99Pop';
  } else if (normalized.includes('indrive') || normalized.includes('passageiro oferece')) {
    app = 'indrive';
    category = 'inDrive Viagem';
  } else {
    app = 'uber';
    if (normalized.includes('comfort')) category = 'Uber Comfort';
    else if (normalized.includes('black')) category = 'Uber Black';
    else if (normalized.includes('flash')) category = 'Uber Flash';
    else category = 'UberX';
  }

  // Detect Price (e.g. "R$ 34,50", "R$34.50", "34,50", "R$ 112,00")
  let price = 0;
  const priceRegex = /(?:r\$\s*|valor\s*:?\s*)(\d{1,3}(?:[.,]\d{2})?)/i;
  const priceMatch = text.match(priceRegex);
  if (priceMatch) {
    const rawVal = priceMatch[1].replace(',', '.');
    price = parseFloat(rawVal);
  } else {
    // Tenta encontrar qualquer valor decimal típico de corrida
    const genericPriceRegex = /\b(\d{1,3}[.,]\d{2})\b/;
    const genericMatch = text.match(genericPriceRegex);
    if (genericMatch) {
      price = parseFloat(genericMatch[1].replace(',', '.'));
    }
  }

  // Detect Distances (e.g., "12,5 km", "12.5km", "a 2,4 km", "viagem: 10 km")
  const distanceMatches = Array.from(text.matchAll(/(\d+(?:[.,]\d+)?)\s*(?:km|quil[oô]metros)\b/gi));
  let totalDistanceKm = 0;
  let pickupDistanceKm = 0;
  let tripDistanceKm = 0;

  if (distanceMatches.length === 1) {
    totalDistanceKm = parseFloat(distanceMatches[0][1].replace(',', '.'));
    pickupDistanceKm = Number((totalDistanceKm * 0.15).toFixed(1));
    tripDistanceKm = Number((totalDistanceKm - pickupDistanceKm).toFixed(1));
  } else if (distanceMatches.length >= 2) {
    const d1 = parseFloat(distanceMatches[0][1].replace(',', '.'));
    const d2 = parseFloat(distanceMatches[1][1].replace(',', '.'));
    pickupDistanceKm = Math.min(d1, d2);
    tripDistanceKm = Math.max(d1, d2);
    totalDistanceKm = Number((pickupDistanceKm + tripDistanceKm).toFixed(1));
  }

  // Detect Durations (e.g. "25 min", "1h 10min", "30 minutos")
  let totalDurationMin = 0;
  const hourMatch = text.match(/(\d+)\s*h(?:oras?)?/i);
  const minMatches = Array.from(text.matchAll(/(\d+)\s*(?:min|minutos)\b/gi));

  if (hourMatch) {
    totalDurationMin += parseInt(hourMatch[1], 10) * 60;
  }

  if (minMatches.length === 1) {
    totalDurationMin += parseInt(minMatches[0][1], 10);
  } else if (minMatches.length >= 2) {
    const m1 = parseInt(minMatches[0][1], 10);
    const m2 = parseInt(minMatches[1][1], 10);
    totalDurationMin = m1 + m2;
  }

  if (price <= 0) price = 25.0;
  if (totalDistanceKm <= 0) totalDistanceKm = 8.5;
  if (totalDurationMin <= 0) totalDurationMin = 20;

  return {
    id: `scan-${Date.now()}`,
    app,
    category,
    price,
    totalDistanceKm,
    pickupDistanceKm,
    tripDistanceKm,
    totalDurationMin,
    pickupDurationMin: Math.max(2, Math.round(totalDurationMin * 0.2)),
    tripDurationMin: Math.max(1, Math.round(totalDurationMin * 0.8)),
    pickupAddress: 'Ponto de Embarque Identificado',
    destinationAddress: 'Destino da Viagem',
    timestamp: 'Agora mesmo',
  };
}
