package com.sahil.callshield.data

/**
 * OWASP MASVS & Common Android Vulnerabilities Hardening:
 * 
 * 1. CWE-1333: ReDoS Protection (Telecom Binder ANR Prevention)
 *    Telecom framework kills screening services exceeding latency thresholds.
 *    Catastrophic regex backtracking from malicious patterns can cause ANRs.
 *    Fix: Strict 32-char bounds and linear O(N) prefix comparison.
 * 
 * 2. CWE-89: SQL Injection Prevention in Room
 *    Fix: Avoid rawQuery. Use compile-time verified @Query with bound parameters.
 * 
 * 3. Linear Prefix Lookup: High-performance O(1) to O(k) evaluation.
 */
object SecurityMatcher {

    fun sanitizeNumber(raw: String): String {
        val trimmed = raw.trim().take(32)
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }.take(25)
        return if (hasPlus) "+$digits" else digits
    }

    /**
     * Extracts national significant digits for resilient telephony matching.
     * Matches across local (e.g. 9876543210), national (09876543210), 
     * and international (+919876543210) formats.
     */
    fun isExactTelephonyMatch(incomingNumber: String, patternNumber: String): Boolean {
        val norm1 = sanitizeNumber(incomingNumber)
        val norm2 = sanitizeNumber(patternNumber)

        // 1. Direct string equality (e.g. "+919876543210" == "+919876543210")
        if (norm1.isNotEmpty() && norm1.equals(norm2, ignoreCase = true)) {
            return true
        }

        val digits1 = norm1.filter { it.isDigit() }
        val digits2 = norm2.filter { it.isDigit() }

        if (digits1.isEmpty() || digits2.isEmpty()) {
            return false
        }

        // 2. Strict digits equality
        if (digits1 == digits2) {
            return true
        }

        // 3. National significant number matching (Last 10 digits or suffix match)
        // Standard phone plans use 7-10 subscriber digits. E.g. "+919876543210" matches "9876543210"
        val minLen = minOf(digits1.length, digits2.length)
        if (minLen >= 7) {
            if (digits1.endsWith(digits2) || digits2.endsWith(digits1)) {
                return true
            }
            if (digits1.length >= 10 && digits2.length >= 10) {
                if (digits1.takeLast(10) == digits2.takeLast(10)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Resilient prefix matching across international (+91140909), national (0140909),
     * and local subscriber (140909) telephony formats.
     */
    fun isPrefixMatch(incomingDigits: String, ruleDigits: String): Boolean {
        if (incomingDigits.isEmpty() || ruleDigits.isEmpty()) return false

        // 1. Direct prefix match (e.g. "911409099615".startsWith("91140909"))
        if (incomingDigits.startsWith(ruleDigits)) {
            return true
        }

        // 2. Trunk '0' variations (e.g. incoming "01409099615" vs rule "140909", or vice versa)
        if (incomingDigits.startsWith("0") && incomingDigits.drop(1).startsWith(ruleDigits)) {
            return true
        }
        if (ruleDigits.startsWith("0") && incomingDigits.startsWith(ruleDigits.drop(1))) {
            return true
        }

        // 3. Rule has international dial code (e.g. +91 140909 -> digits "91140909"),
        // but carrier delivers incoming number in local format (e.g. "1409099615" or "01409099615")
        for (ccLen in 1..4) {
            if (ruleDigits.length > ccLen) {
                val subscriberPrefix = ruleDigits.drop(ccLen)
                if (subscriberPrefix.length >= 3) {
                    if (incomingDigits.startsWith(subscriberPrefix)) return true
                    if (incomingDigits.startsWith("0") && incomingDigits.drop(1).startsWith(subscriberPrefix)) return true
                }
            }
        }

        // 4. Rule is local prefix (e.g. "140909"),
        // but incoming call arrives with international country code (e.g. "+911409099615" -> "911409099615")
        for (ccLen in 1..4) {
            if (incomingDigits.length > ccLen + ruleDigits.length) {
                val nationalIncoming = incomingDigits.drop(ccLen)
                if (nationalIncoming.startsWith(ruleDigits)) return true
                if (nationalIncoming.startsWith("0") && nationalIncoming.drop(1).startsWith(ruleDigits)) return true
            }
        }

        return false
    }

    fun findMatchingRule(rawNumber: String, activeRules: List<BlockRuleEntity>): BlockRuleEntity? {
        val normalized = sanitizeNumber(rawNumber)
        val digitsOnly = normalized.filter { it.isDigit() }

        if (digitsOnly.isEmpty()) return null

        // 1. Exact match check (Handles international, national, and local formats)
        for (rule in activeRules) {
            if (rule.type == "exact" || isExactTelephonyMatch(rawNumber, rule.pattern)) {
                if (isExactTelephonyMatch(rawNumber, rule.pattern)) {
                    return rule
                }
            }
        }

        // 2. High-performance Prefix & Country code matching
        for (rule in activeRules) {
            val cleanRuleDigits = rule.pattern.filter { it.isDigit() }
            if (cleanRuleDigits.isEmpty()) continue

            // Standard prefix or country type
            if (rule.type == "prefix" || rule.type == "country") {
                if (isPrefixMatch(digitsOnly, cleanRuleDigits)) {
                    return rule
                }
            }

            // Fallback for rules marked as "exact" from earlier versions where pattern is shorter than incoming number
            if (rule.type == "exact" && cleanRuleDigits.length < digitsOnly.length) {
                if (isPrefixMatch(digitsOnly, cleanRuleDigits)) {
                    return rule
                }
            }
        }

        // 3. Bounded wildcard check (pattern length bounded to <= 32)
        for (rule in activeRules) {
            if (rule.type == "wildcard" || rule.pattern.contains("*") || rule.pattern.contains("?")) {
                if (safeWildcardMatch(rule.pattern, normalized) || safeWildcardMatch(rule.pattern, digitsOnly)) {
                    return rule
                }
            }
        }

        return null
    }

    private fun safeWildcardMatch(pattern: String, number: String): Boolean {
        if (pattern.length > 32 || number.length > 32) return false
        val regexPattern = "^" + Regex.escape(pattern)
            .replace(Regex.escape("*"), "[0-9]*")
            .replace(Regex.escape("?"), "[0-9]") + "$"
        return try {
            Regex(regexPattern).matches(number)
        } catch (e: Exception) {
            false
        }
    }
}
