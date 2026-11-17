package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartItem
import com.example.data.model.CartState
import com.example.data.model.CuisineCategory
import com.example.data.model.FavoriteRestaurant
import com.example.data.model.LocalRestaurantCatalog
import com.example.data.model.MenuItem
import com.example.data.model.OrderRecord
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethodType
import com.example.data.model.Restaurant
import com.example.data.model.SavedAddress
import com.example.data.model.TokenizedPaymentMethod
import com.example.data.model.UiState
import com.example.data.remote.BiteLocalRepository
import com.example.data.remote.PaymentSecurityValidator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExploreFilterState(
    val searchQuery: String = "",
    val selectedCategory: CuisineCategory = CuisineCategory.ALL,
    val pureVegOnly: Boolean = false,
    val fastDeliveryOnly: Boolean = false,
    val topRatedOnly: Boolean = false
)

data class CheckoutPricing(
    val subtotalCents: Int,
    val deliveryFeeCents: Int,
    val taxCents: Int,
    val discountCents: Int,
    val totalCents: Int,
    val appliedPromo: String?
)

class BiteLocalViewModel(
    private val repository: BiteLocalRepository,
    val currentUserId: String
) : ViewModel() {

    private val _exploreFilter = MutableStateFlow(ExploreFilterState())
    val exploreFilter: StateFlow<ExploreFilterState> = _exploreFilter.asStateFlow()

    private val _cart = MutableStateFlow(CartState())
    val cart: StateFlow<CartState> = _cart.asStateFlow()

    private val _pendingRestaurantSwitch = MutableStateFlow<Pair<Restaurant, MenuItem>?>(null)
    val pendingRestaurantSwitch: StateFlow<Pair<Restaurant, MenuItem>?> = _pendingRestaurantSwitch.asStateFlow()

    private val _promoCode = MutableStateFlow<String?>(null)
    val promoCode: StateFlow<String?> = _promoCode.asStateFlow()

    private val _selectedAddressId = MutableStateFlow<String?>(null)
    val selectedAddressId: StateFlow<String?> = _selectedAddressId.asStateFlow()

    private val _selectedPaymentMethodKey = MutableStateFlow<String?>(null)
    val selectedPaymentMethodKey: StateFlow<String?> = _selectedPaymentMethodKey.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _isSubmittingOrder = MutableStateFlow(false)
    val isSubmittingOrder: StateFlow<Boolean> = _isSubmittingOrder.asStateFlow()

    private var liveTrackingJob: Job? = null

    val ordersState: StateFlow<UiState<List<OrderRecord>>> = repository.observeOrders(currentUserId)
        .map<List<OrderRecord>, UiState<List<OrderRecord>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing orders", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load orders"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val addressesState: StateFlow<UiState<List<SavedAddress>>> = repository.observeAddresses(currentUserId)
        .map<List<SavedAddress>, UiState<List<SavedAddress>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing addresses", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load addresses"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val paymentMethodsState: StateFlow<UiState<List<TokenizedPaymentMethod>>> =
        repository.observePaymentMethods(currentUserId)
            .map<List<TokenizedPaymentMethod>, UiState<List<TokenizedPaymentMethod>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing payment methods", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load payment methods"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val favoriteIdsState: StateFlow<Set<String>> = repository.observeFavorites(currentUserId)
        .map { list -> list.map { it.restaurantId }.toSet() }
        .catch { error ->
            val firestoreError = unwrapFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing favorites", firestoreError ?: error)
            emit(emptySet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val filteredRestaurants: StateFlow<List<Restaurant>> = _exploreFilter
        .map { filter ->
            LocalRestaurantCatalog.restaurants.filter { r ->
                val matchesSearch = filter.searchQuery.isBlank() ||
                    r.name.contains(filter.searchQuery, ignoreCase = true) ||
                    r.cuisine.contains(filter.searchQuery, ignoreCase = true) ||
                    r.neighborhood.contains(filter.searchQuery, ignoreCase = true) ||
                    r.menu.any { it.name.contains(filter.searchQuery, ignoreCase = true) }
                val matchesCategory = filter.selectedCategory == CuisineCategory.ALL ||
                    r.category == filter.selectedCategory
                val matchesVeg = !filter.pureVegOnly || r.pureVeg
                val matchesFast = !filter.fastDeliveryOnly || r.deliveryTimeMax <= 26
                val matchesTop = !filter.topRatedOnly || r.rating >= 4.7
                matchesSearch && matchesCategory && matchesVeg && matchesFast && matchesTop
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LocalRestaurantCatalog.restaurants
        )

    val checkoutPricing: StateFlow<CheckoutPricing> = combine(_cart, _promoCode) { cart, promo ->
        calculatePricing(cart, promo)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CheckoutPricing(0, 0, 0, 0, 0, null)
    )

    fun observeOrderById(orderId: String): StateFlow<UiState<OrderRecord?>> =
        repository.observeOrder(currentUserId, orderId)
            .map<OrderRecord?, UiState<OrderRecord?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to track order"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    fun updateSearchQuery(query: String) {
        _exploreFilter.value = _exploreFilter.value.copy(searchQuery = query)
    }

    fun selectCategory(category: CuisineCategory) {
        _exploreFilter.value = _exploreFilter.value.copy(selectedCategory = category)
    }

    fun togglePureVegFilter() {
        _exploreFilter.value = _exploreFilter.value.copy(pureVegOnly = !_exploreFilter.value.pureVegOnly)
    }

    fun toggleFastDeliveryFilter() {
        _exploreFilter.value = _exploreFilter.value.copy(fastDeliveryOnly = !_exploreFilter.value.fastDeliveryOnly)
    }

    fun toggleTopRatedFilter() {
        _exploreFilter.value = _exploreFilter.value.copy(topRatedOnly = !_exploreFilter.value.topRatedOnly)
    }

    fun clearExploreFilters() {
        _exploreFilter.value = ExploreFilterState()
    }

    fun addDishToCart(restaurant: Restaurant, dish: MenuItem) {
        val current = _cart.value
        if (current.restaurant != null && current.restaurant.id != restaurant.id && current.items.isNotEmpty()) {
            _pendingRestaurantSwitch.value = restaurant to dish
            return
        }
        val existing = current.items.firstOrNull { it.dish.id == dish.id }
        val updatedItems = if (existing != null) {
            current.items.map {
                if (it.dish.id == dish.id) it.copy(quantity = (it.quantity + 1).coerceAtMost(20)) else it
            }
        } else {
            current.items + CartItem(dish, 1)
        }
        _cart.value = CartState(restaurant = restaurant, items = updatedItems)
    }

    fun confirmReplaceCartWithNewRestaurant() {
        val pending = _pendingRestaurantSwitch.value ?: return
        _cart.value = CartState(
            restaurant = pending.first,
            items = listOf(CartItem(pending.second, 1))
        )
        _promoCode.value = null
        _pendingRestaurantSwitch.value = null
    }

    fun dismissRestaurantSwitch() {
        _pendingRestaurantSwitch.value = null
    }

    fun decrementDishInCart(dishId: String) {
        val current = _cart.value
        val updatedItems = current.items.mapNotNull { item ->
            if (item.dish.id != dishId) item
            else if (item.quantity > 1) item.copy(quantity = item.quantity - 1)
            else null
        }
        _cart.value = if (updatedItems.isEmpty()) CartState() else current.copy(items = updatedItems)
    }

    fun reorderFromOrder(order: OrderRecord) {
        val restaurant = LocalRestaurantCatalog.findById(order.restaurantId)
        if (restaurant == null) {
            _bannerMessage.value = "Kitchen menu is currently unavailable."
            return
        }
        val matchedItems = order.items.mapNotNull { line ->
            val dish = restaurant.menu.firstOrNull { it.id == line.dishId }
                ?: MenuItem(
                    id = line.dishId,
                    name = line.name,
                    description = "Reordered favorite dish",
                    priceCents = line.unitPriceCents.toInt(),
                    category = "Favorites",
                    veg = true
                )
            CartItem(dish = dish, quantity = line.quantity.toInt().coerceIn(1, 20))
        }
        if (matchedItems.isNotEmpty()) {
            _cart.value = CartState(restaurant = restaurant, items = matchedItems)
            _bannerMessage.value = "Added ${matchedItems.sumOf { it.quantity }} items from ${restaurant.name} to cart."
        }
    }

    fun applyPromoCode(rawCode: String) {
        val code = rawCode.trim().uppercase()
        when (code) {
            "LOCAL20", "FREEDEL", "BITE15" -> {
                _promoCode.value = code
                _bannerMessage.value = "Promo $code applied!"
            }
            "" -> {
                _promoCode.value = null
            }
            else -> {
                _bannerMessage.value = "Invalid code. Try LOCAL20 or FREEDEL."
            }
        }
    }

    fun clearPromoCode() {
        _promoCode.value = null
    }

    fun selectAddress(addressId: String) {
        _selectedAddressId.value = addressId
    }

    fun selectPaymentMethodKey(key: String) {
        _selectedPaymentMethodKey.value = key
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun toggleFavorite(restaurant: Restaurant) {
        val currentlyFav = favoriteIdsState.value.contains(restaurant.id)
        viewModelScope.launch {
            repository.toggleFavorite(currentUserId, restaurant, currentlyFav)
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to update favorite" }
        }
    }

    fun saveAddress(
        label: String,
        street: String,
        apartment: String?,
        city: String,
        instructions: String?,
        makeDefault: Boolean,
        onSaved: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveAddress(
                userId = currentUserId,
                label = label,
                street = street,
                apartment = apartment,
                city = city,
                instructions = instructions,
                defaultAddress = makeDefault
            ).onSuccess { newId ->
                _selectedAddressId.value = newId
                _bannerMessage.value = "Address '$label' saved."
                onSaved(newId)
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Unable to save address."
            }
        }
    }

    fun setDefaultAddress(addressId: String, allIds: List<String>) {
        _selectedAddressId.value = addressId
        viewModelScope.launch {
            repository.setDefaultAddress(currentUserId, addressId, allIds)
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to update default address" }
        }
    }

    fun deleteAddress(addressId: String) {
        viewModelScope.launch {
            repository.deleteAddress(currentUserId, addressId)
                .onSuccess {
                    if (_selectedAddressId.value == addressId) _selectedAddressId.value = null
                }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to delete address" }
        }
    }

    fun saveCardMethod(
        cardNumber: String,
        expiryMmYy: String,
        cvv: String,
        holderName: String,
        onSaved: (String) -> Unit = {}
    ) {
        val tokenized = PaymentSecurityValidator.validateAndTokenizeCard(
            rawNumber = cardNumber,
            expiryMmYy = expiryMmYy,
            cvv = cvv,
            holderName = holderName
        ).getOrElse { err ->
            _bannerMessage.value = err.message ?: "Invalid card details"
            return
        }
        viewModelScope.launch {
            repository.saveTokenizedPaymentMethod(
                userId = currentUserId,
                methodType = PaymentMethodType.CARD,
                brand = tokenized.brand,
                last4OrHandle = tokenized.last4,
                holderName = tokenized.holderName,
                expiryMonth = tokenized.expiryMonth,
                expiryYear = tokenized.expiryYear,
                defaultMethod = true
            ).onSuccess { id ->
                _selectedPaymentMethodKey.value = id
                _bannerMessage.value = "${tokenized.brand} •••• ${tokenized.last4} tokenized & saved."
                onSaved(id)
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Unable to save payment method."
            }
        }
    }

    fun saveUpiMethod(
        upiHandle: String,
        holderName: String,
        onSaved: (String) -> Unit = {}
    ) {
        val validated = PaymentSecurityValidator.validateUpiHandle(upiHandle, holderName)
            .getOrElse { err ->
                _bannerMessage.value = err.message ?: "Invalid UPI ID"
                return
            }
        viewModelScope.launch {
            repository.saveTokenizedPaymentMethod(
                userId = currentUserId,
                methodType = PaymentMethodType.UPI,
                brand = "UPI",
                last4OrHandle = validated.first,
                holderName = validated.second,
                defaultMethod = true
            ).onSuccess { id ->
                _selectedPaymentMethodKey.value = id
                _bannerMessage.value = "UPI ${validated.first} verified & saved."
                onSaved(id)
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Unable to save UPI ID."
            }
        }
    }

    fun deletePaymentMethod(methodId: String) {
        viewModelScope.launch {
            repository.deletePaymentMethod(currentUserId, methodId)
                .onSuccess {
                    if (_selectedPaymentMethodKey.value == methodId) {
                        _selectedPaymentMethodKey.value = null
                    }
                }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to remove payment method" }
        }
    }

    fun placeSecureOrder(
        addressLabel: String,
        addressLine: String,
        deliveryInstructions: String?,
        paymentMethodSummary: String,
        onOrderCreated: (String) -> Unit
    ) {
        val currentCart = _cart.value
        val restaurant = currentCart.restaurant
        if (restaurant == null || currentCart.items.isEmpty()) {
            _bannerMessage.value = "Your cart is empty."
            return
        }
        if (addressLine.trim().length < 3) {
            _bannerMessage.value = "Please select or add a delivery address."
            return
        }
        if (_isSubmittingOrder.value) return

        val pricing = calculatePricing(currentCart, _promoCode.value)
        _isSubmittingOrder.value = true
        viewModelScope.launch {
            val result = repository.placeOrder(
                userId = currentUserId,
                restaurant = restaurant,
                cartItems = currentCart.items,
                deliveryAddressLabel = addressLabel,
                deliveryAddressLine = addressLine,
                deliveryInstructions = deliveryInstructions,
                paymentMethodSummary = paymentMethodSummary,
                promoCode = pricing.appliedPromo,
                subtotalCents = pricing.subtotalCents,
                deliveryFeeCents = pricing.deliveryFeeCents,
                taxCents = pricing.taxCents,
                discountCents = pricing.discountCents,
                totalCents = pricing.totalCents
            )
            _isSubmittingOrder.value = false
            result.onSuccess { orderId ->
                _cart.value = CartState()
                _promoCode.value = null
                startAutoLiveTrackingSimulation(orderId)
                onOrderCreated(orderId)
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Payment authorization failed."
            }
        }
    }

    fun advanceOrderStage(order: OrderRecord) {
        val next = order.parsedStatus().nextProgression() ?: return
        viewModelScope.launch {
            repository.advanceOrderStatus(
                userId = currentUserId,
                orderId = order.id,
                nextStatus = next.first,
                nextEtaMinutes = next.second
            ).onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Could not update tracking status."
            }
        }
    }

    fun cancelOrder(orderId: String) {
        liveTrackingJob?.cancel()
        viewModelScope.launch {
            repository.cancelOrder(currentUserId, orderId)
                .onSuccess {
                    _bannerMessage.value = "Order cancelled and refund initiated."
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Could not cancel order."
                }
        }
    }

    fun rateDeliveredOrder(orderId: String, rating: Int) {
        viewModelScope.launch {
            repository.rateOrder(currentUserId, orderId, rating)
                .onSuccess {
                    _bannerMessage.value = "Thanks for rating $rating stars!"
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Could not submit rating."
                }
        }
    }

    private fun startAutoLiveTrackingSimulation(orderId: String) {
        liveTrackingJob?.cancel()
        liveTrackingJob = viewModelScope.launch {
            val stages = listOf(
                OrderStatus.CONFIRMED to 22,
                OrderStatus.PREPARING to 15,
                OrderStatus.OUT_FOR_DELIVERY to 7,
                OrderStatus.DELIVERED to 0
            )
            for ((nextStatus, nextEta) in stages) {
                delay(7000L)
                val res = repository.advanceOrderStatus(currentUserId, orderId, nextStatus, nextEta)
                if (res.isFailure) break
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveTrackingJob?.cancel()
    }

    private fun calculatePricing(cart: CartState, promo: String?): CheckoutPricing {
        val restaurant = cart.restaurant
        if (restaurant == null || cart.items.isEmpty()) {
            return CheckoutPricing(0, 0, 0, 0, 0, null)
        }
        val subtotal = cart.subtotalCents
        var deliveryFee = restaurant.deliveryFeeCents
        var discount = 0
        var applied: String? = null
        when (promo) {
            "LOCAL20" -> {
                discount = ((subtotal * 20) / 100).coerceAtMost(800)
                applied = "LOCAL20"
            }
            "BITE15" -> {
                discount = ((subtotal * 15) / 100).coerceAtMost(600)
                applied = "BITE15"
            }
            "FREEDEL" -> {
                deliveryFee = 0
                applied = "FREEDEL"
            }
        }
        val tax = ((subtotal - discount).coerceAtLeast(0) * 8) / 100
        val total = (subtotal - discount + deliveryFee + tax).coerceAtLeast(1)
        return CheckoutPricing(
            subtotalCents = subtotal,
            deliveryFeeCents = deliveryFee,
            taxCents = tax,
            discountCents = discount,
            totalCents = total,
            appliedPromo = applied
        )
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "BiteLocalVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
