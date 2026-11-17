package com.example.ui.explore

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.CuisineCategory
import com.example.data.model.LocalRestaurantCatalog
import com.example.data.model.OrderRecord
import com.example.data.model.Restaurant
import com.example.data.model.SavedAddress
import com.example.data.model.UiState
import com.example.data.model.formatCents
import com.example.ui.components.LiveOrderFloatingBanner
import com.example.ui.components.RestaurantCard
import com.example.ui.viewmodel.BiteLocalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: BiteLocalViewModel,
    onRestaurantClick: (String) -> Unit,
    onOpenCart: () -> Unit,
    onTrackOrder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filter by viewModel.exploreFilter.collectAsStateWithLifecycle()
    val restaurants by viewModel.filteredRestaurants.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIdsState.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val addressesState by viewModel.addressesState.collectAsStateWithLifecycle()
    val selectedAddressId by viewModel.selectedAddressId.collectAsStateWithLifecycle()

    val addresses = (addressesState as? UiState.Success)?.data ?: emptyList()
    val activeAddress = addresses.firstOrNull { it.id == selectedAddressId }
        ?: addresses.firstOrNull { it.defaultAddress }
        ?: addresses.firstOrNull()

    val activeOrder: OrderRecord? = (ordersState as? UiState.Success)?.data
        ?.firstOrNull { it.parsedStatus().isActive }

    var showAddressSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 680.dp
            val spotlight = LocalRestaurantCatalog.restaurants[1] // Kuro Broth & Izakaya

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = if (cart.totalCount > 0 || activeOrder != null) 110.dp else 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ExploreHeader(
                        activeAddress = activeAddress,
                        cartCount = cart.totalCount,
                        onLocationClick = { showAddressSheet = true },
                        onCartClick = onOpenCart
                    )
                }

                item {
                    OutlinedTextField(
                        value = filter.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.explore_search_hint),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search kitchens",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (filter.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explore_search_input")
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(CuisineCategory.entries) { category ->
                                val selected = filter.selectedCategory == category
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectCategory(category) },
                                    label = {
                                        Text(
                                            text = category.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("cuisine_chip_${category.name}")
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = filter.fastDeliveryOnly,
                                    onClick = viewModel::toggleFastDeliveryFilter,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Bolt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Under 26 min") },
                                    shape = RoundedCornerShape(50)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filter.topRatedOnly,
                                    onClick = viewModel::toggleTopRatedFilter,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Rated 4.7+") },
                                    shape = RoundedCornerShape(50)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filter.pureVegOnly,
                                    onClick = viewModel::togglePureVegFilter,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Eco,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Pure Veg") },
                                    shape = RoundedCornerShape(50)
                                )
                            }
                        }
                    }
                }

                if (filter.searchQuery.isBlank() && filter.selectedCategory == CuisineCategory.ALL &&
                    !filter.pureVegOnly && !filter.fastDeliveryOnly && !filter.topRatedOnly
                ) {
                    item {
                        EditorialSpotlightBanner(
                            restaurant = spotlight,
                            onClick = { onRestaurantClick(spotlight.id) }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Local Kitchens Near You",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${restaurants.size} open kitchens • Use code LOCAL20 for 20% off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (restaurants.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restaurant,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "No matching kitchens found",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Try clearing your filters or searching for pizza, ramen, biryani, or burgers.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = viewModel::clearExploreFilters) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    }
                } else if (isWide) {
                    val rows = restaurants.chunked(2)
                    items(rows, key = { pair -> pair.joinToString { it.id } }) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            RestaurantCard(
                                restaurant = pair[0],
                                favorited = favorites.contains(pair[0].id),
                                onToggleFavorite = { viewModel.toggleFavorite(pair[0]) },
                                onClick = { onRestaurantClick(pair[0].id) },
                                modifier = Modifier.weight(1f)
                            )
                            if (pair.size > 1) {
                                RestaurantCard(
                                    restaurant = pair[1],
                                    favorited = favorites.contains(pair[1].id),
                                    onToggleFavorite = { viewModel.toggleFavorite(pair[1]) },
                                    onClick = { onRestaurantClick(pair[1].id) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(restaurants, key = { it.id }) { restaurant ->
                        RestaurantCard(
                            restaurant = restaurant,
                            favorited = favorites.contains(restaurant.id),
                            onToggleFavorite = { viewModel.toggleFavorite(restaurant) },
                            onClick = { onRestaurantClick(restaurant.id) }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeOrder != null) {
                LiveOrderFloatingBanner(
                    order = activeOrder,
                    onClick = { onTrackOrder(activeOrder.id) }
                )
            }
            if (cart.totalCount > 0 && cart.restaurant != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenCart)
                        .testTag("explore_cart_bar")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${cart.totalCount} item${if (cart.totalCount > 1) "s" else ""} • ${formatCents(cart.subtotalCents)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = cart.restaurant!!.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Checkout",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddressSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddressSheet = false }
        ) {
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
}

@Composable
private fun ExploreHeader(
    activeAddress: SavedAddress?,
    cartCount: Int,
    onLocationClick: () -> Unit,
    onCartClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onLocationClick)
                .padding(vertical = 4.dp, horizontal = 2.dp)
                .testTag("location_selector_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Delivery address",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = activeAddress?.label ?: "Set Delivery Address",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = activeAddress?.formattedLine() ?: "Tap to add your street & apartment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = CircleShape,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onCartClick)
                .testTag("header_cart_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ShoppingBag,
                    contentDescription = "Open cart",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                if (cartCount > 0) {
                    Text(
                        text = cartCount.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorialSpotlightBanner(
    restaurant: Restaurant,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("spotlight_banner")
    ) {
        Image(
            painter = painterResource(id = restaurant.imageRes),
            contentDescription = restaurant.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xE61A100E), Color(0x881A100E), Color.Transparent)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(18.dp)
                .fillMaxWidth(0.75f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "CHEF'S SPOTLIGHT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
            Text(
                text = restaurant.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Text(
                text = "${restaurant.cuisine} • Free delivery with FREEDEL",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f)
            )
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "Explore Menu",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun AddressPickerSheetContent(
    addresses: List<SavedAddress>,
    selectedAddressId: String?,
    onSelectAddress: (String) -> Unit,
    onSaveAddress: (label: String, street: String, apt: String?, city: String, instructions: String?) -> Unit
) {
    var addingNew by remember { mutableStateOf(addresses.isEmpty()) }
    var label by remember { mutableStateOf("Home") }
    var street by remember { mutableStateOf("") }
    var apartment by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("San Francisco") }
    var instructions by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (addingNew) "Add Delivery Address" else "Choose Delivery Address",
                style = MaterialTheme.typography.titleLarge
            )
            if (addresses.isNotEmpty()) {
                TextButton(onClick = { addingNew = !addingNew }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (addingNew) "Saved List" else "Add New")
                }
            }
        }

        if (!addingNew) {
            addresses.forEach { address ->
                val isSelected = address.id == selectedAddressId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectAddress(address.id) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = address.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = address.formattedLine(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Home", "Work", "Other").forEach { preset ->
                    FilterChip(
                        selected = label == preset,
                        onClick = { label = preset },
                        label = { Text(preset) }
                    )
                }
            }
            OutlinedTextField(
                value = street,
                onValueChange = { street = it },
                label = { Text("Street Address *") },
                placeholder = { Text("420 Valencia St") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("address_street_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = apartment,
                    onValueChange = { apartment = it },
                    label = { Text("Apt / Suite") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City *") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Delivery Instructions (optional)") },
                placeholder = { Text("Gate code or leave at door") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    if (street.trim().length < 3) {
                        validationError = "Please enter at least 3 characters for street address."
                    } else if (city.trim().length < 2) {
                        validationError = "Please enter a valid city."
                    } else {
                        validationError = null
                        onSaveAddress(label, street, apartment, city, instructions)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_address_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save & Use Address")
            }
        }
    }
}
