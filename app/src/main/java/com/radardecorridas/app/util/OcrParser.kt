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

        // Normaliza quebras de linha e caracteres especiais invisíveis comuns no OCR
        val text = rawText
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u200B', ' ')
            .replace("\r\n", "\n")

        // Requisito 8: Ignorar o texto do próprio pop-up do app para evitar que o OCR leia o pop-up e entre em loop
        val lines = text.lines()
        val filteredLines = lines.filterNot { line ->
            val l = line.lowercase()
            l.contains("verde") || l.contains("amarelo") || l.contains("vermelho") ||
            l.contains("vale a pena") || l.contains("atenção") || l.contains("atencao") ||
            l.contains("recusar") || l.contains("radar de corridas") ||
            l.contains("/km") || l.contains("/h") || l.contains("/hora") ||
            l.contains("r$/km") || l.contains("r$/h")
        }
        val cleanText = filteredLines.joinToString("\n").trim()
        if (cleanText.length < 5) return null

        val normalized = cleanText.lowercase()

        // 1. Detectar Aplicativo
        val app: String
        val category: String
        if (normalized.contains("99") || normalized.contains("pop") || normalized.contains("99plus") ||
            normalized.contains("99moto") || normalized.contains("99taxi") || normalized.contains("taxis99") ||
            normalized.contains("didi") || normalized.contains("passageiro") || normalized.contains("viagem")
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

        // 2. Extrair Preço em R$ (Requisito 4: tolerar "R$ 12,50", "R$12.50", "12,50")
        var price = 0.0

        // Prioridade 1: Botão explícito "Aceitar por R$ 12,50" ou "Aceitar R$ 12,50"
        val acceptPricePattern = Pattern.compile(
            "aceitar\\s+(?:por\\s+)?r?\\$?\\s*(\\d{1,4}(?:[.,]\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )
        val acceptMatcher = acceptPricePattern.matcher(cleanText)
        if (acceptMatcher.find()) {
            price = acceptMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        // Prioridade 2: Valores precedidos por R$ ou palavras-chave de ganho (sem ser taxa por /km ou /h)
        if (price <= 0.0) {
            val priceGeneralPattern = Pattern.compile(
                "(?:r\\$\\s*|valor:?\\s*r?\\$?\\s*|ganha:?\\s*r?\\$?\\s*)(\\d{1,4}(?:[.,]\\d{1,2})?)(?!\\s*/\\s*(?:km|h|hora))",
                Pattern.CASE_INSENSITIVE
            )
            val priceMatcher = priceGeneralPattern.matcher(cleanText)
            val candidatePrices = mutableListOf<Double>()
            while (priceMatcher.find()) {
                val value = priceMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                if (value >= 2.0) {
                    candidatePrices.add(value)
                }
            }
            if (candidatePrices.isNotEmpty()) {
                price = candidatePrices.maxOrNull() ?: candidatePrices[0]
            }
        }

        // Prioridade 3: Tolerância a valor sem prefixo "R$", como "12,50" ou "12.50" (prints da galeria, etc.)
        if (price <= 0.0) {
            val standalonePricePattern = Pattern.compile(
                "(?<![\\d.,/])(\\d{1,3}[.,]\\d{2})(?![\\d.,/]|\\s*(?:km|quil|min|m\\b|h\\b|hora|%|/))",
                Pattern.CASE_INSENSITIVE
            )
            val standaloneMatcher = standalonePricePattern.matcher(cleanText)
            val candidatePrices = mutableListOf<Double>()
            while (standaloneMatcher.find()) {
                val value = standaloneMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                if (value >= 2.5) {
                    candidatePrices.add(value)
                }
            }
            if (candidatePrices.isNotEmpty()) {
                price = candidatePrices.maxOrNull() ?: candidatePrices[0]
            }
        }

        // 3. Extrair Distâncias em km (Requisito 4: tolerar "3,2 km", "3.2km", e somar busca + viagem quando aparecerem duas)
        var totalDistanceKm = 0.0
        var pickupDistanceKm = 0.0
        var tripDistanceKm = 0.0

        // Menção direta a total
        val explicitTotalDistPattern = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*km(?:[\\s•·-]*\\d+\\s*min(?:utos?)?)?[\\s•·-]*(?:no\\s+)?total",
            Pattern.CASE_INSENSITIVE
        )
        val explicitDistMatcher = explicitTotalDistPattern.matcher(cleanText)
        if (explicitDistMatcher.find()) {
            totalDistanceKm = explicitDistMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        // Menção direta a busca / embarque
        val explicitPickupDistPattern = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*km(?:[\\s•·-]*\\d+\\s*min(?:utos?)?)?[\\s•·-]*(?:at[eé]\\s+o\\s+passageiro|embarque|busca)",
            Pattern.CASE_INSENSITIVE
        )
        val explicitPickupMatcher = explicitPickupDistPattern.matcher(cleanText)
        if (explicitPickupMatcher.find()) {
            pickupDistanceKm = explicitPickupMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        // Menção direta a viagem / destino
        val explicitTripDistPattern = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*km(?:[\\s•·-]*\\d+\\s*min(?:utos?)?)?[\\s•·-]*(?:viagem|destino)",
            Pattern.CASE_INSENSITIVE
        )
        val explicitTripMatcher = explicitTripDistPattern.matcher(cleanText)
        if (explicitTripMatcher.find()) {
            tripDistanceKm = explicitTripMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        // Coleta todas as distâncias com "km" (tolera "3,2 km", "3.2km", "3,2km", etc.)
        val distanceRegex = Pattern.compile("(?<!/\\s*)(\\d+(?:[.,]\\d+)?)\\s*(?:km|quil[oô]metros)", Pattern.CASE_INSENSITIVE)
        val distanceMatcher = distanceRegex.matcher(cleanText)
        val allDistances = mutableListOf<Double>()
        while (distanceMatcher.find()) {
            distanceMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull()?.let {
                if (it > 0.0) allDistances.add(it)
            }
        }

        if (totalDistanceKm <= 0.0) {
            if (pickupDistanceKm > 0.0 && tripDistanceKm > 0.0) {
                totalDistanceKm = ((pickupDistanceKm + tripDistanceKm) * 10).roundToInt() / 10.0
            } else if (allDistances.size >= 3) {
                // Na 99 são 3 valores: Busca (ex: 2.3), Viagem (ex: 5.5) e Total (ex: 7.8)
                totalDistanceKm = allDistances.maxOrNull() ?: 0.0
                val sorted = allDistances.sorted()
                pickupDistanceKm = sorted[0]
                tripDistanceKm = sorted[1]
            } else if (allDistances.size == 2) {
                // Requisito 4: somar busca + viagem quando aparecerem duas distâncias
                pickupDistanceKm = min(allDistances[0], allDistances[1])
                tripDistanceKm = max(allDistances[0], allDistances[1])
                totalDistanceKm = ((pickupDistanceKm + tripDistanceKm) * 10).roundToInt() / 10.0
            } else if (allDistances.size == 1) {
                totalDistanceKm = allDistances[0]
                pickupDistanceKm = (totalDistanceKm * 0.15 * 10).roundToInt() / 10.0
                tripDistanceKm = ((totalDistanceKm - pickupDistanceKm) * 10).roundToInt() / 10.0
            }
        } else {
            if (tripDistanceKm <= 0.0 && pickupDistanceKm > 0.0) {
                tripDistanceKm = max(0.1, ((totalDistanceKm - pickupDistanceKm) * 10).roundToInt() / 10.0)
            }
        }

        // 4. Extrair Tempo / Duração em min (Requisito 4: tolerar "8 min", "8min", "1h 05")
        var totalDurationMin = 0
        var pickupDurationMin = 0
        var tripDurationMin = 0

        // Menção explícita a total
        val explicitTotalMinPattern = Pattern.compile(
            "(\\d+)\\s*(?:min|minutos?)[\\s•·-]*(?:no\\s+)?total",
            Pattern.CASE_INSENSITIVE
        )
        val explicitMinMatcher = explicitTotalMinPattern.matcher(cleanText)
        if (explicitMinMatcher.find()) {
            totalDurationMin = explicitMinMatcher.group(1)?.toIntOrNull() ?: 0
        }

        // Menção explícita a tempo de busca / embarque
        val explicitPickupMinPattern = Pattern.compile(
            "(\\d+)\\s*(?:min|minutos?)[\\s•·-]*(?:at[eé]\\s+o\\s+passageiro|embarque|busca)",
            Pattern.CASE_INSENSITIVE
        )
        val explicitPickupMinMatcher = explicitPickupMinPattern.matcher(cleanText)
        if (explicitPickupMinMatcher.find()) {
            pickupDurationMin = explicitPickupMinMatcher.group(1)?.toIntOrNull() ?: 0
        }

        // Menção explícita a tempo de viagem
        val explicitTripMinPattern = Pattern.compile(
            "(\\d+)\\s*(?:min|minutos?)[\\s•·-]*(?:viagem|destino)",
            Pattern.CASE_INSENSITIVE
        )
        val explicitTripMinMatcher = explicitTripMinPattern.matcher(cleanText)
        if (explicitTripMinMatcher.find()) {
            tripDurationMin = explicitTripMinMatcher.group(1)?.toIntOrNull() ?: 0
        }

        // Tolerância para horas e minutos combinados (ex: "1h 05", "1h05", "1h 05 min", "1h")
        val hourPattern = Pattern.compile(
            "(\\d+)\\s*h(?:oras?)?(?:\\s*(\\d{1,2})(?:\\s*min)?)?",
            Pattern.CASE_INSENSITIVE
        )
        val hourMatcher = hourPattern.matcher(cleanText)
        var hoursInMinutes = 0
        while (hourMatcher.find()) {
            val hours = hourMatcher.group(1)?.toIntOrNull() ?: 0
            val extraMinutes = hourMatcher.group(2)?.toIntOrNull() ?: 0
            hoursInMinutes += (hours * 60) + extraMinutes
        }

        // Coleta todos os minutos presentes ("8 min", "8min", "8 m")
        val minPattern = Pattern.compile("(\\d+)\\s*(?:min|minutos?|m\\b)", Pattern.CASE_INSENSITIVE)
        val minMatcher = minPattern.matcher(cleanText)
        val allMinutes = mutableListOf<Int>()
        while (minMatcher.find()) {
            minMatcher.group(1)?.toIntOrNull()?.let {
                if (it > 0) allMinutes.add(it)
            }
        }

        if (totalDurationMin <= 0) {
            if (pickupDurationMin > 0 && tripDurationMin > 0) {
                totalDurationMin = pickupDurationMin + tripDurationMin + hoursInMinutes
            } else if (allMinutes.size >= 3) {
                totalDurationMin = (allMinutes.maxOrNull() ?: 0) + hoursInMinutes
            } else if (allMinutes.size == 2) {
                totalDurationMin = allMinutes.sum() + hoursInMinutes
            } else if (allMinutes.size == 1) {
                totalDurationMin = allMinutes[0] + hoursInMinutes
            } else if (hoursInMinutes > 0) {
                totalDurationMin = hoursInMinutes
            }
        } else {
            totalDurationMin += hoursInMinutes
        }

        // Requisito 5: Só considerar que é uma corrida quando houver valor em R$ E pelo menos uma distância em km E pelo menos um tempo
        if (price <= 0.0) {
            Log.d(TAG, "OcrParser: Requisito 5 não atendido — Preço não encontrado.")
            return null
        }

        if (totalDistanceKm <= 0.0 || totalDurationMin <= 0) {
            Log.d(TAG, "OcrParser: Requisito 5 não atendido — Preço=R$$price, Dist=${totalDistanceKm}km, Tempo=${totalDurationMin}min. Descartando falso positivo.")
            return null
        }

        Log.d(
            TAG,
            "OcrParser: Corrida confirmada! App=$app, Cat=$category, Preço=R$$price, Dist=${totalDistanceKm}km, Tempo=${totalDurationMin}min"
        )

        return RideData(
            id = "ocr-${System.currentTimeMillis()}",
            app = app,
            category = category,
            price = price,
            totalDistanceKm = totalDistanceKm,
            pickupDistanceKm = pickupDistanceKm,
            tripDistanceKm = tripDistanceKm,
            totalDurationMin = totalDurationMin,
            pickupDurationMin = if (pickupDurationMin > 0) pickupDurationMin else max(2, (totalDurationMin * 0.2).roundToInt()),
            tripDurationMin = if (tripDurationMin > 0) tripDurationMin else max(1, (totalDurationMin * 0.8).roundToInt()),
            pickupAddress = "Ponto de Embarque",
            destinationAddress = "Destino da Viagem",
            passengerRating = 4.90,
            timestamp = "Agora mesmo"
        )
    }
}
