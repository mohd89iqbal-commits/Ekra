package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.FavoriteRestaurant
import com.example.data.model.OrderLineItem
import com.example.data.model.OrderRecord
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethodType
import com.example.data.model.PaymentStatus
import com.example.data.model.Restaurant
import com.example.data.model.SavedAddress
import com.example.data.model.TokenizedPaymentMethod
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class BiteLocalRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireVerifiedUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        return uid
    }

    fun observeOrders(userId: String): Flow<List<OrderRecord>> = flow {
        val uid = requireVerifiedUserId(userId)
        emitAll(
            db.collection("users").document(uid).collection("orders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        OrderRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    fun observeOrder(userId: String, orderId: String): Flow<OrderRecord?> = flow {
        val uid = requireVerifiedUserId(userId)
        emitAll(
            db.collection("users").document(uid).collection("orders").document(orderId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        OrderRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    fun observeAddresses(userId: String): Flow<List<SavedAddress>> = flow {
        val uid = requireVerifiedUserId(userId)
        emitAll(
            db.collection("users").document(uid).collection("addresses")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedAddress::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    fun observePaymentMethods(userId: String): Flow<List<TokenizedPaymentMethod>> = flow {
        val uid = requireVerifiedUserId(userId)
        emitAll(
            db.collection("users").document(uid).collection("payment_methods")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TokenizedPaymentMethod::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    fun observeFavorites(userId: String): Flow<List<FavoriteRestaurant>> = flow {
        val uid = requireVerifiedUserId(userId)
        emitAll(
            db.collection("users").document(uid).collection("favorites")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FavoriteRestaurant::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    suspend fun saveAddress(
        userId: String,
        label: String,
        street: String,
        apartment: String?,
        city: String,
        instructions: String?,
        defaultAddress: Boolean
    ): Result<String> {
        var docPath = "users/$userId/addresses"
        return try {
            val uid = requireVerifiedUserId(userId)
            val cleanLabel = label.trim()
            val cleanStreet = street.trim()
            val cleanApt = apartment?.trim()?.takeIf { it.isNotEmpty() }
            val cleanCity = city.trim()
            val cleanInstructions = instructions?.trim()?.takeIf { it.isNotEmpty() }

            require(cleanLabel.length in 1..30) { "Label must be 1 to 30 characters." }
            require(cleanStreet.length in 3..120) { "Street address must be 3 to 120 characters." }
            require(cleanCity.length in 2..60) { "City must be 2 to 60 characters." }
            if (cleanApt != null) require(cleanApt.length <= 60) { "Apartment must be at most 60 characters." }
            if (cleanInstructions != null) require(cleanInstructions.length <= 160) { "Instructions must be at most 160 characters." }

            val colRef = db.collection("users").document(uid).collection("addresses")
            val docRef = colRef.document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "label" to cleanLabel,
                "street" to cleanStreet,
                "apartment" to cleanApt,
                "city" to cleanCity,
                "instructions" to cleanInstructions,
                "defaultAddress" to defaultAddress,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setDefaultAddress(userId: String, addressId: String, allAddressIds: List<String>): Result<Unit> {
        var docPath = "users/$userId/addresses/$addressId"
        return try {
            val uid = requireVerifiedUserId(userId)
            val colRef = db.collection("users").document(uid).collection("addresses")
            val batch = db.batch()
            for (id in allAddressIds) {
                batch.update(
                    colRef.document(id),
                    mapOf(
                        "defaultAddress" to (id == addressId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAddress(userId: String, addressId: String): Result<Unit> {
        val docPath = "users/$userId/addresses/$addressId"
        return try {
            val uid = requireVerifiedUserId(userId)
            require(addressId.isNotBlank()) { "Address ID required." }
            db.collection("users").document(uid).collection("addresses").document(addressId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveTokenizedPaymentMethod(
        userId: String,
        methodType: PaymentMethodType,
        brand: String,
        last4OrHandle: String,
        holderName: String,
        expiryMonth: Int? = null,
        expiryYear: Int? = null,
        defaultMethod: Boolean = true
    ): Result<String> {
        var docPath = "users/$userId/payment_methods"
        return try {
            val uid = requireVerifiedUserId(userId)
            val cleanBrand = brand.trim()
            val cleanLast4 = last4OrHandle.trim()
            val cleanHolder = holderName.trim()

            require(cleanBrand.length in 2..30) { "Invalid payment brand." }
            require(cleanLast4.length in 4..50) { "Invalid payment identifier." }
            require(cleanHolder.length in 2..60) { "Holder name must be 2-60 characters." }

            val docRef = db.collection("users").document(uid).collection("payment_methods").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "methodType" to methodType.name,
                "brand" to cleanBrand,
                "last4OrHandle" to cleanLast4,
                "holderName" to cleanHolder,
                "expiryMonth" to expiryMonth?.toLong(),
                "expiryYear" to expiryYear?.toLong(),
                "defaultMethod" to defaultMethod,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePaymentMethod(userId: String, methodId: String): Result<Unit> {
        val docPath = "users/$userId/payment_methods/$methodId"
        return try {
            val uid = requireVerifiedUserId(userId)
            require(methodId.isNotBlank()) { "Payment method ID required." }
            db.collection("users").document(uid).collection("payment_methods").document(methodId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(
        userId: String,
        restaurant: Restaurant,
        currentlyFavorited: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/favorites/${restaurant.id}"
        return try {
            val uid = requireVerifiedUserId(userId)
            val docRef = db.collection("users").document(uid).collection("favorites").document(restaurant.id)
            if (currentlyFavorited) {
                docRef.delete().await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "restaurantId" to restaurant.id,
                    "restaurantName" to restaurant.name.take(80),
                    "cuisine" to restaurant.cuisine.take(60),
                    "rating" to restaurant.rating,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun placeOrder(
        userId: String,
        restaurant: Restaurant,
        cartItems: List<CartItem>,
        deliveryAddressLabel: String,
        deliveryAddressLine: String,
        deliveryInstructions: String?,
        paymentMethodSummary: String,
        promoCode: String?,
        subtotalCents: Int,
        deliveryFeeCents: Int,
        taxCents: Int,
        discountCents: Int,
        totalCents: Int
    ): Result<String> {
        var docPath = "users/$userId/orders"
        return try {
            val uid = requireVerifiedUserId(userId)
            require(cartItems.isNotEmpty()) { "Cart cannot be empty." }
            require(cartItems.size <= 30) { "Maximum 30 line items per order." }
            require(totalCents >= 1) { "Order total must be positive." }

            val docRef = db.collection("users").document(uid).collection("orders").document()
            docPath = docRef.path

            val courierPool = listOf(
                Triple("Marcus Vance", 4.9, "E-Bike • BL-402"),
                Triple("Elena Rostova", 4.8, "Scooter • BL-118"),
                Triple("Devon Patel", 5.0, "E-Bike • BL-774")
            )
            val assignedCourier = courierPool[Math.abs(restaurant.id.hashCode()) % courierPool.size]
            val txRef = "TXN-" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()

            val orderItems = cartItems.map { item ->
                OrderLineItem(
                    dishId = item.dish.id,
                    name = item.dish.name,
                    quantity = item.quantity.toLong(),
                    unitPriceCents = item.dish.priceCents.toLong()
                ).toMap()
            }

            val cleanInstructions = deliveryInstructions?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
            val cleanPromo = promoCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.take(24)

            val payload = mapOf(
                "userId" to uid,
                "restaurantId" to restaurant.id,
                "restaurantName" to restaurant.name.take(80),
                "restaurantCuisine" to restaurant.cuisine.take(60),
                "items" to orderItems,
                "deliveryAddressLabel" to deliveryAddressLabel.trim().take(40).ifBlank { "Delivery" },
                "deliveryAddressLine" to deliveryAddressLine.trim().take(200),
                "deliveryInstructions" to cleanInstructions,
                "paymentMethodSummary" to paymentMethodSummary.trim().take(80),
                "paymentStatus" to PaymentStatus.PAID.name,
                "transactionRef" to txRef,
                "promoCode" to cleanPromo,
                "subtotalCents" to subtotalCents.toLong(),
                "deliveryFeeCents" to deliveryFeeCents.toLong(),
                "taxCents" to taxCents.toLong(),
                "discountCents" to discountCents.toLong(),
                "totalCents" to totalCents.toLong(),
                "status" to OrderStatus.PLACED.name,
                "etaMinutes" to restaurant.deliveryTimeMax.toLong(),
                "courierName" to assignedCourier.first,
                "courierRating" to assignedCourier.second,
                "courierVehicle" to assignedCourier.third,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun advanceOrderStatus(
        userId: String,
        orderId: String,
        nextStatus: OrderStatus,
        nextEtaMinutes: Int
    ): Result<Unit> {
        val docPath = "users/$userId/orders/$orderId"
        return try {
            val uid = requireVerifiedUserId(userId)
            require(orderId.isNotBlank()) { "Order ID required." }
            db.collection("users").document(uid).collection("orders").document(orderId)
                .update(
                    mapOf(
                        "status" to nextStatus.name,
                        "etaMinutes" to nextEtaMinutes.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(userId: String, orderId: String): Result<Unit> {
        val docPath = "users/$userId/orders/$orderId"
        return try {
            val uid = requireVerifiedUserId(userId)
            require(orderId.isNotBlank()) { "Order ID required." }
            db.collection("users").document(uid).collection("orders").document(orderId)
                .update(
                    mapOf(
                        "status" to OrderStatus.CANCELLED.name,
                        "paymentStatus" to PaymentStatus.REFUNDED.name,
                        "etaMinutes" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun rateOrder(userId: String, orderId: String, rating: Int): Result<Unit> {
        val docPath = "users/$userId/orders/$orderId"
        return try {
            val uid = requireVerifiedUserId(userId)
            require(orderId.isNotBlank()) { "Order ID required." }
            require(rating in 1..5) { "Rating must be between 1 and 5." }
            db.collection("users").document(uid).collection("orders").document(orderId)
                .update(
                    mapOf(
                        "userRating" to rating.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
