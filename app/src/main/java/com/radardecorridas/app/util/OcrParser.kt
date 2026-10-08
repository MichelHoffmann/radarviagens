package com.radardecorridas.app.util

import android.util.Log
import com.radardecorridas.app.model.RideData
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object OcrParser {

    private const val TAG = "RadarDeCorridas"

    fun parseRideText(rawText: String): RideData? {
        if (rawText.isBlank()) return null

        // Normaliza quebras de linha e espaços não separáveis comuns em Android (ex: \u00A0 entre R$ e valor)
        val text = rawText
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u200B', ' ')
            .replace("\r\n", "\n")

        val normalized = text.lowercase()

        // 1. Detectar Aplicativo
        val app: String
        val category: String
        if (normalized.contains("99") || normalized.contains("pop") || normalized.contains("99plus") ||
            normalized.contains("99moto") || normalized.contains("99taxi") || normalized.contains("taxis99") ||
            normalized.contains("didi")
        ) {
            app = "99"
            category = when {
                normalized.contains("plus") -> "99Plus"
                normalized.contains("moto") -> "99Moto"
                normalized.contains("taxi") || normalized.contains("táxi") -> "99Táxi"
                normalized.contains("entrega") -> "99Entrega"
                else -> "99Pop"
            }
        } else if (normalized.contains("indrive") || normalized.contains("passageiro oferece")) {
            app = "indrive"
            category = "inDrive Viagem"
        } else {
            app = "uber"
            category = when {
                normalized.contains("comfort") -> "Uber Comfort"
                normalized.contains("black") -> "Uber Black"
                normalized.contains("flash") -> "Uber Flash"
                normalized.contains("moto") -> "Uber Moto"
                else -> "UberX"
            }
        }

        // 2. Extrair Preço (Ex: R$ 18,90 / R$18.90 / R$ 25 / Valor: R$ 34,50)
        var price = 0.0
        val pricePattern = Pattern.compile(
            "(?:R\\$|R\\$\\s*|Valor:?\\s*R?\\$?\\s*|Ganha:?\\s*R?\\$?\\s*)(\\d{1,4}(?:[.,]\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )
        val priceMatcher = pricePattern.matcher(text)
        if (priceMatcher.find()) {
            price = priceMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        } else {
            // Tenta encontrar menção a R$ com formato numérico
            val genericPattern = Pattern.compile("R\\$\\s*(\\d{1,4}(?:[.,]\\d{2})?)", Pattern.CASE_INSENSITIVE)
            val genericMatcher = genericPattern.matcher(text)
            if (genericMatcher.find()) {
                price = genericMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            }
        }

        // 3. Extrair Distâncias (Ex: 8,4 km / 8.4km / 12 km / quilômetros)
        val distancePattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:km|quil[oô]metros)", Pattern.CASE_INSENSITIVE)
        val distanceMatcher = distancePattern.matcher(text)
        val distances = mutableListOf<Double>()
        while (distanceMatcher.find()) {
            distanceMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull()?.let {
                if (it > 0.0) distances.add(it)
            }
        }

        var totalDistanceKm = 0.0
        var pickupDistanceKm = 0.0
        var tripDistanceKm = 0.0

        // Verifica menção explícita de "no total" comum na Uber
        val totalExplicitPattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:km|quil[oô]metros)?\\s*no total", Pattern.CASE_INSENSITIVE)
        val totalExplicitMatcher = totalExplicitPattern.matcher(text)
        if (totalExplicitMatcher.find()) {
            totalDistanceKm = totalExplicitMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        if (totalDistanceKm <= 0.0) {
            if (distances.size == 1) {
                totalDistanceKm = distances[0]
                pickupDistanceKm = (totalDistanceKm * 0.15 * 10).roundToInt() / 10.0
                tripDistanceKm = ((totalDistanceKm - pickupDistanceKm) * 10).roundToInt() / 10.0
            } else if (distances.size >= 2) {
                pickupDistanceKm = min(distances[0], distances[1])
                tripDistanceKm = max(distances[0], distances[1])
                totalDistanceKm = ((pickupDistanceKm + tripDistanceKm) * 10).roundToInt() / 10.0
            }
        } else {
            if (distances.isNotEmpty()) {
                val other = distances.firstOrNull { it != totalDistanceKm }
                if (other != null && other < totalDistanceKm) {
                    pickupDistanceKm = other
                    tripDistanceKm = ((totalDistanceKm - pickupDistanceKm) * 10).roundToInt() / 10.0
                } else {
                    pickupDistanceKm = (totalDistanceKm * 0.15 * 10).roundToInt() / 10.0
                    tripDistanceKm = ((totalDistanceKm - pickupDistanceKm) * 10).roundToInt() / 10.0
                }
            }
        }

        // 4. Extrair Duração (Ex: 18 min / 18 minutos / 18m / 1h 10min)
        var totalDurationMin = 0
        val hourPattern = Pattern.compile("(\\d+)\\s*h(?:oras?)?", Pattern.CASE_INSENSITIVE)
        val hourMatcher = hourPattern.matcher(text)
        if (hourMatcher.find()) {
            totalDurationMin += (hourMatcher.group(1)?.toIntOrNull() ?: 0) * 60
        }

        val minPattern = Pattern.compile("(\\d+)\\s*(?:min|minutos|m\\b)", Pattern.CASE_INSENSITIVE)
        val minMatcher = minPattern.matcher(text)
        val minutesList = mutableListOf<Int>()
        while (minMatcher.find()) {
            minMatcher.group(1)?.toIntOrNull()?.let {
                if (it > 0) minutesList.add(it)
            }
        }

        if (minutesList.size == 1) {
            totalDurationMin += minutesList[0]
        } else if (minutesList.size >= 2) {
            totalDurationMin += minutesList.sum()
        }

        // Validação estrita: SEM fallback fictício para evitar falsos positivos
        if (price <= 0.0) {
            Log.d(TAG, "OcrParser: Nenhum preço válido detectado.")
            return null
        }

        if (totalDistanceKm <= 0.0 && totalDurationMin <= 0) {
            Log.d(TAG, "OcrParser: Preço R$$price detectado, mas sem distância ou tempo. Descartando.")
            return null
        }

        // Se uma das métricas estiver presente e a outra faltar, estima realisticamente para os cálculos
        if (totalDurationMin <= 0 && totalDistanceKm > 0.0) {
            totalDurationMin = max(5, (totalDistanceKm * 2.2).roundToInt())
        }
        if (totalDistanceKm <= 0.0 && totalDurationMin > 0) {
            totalDistanceKm = max(1.0, ((totalDurationMin / 2.2) * 10).roundToInt() / 10.0)
        }

        Log.d(
            TAG,
            "OcrParser: CORRIDA IDENTIFICADA! App=$app, Cat=$category, Preço=R$$price, Dist=${totalDistanceKm}km, Tempo=${totalDurationMin}min"
        )

        return RideData(
            id = "scan-${System.currentTimeMillis()}",
            app = app,
            category = category,
            price = price,
            totalDistanceKm = totalDistanceKm,
            pickupDistanceKm = pickupDistanceKm,
            tripDistanceKm = tripDistanceKm,
            totalDurationMin = totalDurationMin,
            pickupDurationMin = max(2, (totalDurationMin * 0.2).roundToInt()),
            tripDurationMin = max(1, (totalDurationMin * 0.8).roundToInt()),
            pickupAddress = "Ponto de Embarque",
            destinationAddress = "Destino da Viagem",
            passengerRating = 4.90,
            timestamp = "Agora mesmo"
        )
    }
}
