export type RideVerdict = 'green' | 'yellow' | 'red';

export interface RideData {
  id: string;
  app: 'uber' | '99' | 'indrive';
  category: string; // ex: 'UberX', 'Uber Comfort', '99 Pop', '99 Plus'
  price: number; // ex: 34.50
  totalDistanceKm: number; // ex: 12.8 km (inclui busca + viagem)
  pickupDistanceKm: number; // ex: 2.1 km
  tripDistanceKm: number; // ex: 10.7 km
  totalDurationMin: number; // ex: 24 min
  pickupDurationMin: number; // ex: 6 min
  tripDurationMin: number; // ex: 18 min
  pickupAddress: string;
  destinationAddress: string;
  passengerRating?: number;
  timestamp: string;
}

export interface RideEvaluation {
  ride: RideData;
  pricePerKm: number; // R$ / km
  pricePerHour: number; // R$ / hora
  fuelCost: number; // Custo de combustível estimado
  netProfit: number; // Lucro líquido
  netPerHour: number; // Lucro líquido por hora
  meetsKmRequirement: boolean;
  meetsHourRequirement: boolean;
  verdict: RideVerdict;
  verdictReason: string;
  metricsMetCount: number; // 0, 1 ou 2
}

export interface DriverSettings {
  isEnabled: boolean; // Ativar / Desativar monitoramento
  minPricePerKm: number; // ex: R$ 2.20
  minPricePerHour: number; // ex: R$ 40.00
  fuelPricePerLiter: number; // ex: R$ 5.85
  vehicleConsumptionKmPerLiter: number; // ex: 11.5 km/l
  additionalCostPerKm: number; // Manutenção, pneus, etc (ex: R$ 0.20)
  soundAlertsEnabled: boolean;
  vibrationAlertsEnabled: boolean;
  autoDismissSeconds: number; // ex: 12 segundos
  targetApps: {
    uber: boolean;
    ninetyNine: boolean;
    inDrive: boolean;
  };
  overlayPosition: {
    x: number;
    y: number;
  };
  compactMode: boolean;
}

export interface ScanHistoryItem extends RideEvaluation {
  actionTaken?: 'accepted' | 'declined' | 'ignored';
  evaluatedAt: string;
}
