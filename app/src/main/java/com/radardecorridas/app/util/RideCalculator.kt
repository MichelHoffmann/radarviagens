package com.radardecorridas.app.util

import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.model.RideData
import com.radardecorridas.app.model.RideEvaluation
import com.radardecorridas.app.model.RideVerdict
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

object RideCalculator {

    fun evaluateRide(ride: RideData, settings: DriverSettings): RideEvaluation {
        val totalKm = max(0.1, ride.totalDistanceKm)
        val totalMin = max(1, ride.totalDurationMin)

        // 1. Valor por KM (R$/km)
        val pricePerKm = (ride.price / totalKm)

        // 2. Valor por Hora (R$/h)
        val pricePerHour = ((ride.price / totalMin) * 60.0)

        // Custos operacionais
        val consumption = max(1.0, settings.vehicleConsumptionKmPerLiter)
        val litersConsumed = totalKm / consumption
        val fuelCost = litersConsumed * settings.fuelPricePerLiter
        val maintenanceCost = totalKm * settings.additionalCostPerKm
        val totalCost = fuelCost + maintenanceCost
        val netProfit = ride.price - totalCost
        val netPerHour = ((netProfit / totalMin) * 60.0)

        // Verificação dos critérios definidos pelo motorista
        val meetsKmRequirement = pricePerKm >= settings.minPricePerKm
        val meetsHourRequirement = pricePerHour >= settings.minPricePerHour

        var metricsMetCount = 0
        if (meetsKmRequirement) metricsMetCount++
        if (meetsHourRequirement) metricsMetCount++

        val verdict: RideVerdict
        val verdictReason: String

        when (metricsMetCount) {
            2 -> {
                verdict = RideVerdict.GREEN
                verdictReason = "Excelente! Atende tanto a meta de R$/km quanto a meta de R$/hora."
            }
            1 -> {
                verdict = RideVerdict.YELLOW
                verdictReason = if (meetsKmRequirement) {
                    String.format(
                        Locale.getDefault(),
                        "Atende apenas R$/km (R$ %.2f/km), mas R$/hora está abaixo (R$ %.2f/h vs meta R$ %.2f/h). Trânsito lento.",
                        pricePerKm,
                        pricePerHour,
                        settings.minPricePerHour
                    )
                } else {
                    String.format(
                        Locale.getDefault(),
                        "Atende apenas R$/hora (R$ %.2f/h), mas R$/km está abaixo (R$ %.2f/km vs meta R$ %.2f/km). Alto consumo de combustível.",
                        pricePerHour,
                        pricePerKm,
                        settings.minPricePerKm
                    )
                }
            }
            else -> {
                verdict = RideVerdict.RED
                verdictReason = String.format(
                    Locale.getDefault(),
                    "Prejuízo! Não atende nenhum critério (R$ %.2f/km < R$ %.2f e R$ %.2f/h < R$ %.2f).",
                    pricePerKm,
                    settings.minPricePerKm,
                    pricePerHour,
                    settings.minPricePerHour
                )
            }
        }

        return RideEvaluation(
            ride = ride,
            pricePerKm = pricePerKm,
            pricePerHour = pricePerHour,
            fuelCost = fuelCost,
            netProfit = netProfit,
            netPerHour = netPerHour,
            meetsKmRequirement = meetsKmRequirement,
            meetsHourRequirement = meetsHourRequirement,
            verdict = verdict,
            verdictReason = verdictReason,
            metricsMetCount = metricsMetCount
        )
    }

    fun formatBRL(value: Double): String {
        return try {
            val ptBr = Locale("pt", "BR")
            NumberFormat.getCurrencyInstance(ptBr).format(value)
        } catch (_: Exception) {
            String.format(Locale.getDefault(), "R$ %.2f", value)
        }
    }
}
