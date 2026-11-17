package com.example.ui.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.SavedAddress
import com.example.data.model.TokenizedPaymentMethod
import com.example.data.model.UiState
import com.example.data.model.formatCents
import com.example.data.remote.PaymentSecurityValidator
import com.example.ui.components.VegIndicator
import com.example.ui.explore.AddressPickerSheetContent
import com.example.ui.viewmodel.BiteLocalViewModel

private const val EXPRESS_WALLET_KEY = "bitepay_express_wallet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    viewModel: BiteLocalViewModel,
    onBack: () -> Unit,
    onOrderPlaced: (String) -> Unit
) {
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val pricing by viewModel.checkoutPricing.collectAsStateWithLifecycle()
    val addressesState by viewModel.addressesState.collectAsStateWithLifecycle()
    val paymentMethodsState by viewModel.paymentMethodsState.collectAsStateWithLifecycle()
    val selectedAddressId by viewModel.selectedAddressId.collectAsStateWithLifecycle()
    val selectedPaymentKey by viewModel.selectedPaymentMethodKey.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmittingOrder.collectAsStateWithLifecycle()

    val addresses = (addressesState as? UiState.Success)?.data ?: emptyList()
    val activeAddress: SavedAddress? = addresses.firstOrNull { it.id == selectedAddressId }
        ?: addresses.firstOrNull { it.defaultAddress }
        ?: addresses.firstOrNull()

    val savedMethods = (paymentMethodsState as? UiState.Success)?.data ?: emptyList()
    val effectivePaymentKey = selectedPaymentKey
        ?: savedMethods.firstOrNull { it.defaultMethod }?.id
        ?: savedMethods.firstOrNull()?.id
        ?: EXPRESS_WALLET_KEY

    val selectedSavedMethod: TokenizedPaymentMethod? = savedMethods.firstOrNull { it.id == effectivePaymentKey }
    val paymentSummaryText = selectedSavedMethod?.displaySummary() ?: "BitePay Express Wallet"

    var promoInput by remember { mutableStateOf("") }
    var deliveryInstructions by remember { mutableStateOf("") }
    var showAddressSheet by remember { mutableStateOf(false) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showPaymentAuthDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Secure Checkout",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (cart.restaurant != null) {
                            Text(
                                text = cart.restaurant!!.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (cart.items.isEmpty() || cart.restaurant == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Your cart is empty",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Browse local restaurants in Explore to add fresh dishes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onBack) {
                        Text("Explore Restaurants")
                    }
                }
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Security Trust Pill
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.checkout_security_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Delivery Address Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Delivery Address", style = MaterialTheme.typography.titleMedium)
                                }
                                TextButton(
                                    onClick = { showAddressSheet = true },
                                    modifier = Modifier.testTag("checkout_change_address_btn")
                                ) {
                                    Text(if (activeAddress == null) "Add Address" else "Change")
                                }
                            }
                            if (activeAddress != null) {
                                Text(
                                    text = "${activeAddress.label} • ${activeAddress.formattedLine()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "No delivery address selected yet. Tap 'Add Address' to continue.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            OutlinedTextField(
                                value = deliveryInstructions,
                                onValueChange = { if (it.length <= 160) deliveryInstructions = it },
                                label = { Text("Cooking or drop-off note (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Cart Items Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Order Items", style = MaterialTheme.typography.titleMedium)
                            cart.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        VegIndicator(veg = item.dish.veg)
                                        Column {
                                            Text(
                                                text = item.dish.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = formatCents(item.dish.priceCents),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.decrementDishInCart(item.dish.id) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.Remove, contentDescription = "Remove one", modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = item.quantity.toString(),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = { viewModel.addDishToCart(cart.restaurant!!, item.dish) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = "Add one", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        text = formatCents(item.lineTotalCents),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // Promo Code Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocalOffer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text("Offers & Promo Codes", style = MaterialTheme.typography.titleMedium)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = promoInput,
                                    onValueChange = { promoInput = it },
                                    label = { Text("Promo code") },
                                    placeholder = { Text("LOCAL20 or FREEDEL") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { viewModel.applyPromoCode(promoInput) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Apply")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("LOCAL20", "FREEDEL").forEach { code ->
                                    FilterChip(
                                        selected = pricing.appliedPromo == code,
                                        onClick = {
                                            promoInput = code
                                            viewModel.applyPromoCode(code)
                                        },
                                        label = { Text(code) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Secure Payment Method Selector
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Payment Method", style = MaterialTheme.typography.titleMedium)
                                }
                                TextButton(
                                    onClick = { showPaymentSheet = true },
                                    modifier = Modifier.testTag("add_payment_method_btn")
                                ) {
                                    Text("+ Card / UPI")
                                }
                            }

                            savedMethods.forEach { method ->
                                val selected = effectivePaymentKey == method.id
                                PaymentOptionRow(
                                    title = method.displaySummary(),
                                    subtitle = if (method.expiryMonth != null && method.expiryYear != null)
                                        "Tokenized • Exp %02d/%d".format(method.expiryMonth, method.expiryYear % 100)
                                    else "Verified ${method.holderName}",
                                    selected = selected,
                                    onClick = { viewModel.selectPaymentMethodKey(method.id) }
                                )
                            }

                            PaymentOptionRow(
                                title = "BitePay Express Wallet",
                                subtitle = "Instant tokenized one-tap pay",
                                selected = effectivePaymentKey == EXPRESS_WALLET_KEY,
                                onClick = { viewModel.selectPaymentMethodKey(EXPRESS_WALLET_KEY) }
                            )
                        }
                    }
                }

                // Bill Summary
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Bill Summary", style = MaterialTheme.typography.titleMedium)
                            BillRow("Item Subtotal", formatCents(pricing.subtotalCents))
                            BillRow("Delivery Partner Fee", if (pricing.deliveryFeeCents == 0) "FREE" else formatCents(pricing.deliveryFeeCents))
                            BillRow("Taxes & Kitchen Packaging (8%)", formatCents(pricing.taxCents))
                            if (pricing.discountCents > 0) {
                                BillRow(
                                    label = "Promo Discount (${pricing.appliedPromo})",
                                    value = "-${formatCents(pricing.discountCents)}",
                                    highlightColor = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Grand Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = formatCents(pricing.totalCents),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Sticky Pay Now Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .widthIn(max = 680.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = formatCents(pricing.totalCents),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = paymentSummaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            if (activeAddress == null) {
                                showAddressSheet = true
                            } else {
                                showPaymentAuthDialog = true
                            }
                        },
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("pay_and_order_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (activeAddress == null) "Select Address" else "Authorize & Pay",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddressSheet) {
        ModalBottomSheet(onDismissRequest = { showAddressSheet = false }) {
            AddressPickerSheetContent(
                addresses = addresses,
                selectedAddressId = activeAddress?.id,
                onSelectAddress = { id ->
                    viewModel.selectAddress(id)
                    showAddressSheet = false
                },
                onSaveAddress = { label, street, apt, city, instructions ->
                    viewModel.saveAddress(
                        label = label,
                        street = street,
                        apartment = apt,
                        city = city,
                        instructions = instructions,
                        makeDefault = true,
                        onSaved = { showAddressSheet = false }
                    )
                }
            )
        }
    }

    if (showPaymentSheet) {
        ModalBottomSheet(onDismissRequest = { showPaymentSheet = false }) {
            AddPaymentMethodSheetContent(
                onSaveCard = { number, expiry, cvv, holder ->
                    viewModel.saveCardMethod(number, expiry, cvv, holder) {
                        showPaymentSheet = false
                    }
                },
                onSaveUpi = { handle, holder ->
                    viewModel.saveUpiMethod(handle, holder) {
                        showPaymentSheet = false
                    }
                }
            )
        }
    }

    if (showPaymentAuthDialog && activeAddress != null) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showPaymentAuthDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(34.dp)
                )
            },
            title = { Text("Confirm Encrypted Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Authorizing ${formatCents(pricing.totalCents)} via $paymentSummaryText.")
                    Text(
                        text = "Delivering to ${activeAddress.label}: ${activeAddress.formattedLine()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPaymentAuthDialog = false
                        viewModel.placeSecureOrder(
                            addressLabel = activeAddress.label,
                            addressLine = activeAddress.formattedLine(),
                            deliveryInstructions = deliveryInstructions.ifBlank { activeAddress.instructions },
                            paymentMethodSummary = paymentSummaryText,
                            onOrderCreated = onOrderPlaced
                        )
                    },
                    modifier = Modifier.testTag("confirm_payment_dialog_btn")
                ) {
                    Text("Confirm ${formatCents(pricing.totalCents)}")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentAuthDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PaymentOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (title.contains("Wallet")) Icons.Rounded.AccountBalanceWallet else Icons.Rounded.CreditCard,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun BillRow(
    label: String,
    value: String,
    highlightColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = highlightColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AddPaymentMethodSheetContent(
    onSaveCard: (cardNumber: String, expiryMmYy: String, cvv: String, holderName: String) -> Unit,
    onSaveUpi: (upiHandle: String, holderName: String) -> Unit
) {
    var mode by remember { mutableStateOf("CARD") }
    var cardNumber by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf("") }
    var upiHandle by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val detectedBrand = remember(cardNumber) {
        val digits = cardNumber.filter { it.isDigit() }
        if (digits.isNotEmpty()) PaymentSecurityValidator.detectCardBrand(digits) else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add Secure Payment Method", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Only masked last-4 tokens are stored. Full card numbers & CVVs never leave memory.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == "CARD",
                onClick = { mode = "CARD"; localError = null },
                label = { Text("Credit / Debit Card") }
            )
            FilterChip(
                selected = mode == "UPI",
                onClick = { mode = "UPI"; localError = null },
                label = { Text("UPI Handle") }
            )
        }

        if (mode == "CARD") {
            OutlinedTextField(
                value = cardNumber,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(16)
                    cardNumber = digits
                },
                label = { Text("Card Number *") },
                trailingIcon = {
                    if (detectedBrand != null) {
                        Text(
                            text = detectedBrand,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("card_number_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = expiry,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(4)
                        expiry = if (digits.length > 2) "${digits.take(2)}/${digits.drop(2)}" else digits
                    },
                    label = { Text("MM/YY *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("card_expiry_input")
                )
                OutlinedTextField(
                    value = cvv,
                    onValueChange = { input ->
                        cvv = input.filter { it.isDigit() }.take(4)
                    },
                    label = { Text("CVV *") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("card_cvv_input")
                )
            }
            OutlinedTextField(
                value = holderName,
                onValueChange = { holderName = it },
                label = { Text("Name on Card *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("card_holder_input")
            )
        } else {
            OutlinedTextField(
                value = upiHandle,
                onValueChange = { upiHandle = it },
                label = { Text("UPI VPA ID *") },
                placeholder = { Text("alex@okaxis") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = holderName,
                onValueChange = { holderName = it },
                label = { Text("Account Holder Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (localError != null) {
            Text(
                text = localError ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                if (mode == "CARD") {
                    PaymentSecurityValidator.validateAndTokenizeCard(cardNumber, expiry, cvv, holderName)
                        .onSuccess {
                            localError = null
                            onSaveCard(cardNumber, expiry, cvv, holderName)
                        }
                        .onFailure { localError = it.message }
                } else {
                    PaymentSecurityValidator.validateUpiHandle(upiHandle, holderName)
                        .onSuccess {
                            localError = null
                            onSaveUpi(upiHandle, holderName)
                        }
                        .onFailure { localError = it.message }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_payment_method_btn"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (mode == "CARD") "Verify & Tokenize Card" else "Verify & Save UPI")
        }
    }
}
