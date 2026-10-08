import { DriverSettings, RideData, RideEvaluation, RideVerdict } from '../types';

export function evaluateRide(ride: RideData, settings: DriverSettings): RideEvaluation {
  const totalKm = Math.max(0.1, ride.totalDistanceKm);
  const totalMin = Math.max(1, ride.totalDurationMin);

  // 1. Valor por KM (R$/km)
  const pricePerKm = Number((ride.price / totalKm).toFixed(2));

  // 2. Valor por Hora (R$/h)
  const pricePerHour = Number(((ride.price / totalMin) * 60).toFixed(2));

  // Custos operacionais do motorista
  // Consumo: litros = totalKm / consumoKmPorLitro
  const litersConsumed = totalKm / Math.max(1, settings.vehicleConsumptionKmPerLiter);
  const fuelCost = Number((litersConsumed * settings.fuelPricePerLiter).toFixed(2));
  const maintenanceCost = Number((totalKm * settings.additionalCostPerKm).toFixed(2));
  const totalCost = fuelCost + maintenanceCost;
  const netProfit = Number((ride.price - totalCost).toFixed(2));
  const netPerHour = Number(((netProfit / totalMin) * 60).toFixed(2));

  // Verificação dos critérios configurados pelo motorista
  const meetsKmRequirement = pricePerKm >= settings.minPricePerKm;
  const meetsHourRequirement = pricePerHour >= settings.minPricePerHour;

  let metricsMetCount = 0;
  if (meetsKmRequirement) metricsMetCount++;
  if (meetsHourRequirement) metricsMetCount++;

  let verdict: RideVerdict;
  let verdictReason = '';

  if (metricsMetCount === 2) {
    verdict = 'green';
    verdictReason = 'Excelente! Atende tanto a meta de R$/km quanto a meta de R$/hora.';
  } else if (metricsMetCount === 1) {
    verdict = 'yellow';
    if (meetsKmRequirement && !meetsHourRequirement) {
      verdictReason = `Atende apenas R$/km (R$ ${pricePerKm.toFixed(2)}/km), mas o valor por hora está abaixo da meta (R$ ${pricePerHour.toFixed(2)}/h vs R$ ${settings.minPricePerHour.toFixed(2)}/h). Possível trânsito lento.`;
    } else {
      verdictReason = `Atende apenas R$/hora (R$ ${pricePerHour.toFixed(2)}/h), mas o valor por km está abaixo da meta (R$ ${pricePerKm.toFixed(2)}/km vs R$ ${settings.minPricePerKm.toFixed(2)}/km). Corrida longa que consome mais combustível.`;
    }
  } else {
    verdict = 'red';
    verdictReason = `Prejuízo! Não atende nenhum critério (R$ ${pricePerKm.toFixed(2)}/km < R$ ${settings.minPricePerKm.toFixed(2)} e R$ ${pricePerHour.toFixed(2)}/h < R$ ${settings.minPricePerHour.toFixed(2)}).`;
  }

  return {
    ride,
    pricePerKm,
    pricePerHour,
    fuelCost,
    netProfit,
    netPerHour,
    meetsKmRequirement,
    meetsHourRequirement,
    verdict,
    verdictReason,
    metricsMetCount,
  };
}

export function formatCurrencyBRL(value: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(value);
}
