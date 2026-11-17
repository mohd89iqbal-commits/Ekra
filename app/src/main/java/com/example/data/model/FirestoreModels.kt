package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class PaymentMethodType(val label: String) {
    CARD("Credit / Debit Card"),
    UPI("UPI Instant Pay"),
    WALLET("BitePay Express")
}

enum class PaymentStatus {
    PAID,
    REFUNDED
}

enum class OrderStatus(
    val stepIndex: Int,
    val title: String,
    val subtitle: String
) {
    PLACED(0, "Order Placed", "Payment verified & sent to kitchen"),
    CONFIRMED(1, "Kitchen Confirmed", "Chef has accepted your ticket"),
    PREPARING(2, "Cooking Fresh", "Your meal is being prepared now"),
    OUT_FOR_DELIVERY(3, "Out for Delivery", "Courier is heading to your address"),
    DELIVERED(4, "Delivered", "Enjoy your meal!"),
    CANCELLED(-1, "Cancelled", "Order cancelled & payment refunded");

    val isTerminal: Boolean get() = this == DELIVERED || this == CANCELLED
    val isActive: Boolean get() = !isTerminal

    fun nextProgression(): Pair<OrderStatus, Int>? = when (this) {
        PLACED -> CONFIRMED to 22
        CONFIRMED -> PREPARING to 15
        PREPARING -> OUT_FOR_DELIVERY to 7
        OUT_FOR_DELIVERY -> DELIVERED to 0
        DELIVERED, CANCELLED -> null
    }

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.name == raw } ?: PLACED
    }
}

@Keep
@IgnoreExtraProperties
data class SavedAddress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val street: String = "",
    val apartment: String? = null,
    val city: String = "",
    val instructions: String? = null,
    val defaultAddress: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun formattedLine(): String = buildString {
        append(street.trim())
        if (!apartment.isNullOrBlank()) {
            append(", ")
            append(apartment.trim())
        }
        if (city.isNotBlank()) {
            append(", ")
            append(city.trim())
        }
    }
}

@Keep
@IgnoreExtraProperties
data class TokenizedPaymentMethod(
    @DocumentId val id: String = "",
    val userId: String = "",
    val methodType: String = PaymentMethodType.CARD.name,
    val brand: String = "",
    val last4OrHandle: String = "",
    val holderName: String = "",
    val expiryMonth: Long? = null,
    val expiryYear: Long? = null,
    val defaultMethod: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun parsedType(): PaymentMethodType =
        PaymentMethodType.entries.firstOrNull { it.name == methodType } ?: PaymentMethodType.CARD

    fun displaySummary(): String = when (parsedType()) {
        PaymentMethodType.CARD -> "$brand •••• $last4OrHandle"
        PaymentMethodType.UPI -> "UPI • $last4OrHandle"
        PaymentMethodType.WALLET -> "$brand • $last4OrHandle"
    }
}

@Keep
@IgnoreExtraProperties
data class FavoriteRestaurant(
    @DocumentId val id: String = "",
    val userId: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val cuisine: String = "",
    val rating: Double = 0.0,
    val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val dishId: String = "",
    val name: String = "",
    val quantity: Long = 1L,
    val unitPriceCents: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "dishId" to dishId,
        "name" to name,
        "quantity" to quantity,
        "unitPriceCents" to unitPriceCents
    )
}

@Keep
@IgnoreExtraProperties
data class OrderRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val restaurantCuisine: String = "",
    val items: List<OrderLineItem> = emptyList(),
    val deliveryAddressLabel: String = "",
    val deliveryAddressLine: String = "",
    val deliveryInstructions: String? = null,
    val paymentMethodSummary: String = "",
    val paymentStatus: String = PaymentStatus.PAID.name,
    val transactionRef: String = "",
    val promoCode: String? = null,
    val subtotalCents: Long = 0L,
    val deliveryFeeCents: Long = 0L,
    val taxCents: Long = 0L,
    val discountCents: Long = 0L,
    val totalCents: Long = 0L,
    val status: String = OrderStatus.PLACED.name,
    val etaMinutes: Long = 26L,
    val courierName: String = "",
    val courierRating: Double = 4.9,
    val courierVehicle: String = "",
    val userRating: Long? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun parsedStatus(): OrderStatus = OrderStatus.fromRaw(status)
    fun parsedPaymentStatus(): PaymentStatus =
        PaymentStatus.entries.firstOrNull { it.name == paymentStatus } ?: PaymentStatus.PAID
}
