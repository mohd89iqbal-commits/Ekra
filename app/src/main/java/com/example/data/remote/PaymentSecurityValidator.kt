package com.example.data.remote

import java.util.Calendar

data class ValidatedCardToken(
    val brand: String,
    val last4: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val holderName: String
)

object PaymentSecurityValidator {

    fun detectCardBrand(digitsOnly: String): String = when {
        digitsOnly.startsWith("4") -> "Visa"
        digitsOnly.startsWith("5") || digitsOnly.startsWith("2") -> "Mastercard"
        digitsOnly.startsWith("34") || digitsOnly.startsWith("37") -> "Amex"
        digitsOnly.startsWith("6") -> "Discover"
        else -> "Card"
    }

    fun isValidLuhn(rawNumber: String): Boolean {
        val digits = rawNumber.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        var doubleDigit = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i].digitToInt()
            if (doubleDigit) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            doubleDigit = !doubleDigit
        }
        return sum % 10 == 0
    }

    fun validateAndTokenizeCard(
        rawNumber: String,
        expiryMmYy: String,
        cvv: String,
        holderName: String
    ): Result<ValidatedCardToken> {
        val digits = rawNumber.filter { it.isDigit() }
        if (!isValidLuhn(digits)) {
            return Result.failure(IllegalArgumentException("Enter a valid card number (Luhn check failed)."))
        }
        val cleanHolder = holderName.trim()
        if (cleanHolder.length !in 2..60) {
            return Result.failure(IllegalArgumentException("Enter cardholder name (2-60 characters)."))
        }
        val cleanExpiry = expiryMmYy.filter { it.isDigit() }
        if (cleanExpiry.length != 4) {
            return Result.failure(IllegalArgumentException("Enter expiry as MM/YY."))
        }
        val month = cleanExpiry.substring(0, 2).toIntOrNull() ?: 0
        val yearTwoDigits = cleanExpiry.substring(2, 4).toIntOrNull() ?: 0
        val fullYear = 2000 + yearTwoDigits
        if (month !in 1..12) {
            return Result.failure(IllegalArgumentException("Expiry month must be 01-12."))
        }
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1
        if (fullYear < currentYear || (fullYear == currentYear && month < currentMonth) || fullYear > 2045) {
            return Result.failure(IllegalArgumentException("Card is expired or expiry year is invalid."))
        }
        val brand = detectCardBrand(digits)
        val expectedCvvLength = if (brand == "Amex") 4 else 3
        val cleanCvv = cvv.filter { it.isDigit() }
        if (cleanCvv.length != expectedCvvLength) {
            return Result.failure(IllegalArgumentException("Enter a valid $expectedCvvLength-digit security code (CVV)."))
        }

        // Tokenize immediately: never retain raw card PAN or CVV beyond validation
        return Result.success(
            ValidatedCardToken(
                brand = brand,
                last4 = digits.takeLast(4),
                expiryMonth = month,
                expiryYear = fullYear,
                holderName = cleanHolder
            )
        )
    }

    fun validateUpiHandle(handle: String, holderName: String): Result<Pair<String, String>> {
        val cleanHandle = handle.trim().lowercase()
        val cleanHolder = holderName.trim()
        if (!cleanHandle.matches(Regex("^[a-z0-9._\\-]{2,32}@[a-z]{2,16}$"))) {
            return Result.failure(IllegalArgumentException("Enter a valid UPI ID (e.g. name@okaxis)."))
        }
        if (cleanHolder.length !in 2..60) {
            return Result.failure(IllegalArgumentException("Enter account holder name (2-60 characters)."))
        }
        return Result.success(cleanHandle to cleanHolder)
    }
}
