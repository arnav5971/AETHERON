package com.aetheron.browser

/**
 * Conservative catalogue filters. A service is included only when its own
 * catalogue text explicitly supports the selected claim; missing evidence
 * never gets interpreted as a positive match.
 */
internal object ResourceFilterRules {
    fun matchesQuickFilter(
        filterId: String,
        categoryName: String,
        serviceName: String,
        description: String,
        freeTierNotes: String
    ): Boolean {
        if (filterId == "all") return true
        // Eligibility claims are checked against source-derived free-tier notes only.
        val evidence = freeTierNotes.lowercase()
        val notes = evidence
        return when (filterId) {
            "no_card" -> listOf(
                "no credit card", "no card required", "without a credit card",
                "without credit card", "credit card not required", "no payment method"
            ).any(evidence::contains)
            "no_signup" -> listOf(
                "no signup", "no sign-up", "without signup", "without sign-up",
                "no registration", "without registration", "no account required",
                "without creating an account"
            ).any(evidence::contains)
            "always_free" -> listOf(
                "always free", "free forever", "forever free", "permanently free",
                "permanent free", "free for life"
            ).any(evidence::contains)
            "limits" -> notes.isNotBlank() && (
                Regex("[0-9]").containsMatchIn(notes) ||
                    listOf("limit", "quota", "per month", "per day", "per week", "requests", "storage", "bandwidth", "credits", "hours", "users", "build minutes")
                        .any(notes::contains)
                )
            else -> true
        }
    }

    fun matchesTypeFilter(
        filterId: String,
        categoryName: String,
        serviceName: String,
        description: String,
        url: String
    ): Boolean {
        if (filterId == "all") return true
        val category = categoryName.lowercase()
        val service = "$serviceName $description $url".lowercase()
        return when (filterId) {
            "cloud" -> category.contains("cloud") || category.contains("iaas") ||
                service.contains("cloud computing") || service.contains("cloud hosting")
            "ai" -> category.contains("generative ai") || category.contains("apis, data & ml") ||
                Regex("(^|[^a-z])(ai|ml|llm|gpt|gemini)([^a-z]|$)").containsMatchIn(service) ||
                listOf("machine learning", "artificial intelligence", "model inference").any(service::contains)
            "api" -> category.contains("apis") || category.contains("api") ||
                Regex("(^|[^a-z])api([^a-z]|$)").containsMatchIn(service) || service.contains("/api")
            "hosting" -> category.contains("web hosting") || category.contains("paas") ||
                category.contains("iaas") || category.contains("baas") ||
                service.contains("web hosting") || service.contains("static hosting")
            else -> true
        }
    }
}
