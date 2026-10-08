package com.radardecorridas.app.model

enum class RideVerdict {
    GREEN,
    YELLOW,
    RED
}

data class RideData(
    val id: String,
    val app: String, // "uber", "99", "indrive"
    val category: String, // "UberX", "Uber Comfort", "99Pop", "99Plus"
    val price: Double,
    val totalDistanceKm: Double,
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    val totalDurationMin: Int,
    val pickupDurationMin: Int,
    val tripDurationMin: Int,
    val pickupAddress: String,
    val destinationAddress: String,
    val passengerRating: Double? = null,
    val timestamp: String = "Agora mesmo"
)

data class RideEvaluation(
    val ride: RideData,
    val pricePerKm: Double,
    val pricePerHour: Double,
    val fuelCost: Double,
    val netProfit: Double,
    val netPerHour: Double,
    val meetsKmRequirement: Boolean,
    val meetsHourRequirement: Boolean,
    val verdict: RideVerdict,
    val verdictReason: String,
    val metricsMetCount: Int
)

data class DriverSettings(
    val isEnabled: Boolean = true,
    val minPricePerKm: Double = 2.00,
    val minPricePerHour: Double = 35.00,
    val fuelPricePerLiter: Double = 5.85,
    val vehicleConsumptionKmPerLiter: Double = 11.5,
    val additionalCostPerKm: Double = 0.20,
    val soundAlertsEnabled: Boolean = true,
    val vibrationAlertsEnabled: Boolean = true,
    val autoDismissSeconds: Int = 12,
    val targetUber: Boolean = true,
    val target99: Boolean = true,
    val targetInDrive: Boolean = true
)

data class ScanHistoryItem(
    val evaluation: RideEvaluation,
    val actionTaken: String? = null, // "accepted", "declined", "ignored"
    val evaluatedAt: String
)
