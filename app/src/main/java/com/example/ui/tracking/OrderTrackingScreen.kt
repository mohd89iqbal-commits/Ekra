package com.example.ui.tracking

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.OrderRecord
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.data.model.UiState
import com.example.data.model.formatCents
import com.example.ui.viewmodel.BiteLocalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTrackingScreen(
    orderId: String,
    viewModel: BiteLocalViewModel,
    onBack: () -> Unit
) {
    val orderFlow = remember(orderId, viewModel) { viewModel.observeOrderById(orderId) }
    val orderState by orderFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Order Tracking", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tracking_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = orderState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
                is UiState.Success -> {
                    val order = state.data
                    if (order == null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Order not found", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onBack) { Text("Back to Orders") }
                        }
                    } else {
                        OrderTrackingContent(
                            order = order,
                            onAdvanceStage = { viewModel.advanceOrderStage(order) },
                            onCancelOrder = { viewModel.cancelOrder(order.id) },
                            onRateOrder = { stars -> viewModel.rateDeliveredOrder(order.id, stars) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderTrackingContent(
    order: OrderRecord,
    onAdvanceStage: () -> Unit,
    onCancelOrder: () -> Unit,
    onRateOrder: (Int) -> Unit
) {
    val status = order.parsedStatus()
    val progressTarget = when (status) {
        OrderStatus.PLACED -> 0.08f
        OrderStatus.CONFIRMED -> 0.25f
        OrderStatus.PREPARING -> 0.45f
        OrderStatus.OUT_FOR_DELIVERY -> 0.78f
        OrderStatus.DELIVERED -> 1.0f
        OrderStatus.CANCELLED -> 0.0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "courierRouteProgress"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero ETA & Status Header
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (status) {
                        OrderStatus.DELIVERED -> MaterialTheme.colorScheme.tertiaryContainer
                        OrderStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = when (status) {
                                OrderStatus.DELIVERED -> MaterialTheme.colorScheme.tertiary
                                OrderStatus.CANCELLED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (status.isActive) "LIVE • FIRESTORE SYNC" else status.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = order.transactionRef,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = when (status) {
                            OrderStatus.DELIVERED -> "Order Delivered!"
                            OrderStatus.CANCELLED -> "Order Cancelled & Refunded"
                            else -> "Arriving in ${order.etaMinutes} min"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("tracking_eta_title")
                    )
                    Text(
                        text = "${order.restaurantName} • ${status.subtitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Stylized Live Neighborhood Route Canvas
        if (status != OrderStatus.CANCELLED) {
            item {
                LiveRouteMapCard(
                    restaurantName = order.restaurantName,
                    destinationLabel = order.deliveryAddressLabel,
                    progress = animatedProgress,
                    isDelivered = status == OrderStatus.DELIVERED
                )
            }
        }

        // 5-Step Real-Time Timeline
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Live Order Milestones", style = MaterialTheme.typography.titleMedium)

                    val steps = listOf(
                        OrderStatus.PLACED,
                        OrderStatus.CONFIRMED,
                        OrderStatus.PREPARING,
                        OrderStatus.OUT_FOR_DELIVERY,
                        OrderStatus.DELIVERED
                    )

                    steps.forEachIndexed { index, step ->
                        val reached = status != OrderStatus.CANCELLED && status.stepIndex >= step.stepIndex
                        val isCurrent = status == step
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            reached -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (reached) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (reached) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = step.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (status.isActive) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onAdvanceStage,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("advance_tracking_stage_btn")
                            ) {
                                Icon(Icons.Rounded.FastForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Next Live Update")
                            }
                            if (status == OrderStatus.PLACED || status == OrderStatus.CONFIRMED) {
                                OutlinedButton(
                                    onClick = onCancelOrder,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("cancel_order_btn")
                                ) {
                                    Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Courier Details Card
        if (status != OrderStatus.CANCELLED) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeliveryDining,
                                contentDescription = "Delivery partner",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = order.courierName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${order.courierVehicle} • ★ ${String.format("%.1f", order.courierRating)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (status == OrderStatus.OUT_FOR_DELIVERY) "En Route" else "Assigned",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Post-delivery Rating Card
        if (status == OrderStatus.DELIVERED) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val existingRating = order.userRating?.toInt()
                        Text(
                            text = if (existingRating != null) "You rated this meal $existingRating/5"
                            else "How was your order from ${order.restaurantName}?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (star in 1..5) {
                                val filled = existingRating != null && star <= existingRating
                                IconButton(
                                    onClick = { if (existingRating == null) onRateOrder(star) },
                                    enabled = existingRating == null
                                ) {
                                    Icon(
                                        imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Rate $star stars",
                                        tint = if (filled) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Receipt & Encrypted Payment Verification Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Order & Payment Receipt", style = MaterialTheme.typography.titleMedium)
                        }
                        Surface(
                            color = if (order.parsedPaymentStatus() == PaymentStatus.PAID)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = order.paymentStatus,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "Delivering to ${order.deliveryAddressLabel}: ${order.deliveryAddressLine}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Paid via ${order.paymentMethodSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    order.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.quantity}x ${item.name}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatCents((item.unitPriceCents * item.quantity).toInt()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatCents(order.totalCents.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveRouteMapCard(
    restaurantName: String,
    destinationLabel: String,
    progress: Float,
    isDelivered: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "courierPulse")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "haloScale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val bgColor = MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Subtle neighborhood city block grid
                    val stepX = size.width / 6f
                    val stepY = size.height / 4f
                    for (i in 1..5) {
                        drawLine(
                            color = gridColor,
                            start = Offset(stepX * i, 0f),
                            end = Offset(stepX * i, size.height),
                            strokeWidth = 2f
                        )
                    }
                    for (j in 1..3) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, stepY * j),
                            end = Offset(size.width, stepY * j),
                            strokeWidth = 2f
                        )
                    }

                    val start = Offset(size.width * 0.14f, size.height * 0.68f)
                    val control = Offset(size.width * 0.50f, size.height * 0.16f)
                    val end = Offset(size.width * 0.86f, size.height * 0.64f)

                    // Dashed full route
                    val routePath = Path().apply {
                        moveTo(start.x, start.y)
                        quadraticTo(control.x, control.y, end.x, end.y)
                    }
                    drawPath(
                        path = routePath,
                        color = primaryColor.copy(alpha = 0.28f),
                        style = Stroke(
                            width = 8f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                        )
                    )

                    // Completed segment sampled along quadratic Bezier
                    val steps = 40
                    val maxStep = (steps * progress).toInt().coerceAtLeast(1)
                    val activePath = Path()
                    for (s in 0..maxStep) {
                        val t = (s / steps.toFloat()).coerceAtMost(progress)
                        val oneMinusT = 1f - t
                        val px = oneMinusT * oneMinusT * start.x + 2 * oneMinusT * t * control.x + t * t * end.x
                        val py = oneMinusT * oneMinusT * start.y + 2 * oneMinusT * t * control.y + t * t * end.y
                        if (s == 0) activePath.moveTo(px, py) else activePath.lineTo(px, py)
                    }
                    drawPath(
                        path = activePath,
                        color = tertiaryColor,
                        style = Stroke(width = 10f, cap = StrokeCap.Round)
                    )

                    // Kitchen & Home endpoints
                    drawCircle(color = primaryColor, radius = 16f, center = start)
                    drawCircle(color = Color.White, radius = 7f, center = start)
                    drawCircle(color = tertiaryColor, radius = 16f, center = end)
                    drawCircle(color = Color.White, radius = 7f, center = end)

                    // Animated courier marker
                    val t = progress.coerceIn(0f, 1f)
                    val oneMinusT = 1f - t
                    val cx = oneMinusT * oneMinusT * start.x + 2 * oneMinusT * t * control.x + t * t * end.x
                    val cy = oneMinusT * oneMinusT * start.y + 2 * oneMinusT * t * control.y + t * t * end.y
                    val courierCenter = Offset(cx, cy)

                    if (!isDelivered) {
                        drawCircle(
                            color = tertiaryColor.copy(alpha = 0.22f),
                            radius = 28f * haloScale,
                            center = courierCenter
                        )
                    }
                    drawCircle(color = tertiaryColor, radius = 18f, center = courierCenter)
                    drawCircle(color = Color.White, radius = 8f, center = courierCenter)
                }

                // Kitchen & Home label chips
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = primaryColor, modifier = Modifier.size(13.dp))
                        Text(restaurantName, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = null, tint = tertiaryColor, modifier = Modifier.size(13.dp))
                        Text(destinationLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
