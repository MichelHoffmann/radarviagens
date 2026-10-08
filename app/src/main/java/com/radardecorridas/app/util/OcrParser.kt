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

        // Normaliza quebras de linha e espaços não separáveis comuns no Android (ex: \u00A0 entre R$ e valor)
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

        // 2. Extrair Preço Principal
        // Na 99 Motorista:
        // O valor principal aparece como "R$ 10,32", "R$10,32" ou dentro do botão "Aceitar por R$10,32".
        // O badge lateral exibe "R$1,32/km" ou "R$ 1,32 / km" (que NÃO é o preço da corrida).
        var price = 0.0

        // Prioridade 1: Botão explícito da 99 "Aceitar por R$ 10,32" ou "Aceitar R$ 10,32"
        val acceptPricePattern = Pattern.compile(
            "aceitar\\s+(?:por\\s+)?r\\$\\s*(\\d{1,4}(?:[.,]\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )
        val acceptMatcher = acceptPricePattern.matcher(text)
        if (acceptMatcher.find()) {
            price = acceptMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        // Prioridade 2: Preço geral ignorando taxas por km (exclui sequências seguidas de /km)
        if (price <= 0.0) {
            // Regex que busca "R$ 10,32" garantindo que NÃO seja seguido por "/km"
            val priceGeneralPattern = Pattern.compile(
                "(?:r\\$\\s*|valor:?\\s*r?\\$?\\s*|ganha:?\\s*r?\\$?\\s*)(\\d{1,4}(?:[.,]\\d{1,2})?)(?!\\s*/\\s*km)",
                Pattern.CASE_INSENSITIVE
            )
            val priceMatcher = priceGeneralPattern.matcher(text)
            val candidatePrices = mutableListOf<Double>()
            while (priceMatcher.find()) {
                val value = priceMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                if (value > 2.0) { // corridas urbanas reais normalmente custam mais de R$ 2,50
                    candidatePrices.add(value)
                }
            }
            if (candidatePrices.isNotEmpty()) {
                // Se houver mais de um valor (ex: R$ 10,32 no cabeçalho e no botão), o valor total da corrida
                // é o maior valor absoluto entre os candidatos que não sejam taxas unitárias
                price = candidatePrices.maxOrNull() ?: candidatePrices[0]
            }
        }

        // 3. Extrair Distâncias
        // Padrão 99:
        // "2,3 km • 7 min até o passageiro"
        // "5,5 km • 8 min viagem"
        // "7,8 km • 15 min total"
        var totalDistanceKm = 0.0
        var pickupDistanceKm = 0.0
        var tripDistanceKm = 0.0

        // Procura menção direta a distância total na 99 ou Uber (ex: "7,8 km ... total", "7.8km total", "7,8 km no total")
        val explicitTotalDistPattern = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*km(?:[\\s•·-]*\\d+\\s*min(?:utos?)?)?[\\s•·-]*(?:no\\s+)?total",
            Pattern.CASE_INSENSITIVE
        )
        val explicitDistMatcher = explicitTotalDistPattern.matcher(text)
        if (explicitDistMatcher.find()) {
            totalDistanceKm = explicitDistMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        // Procura menção direta a distância até o passageiro / embarque (ex: "2,3 km ... até o passageiro")
        val explicitPickupDistPattern = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*km(?:[\\s•·-]*\\d+\\s*min(?:utos?)?)?[\\s•·-]*(?:at[eé]\\s+o\\s+passageiro|embarque|busca)",
            Pattern.CASE_INSENSITIVE
        )
        val explicitPickupMatcher = explicitPickupDistPattern.matcher(text)
        if (explicitPickupMatcher.find()) {
            pickupDistanceKm = explicitPickupMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        // Procura menção direta a distância de viagem (ex: "5,5 km ... viagem")
        val explicitTripDistPattern = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*km(?:[\\s•·-]*\\d+\\s*min(?:utos?)?)?[\\s•·-]*(?:viagem|destino)",
            Pattern.CASE_INSENSITIVE
        )
        val explicitTripMatcher = explicitTripDistPattern.matcher(text)
        if (explicitTripMatcher.find()) {
            tripDistanceKm = explicitTripMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        // Coleta todas as distâncias numéricas com "km" (ignorando se fizer parte de R$/km)
        val distanceRegex = Pattern.compile("(?<!/\\s*)(\\d+(?:[.,]\\d+)?)\\s*(?:km|quil[oô]metros)", Pattern.CASE_INSENSITIVE)
        val distanceMatcher = distanceRegex.matcher(text)
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
                // Na 99 são 3 valores: Embarque (2.3), Viagem (5.5) e Total (7.8)
                // O maior número é a distância total
                totalDistanceKm = allDistances.maxOrNull() ?: 0.0
                val sorted = allDistances.sorted()
                pickupDistanceKm = sorted[0]
                tripDistanceKm = sorted[1]
            } else if (allDistances.size == 2) {
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

        // 4. Extrair Duração / Tempo
        var totalDurationMin = 0
        var pickupDurationMin = 0
        var tripDurationMin = 0

        // Procura tempo explícito com "total" (ex: "15 min ... total" ou "15 min total")
        val explicitTotalMinPattern = Pattern.compile(
            "(\\d+)\\s*(?:min|minutos?)[\\s•·-]*(?:no\\s+)?total",
            Pattern.CASE_INSENSITIVE
        )
        val explicitMinMatcher = explicitTotalMinPattern.matcher(text)
        if (explicitMinMatcher.find()) {
            totalDurationMin = explicitMinMatcher.group(1)?.toIntOrNull() ?: 0
        }

        // Procura tempo até o passageiro
        val explicitPickupMinPattern = Pattern.compile(
            "(\\d+)\\s*(?:min|minutos?)[\\s•·-]*(?:at[eé]\\s+o\\s+passageiro|embarque|busca)",
            Pattern.CASE_INSENSITIVE
        )
        val explicitPickupMinMatcher = explicitPickupMinPattern.matcher(text)
        if (explicitPickupMinMatcher.find()) {
            pickupDurationMin = explicitPickupMinMatcher.group(1)?.toIntOrNull() ?: 0
        }

        // Procura tempo de viagem
        val explicitTripMinPattern = Pattern.compile(
            "(\\d+)\\s*(?:min|minutos?)[\\s•·-]*(?:viagem|destino)",
            Pattern.CASE_INSENSITIVE
        )
        val explicitTripMinMatcher = explicitTripMinPattern.matcher(text)
        if (explicitTripMinMatcher.find()) {
            tripDurationMin = explicitTripMinMatcher.group(1)?.toIntOrNull() ?: 0
        }

        // Extrai horas caso existam (ex: 1 h 15 min)
        val hourPattern = Pattern.compile("(\\d+)\\s*h(?:oras?)?", Pattern.CASE_INSENSITIVE)
        val hourMatcher = hourPattern.matcher(text)
        var hoursInMinutes = 0
        if (hourMatcher.find()) {
            hoursInMinutes = (hourMatcher.group(1)?.toIntOrNull() ?: 0) * 60
        }

        // Coleta todos os minutos presentes
        val minPattern = Pattern.compile("(\\d+)\\s*(?:min|minutos?|m\\b)", Pattern.CASE_INSENSITIVE)
        val minMatcher = minPattern.matcher(text)
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
                // Na 99: 7 min (embarque), 8 min (viagem), 15 min (total)
                totalDurationMin = (allMinutes.maxOrNull() ?: 0) + hoursInMinutes
            } else if (allMinutes.size == 2) {
                totalDurationMin = allMinutes.sum() + hoursInMinutes
            } else if (allMinutes.size == 1) {
                totalDurationMin = allMinutes[0] + hoursInMinutes
            }
        } else {
            totalDurationMin += hoursInMinutes
        }

        // Validação estrita
        if (price <= 0.0) {
            Log.d(TAG, "OcrParser: Nenhum preço válido detectado.")
            return null
        }

        if (totalDistanceKm <= 0.0 && totalDurationMin <= 0) {
            Log.d(TAG, "OcrParser: Preço R$$price detectado, mas sem distância ou tempo. Descartando.")
            return null
        }

        if (totalDurationMin <= 0 && totalDistanceKm > 0.0) {
            totalDurationMin = max(5, (totalDistanceKm * 2.2).roundToInt())
        }
        if (totalDistanceKm <= 0.0 && totalDurationMin > 0) {
            totalDistanceKm = max(1.0, ((totalDurationMin / 2.2) * 10).roundToInt() / 10.0)
        }

        Log.d(
            TAG,
            "OcrParser: CORRIDA IDENTIFICADA! App=$app, Cat=$category, Preço=R$$price, Dist=${totalDistanceKm}km (busca: ${pickupDistanceKm}km, viagem: ${tripDistanceKm}km), Tempo=${totalDurationMin}min"
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
            pickupDurationMin = if (pickupDurationMin > 0) pickupDurationMin else max(2, (totalDurationMin * 0.2).roundToInt()),
            tripDurationMin = if (tripDurationMin > 0) tripDurationMin else max(1, (totalDurationMin * 0.8).roundToInt()),
            pickupAddress = "Ponto de Embarque",
            destinationAddress = "Destino da Viagem",
            passengerRating = 4.90,
            timestamp = "Agora mesmo"
        )
    }
}
