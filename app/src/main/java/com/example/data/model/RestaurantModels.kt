package com.example.data.model

import androidx.annotation.DrawableRes
import com.example.R
import java.util.Locale

enum class CuisineCategory(val label: String) {
    ALL("All"),
    ITALIAN("Wood-Fired"),
    ASIAN("Ramen & Bao"),
    BURGERS("Craft Grill"),
    INDIAN("Biryanis"),
    HEALTHY("Bowls")
}

data class MenuItem(
    val id: String,
    val name: String,
    val description: String,
    val priceCents: Int,
    val category: String,
    val veg: Boolean,
    val bestseller: Boolean = false,
    val spicyLevel: Int = 0,
    val calories: Int = 520
)

data class Restaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val category: CuisineCategory,
    val rating: Double,
    val reviewCount: Int,
    val deliveryTimeMin: Int,
    val deliveryTimeMax: Int,
    val distanceKm: Double,
    val priceForTwoCents: Int,
    val deliveryFeeCents: Int,
    val pureVeg: Boolean,
    val promoTag: String?,
    val neighborhood: String,
    val kitchenAddress: String,
    val hygieneBadge: String,
    @DrawableRes val imageRes: Int,
    val menu: List<MenuItem>
)

data class CartItem(
    val dish: MenuItem,
    val quantity: Int
) {
    val lineTotalCents: Int get() = dish.priceCents * quantity
}

data class CartState(
    val restaurant: Restaurant? = null,
    val items: List<CartItem> = emptyList()
) {
    val totalCount: Int get() = items.sumOf { it.quantity }
    val subtotalCents: Int get() = items.sumOf { it.lineTotalCents }
    fun quantityFor(dishId: String): Int = items.firstOrNull { it.dish.id == dishId }?.quantity ?: 0
}

fun formatCents(cents: Int): String {
    return String.format(Locale.US, "$%.2f", cents / 100.0)
}

object LocalRestaurantCatalog {
    val restaurants: List<Restaurant> = listOf(
        Restaurant(
            id = "rest_napoli_ember",
            name = "Napoli Ember Kitchen",
            cuisine = "Wood-Fired Neapolitan • Sourdough",
            category = CuisineCategory.ITALIAN,
            rating = 4.8,
            reviewCount = 1420,
            deliveryTimeMin = 20,
            deliveryTimeMax = 26,
            distanceKm = 1.1,
            priceForTwoCents = 3400,
            deliveryFeeCents = 199,
            pureVeg = false,
            promoTag = "20% OFF • LOCAL20",
            neighborhood = "Valencia Corridor",
            kitchenAddress = "742 Valencia St",
            hygieneBadge = "A+ Open Kitchen",
            imageRes = R.drawable.img_food_pizza,
            menu = listOf(
                MenuItem(
                    id = "dish_margherita",
                    name = "Buffalo Margherita DOP",
                    description = "72-hour fermented crust, crushed San Marzano tomatoes, fior di latte, basil oil.",
                    priceCents = 1850,
                    category = "Wood-Fired Pizzas",
                    veg = true,
                    bestseller = true,
                    calories = 680
                ),
                MenuItem(
                    id = "dish_diavola",
                    name = "Hot Honey Diavola",
                    description = "Crispy Calabrian soppressata, chili-infused wildflower honey, aged provolone.",
                    priceCents = 2150,
                    category = "Wood-Fired Pizzas",
                    veg = false,
                    bestseller = true,
                    spicyLevel = 2,
                    calories = 790
                ),
                MenuItem(
                    id = "dish_truffle_funghi",
                    name = "Wild Mushroom & Truffle",
                    description = "Roasted cremini, fontina fonduta, thyme, black winter truffle drizzle.",
                    priceCents = 2050,
                    category = "Wood-Fired Pizzas",
                    veg = true,
                    calories = 710
                ),
                MenuItem(
                    id = "dish_burrata_salad",
                    name = "Heirloom Burrata & Fig",
                    description = "Creamy Pugliese burrata, charred sourdough crostini, aged balsamic glaze.",
                    priceCents = 1450,
                    category = "Starters",
                    veg = true,
                    calories = 420
                ),
                MenuItem(
                    id = "dish_tiramisu",
                    name = "Espresso Mascarpone Tiramisu",
                    description = "Savoiardi soaked in single-origin ristretto, whipped mascarpone, Valrhona cocoa.",
                    priceCents = 950,
                    category = "Dolci",
                    veg = true,
                    bestseller = true,
                    calories = 390
                )
            )
        ),
        Restaurant(
            id = "rest_kuro_ramen",
            name = "Kuro Broth & Izakaya",
            cuisine = "18-Hr Tonkotsu • Handcrafted Noodles",
            category = CuisineCategory.ASIAN,
            rating = 4.9,
            reviewCount = 2180,
            deliveryTimeMin = 22,
            deliveryTimeMax = 29,
            distanceKm = 1.6,
            priceForTwoCents = 3800,
            deliveryFeeCents = 249,
            pureVeg = false,
            promoTag = "FREE DELIVERY • FREEDEL",
            neighborhood = "Japantown West",
            kitchenAddress = "1610 Post St",
            hygieneBadge = "Chef Verified",
            imageRes = R.drawable.img_food_ramen,
            menu = listOf(
                MenuItem(
                    id = "dish_black_tonkotsu",
                    name = "Black Garlic Tonkotsu",
                    description = "18-hour pork bone broth, seared chashu belly, ajitama egg, wood ear mushrooms, mayu oil.",
                    priceCents = 1950,
                    category = "Signature Ramen",
                    veg = false,
                    bestseller = true,
                    calories = 740
                ),
                MenuItem(
                    id = "dish_spicy_miso_veg",
                    name = "Charred Miso Sesame Ramen",
                    description = "Roasted shiitake dashi, red miso tare, crispy tofu, sweet corn, chili threads.",
                    priceCents = 1750,
                    category = "Signature Ramen",
                    veg = true,
                    bestseller = true,
                    spicyLevel = 2,
                    calories = 610
                ),
                MenuItem(
                    id = "dish_pork_bao",
                    name = "Crispy Pork Belly Bao (2 pcs)",
                    description = "Steamed pillowy buns, hoisin glaze, pickled daikon, crushed roasted peanuts.",
                    priceCents = 1200,
                    category = "Small Plates",
                    veg = false,
                    calories = 490
                ),
                MenuItem(
                    id = "dish_yuzu_matcha",
                    name = "Kyoto Matcha Yuzu Cheesecake",
                    description = "Ceremonial Uji matcha Basque cheesecake with bright yuzu curd.",
                    priceCents = 900,
                    category = "Desserts",
                    veg = true,
                    calories = 360
                )
            )
        ),
        Restaurant(
            id = "rest_saffron_handi",
            name = "Saffron Handi House",
            cuisine = "Hyderabadi Dum Biryani • Tandoor",
            category = CuisineCategory.INDIAN,
            rating = 4.7,
            reviewCount = 1890,
            deliveryTimeMin = 25,
            deliveryTimeMax = 34,
            distanceKm = 2.0,
            priceForTwoCents = 3200,
            deliveryFeeCents = 149,
            pureVeg = false,
            promoTag = "20% OFF • LOCAL20",
            neighborhood = "Mission Terrace",
            kitchenAddress = "905 Polk St",
            hygieneBadge = "A+ Sealed Handi",
            imageRes = R.drawable.img_food_biryani,
            menu = listOf(
                MenuItem(
                    id = "dish_dum_biryani",
                    name = "Royal Chicken Dum Biryani",
                    description = "Aged basmati slow-sealed with saffron milk, mint, fried shallots, served with mirchi ka salan & raita.",
                    priceCents = 1890,
                    category = "Claypot Biryanis",
                    veg = false,
                    bestseller = true,
                    spicyLevel = 2,
                    calories = 780
                ),
                MenuItem(
                    id = "dish_jackfruit_biryani",
                    name = "Subz & Paneer Dum Biryani",
                    description = "Smoked malai paneer, charred seasonal vegetables, saffron rice, cashew-rose essence.",
                    priceCents = 1750,
                    category = "Claypot Biryanis",
                    veg = true,
                    bestseller = true,
                    spicyLevel = 1,
                    calories = 690
                ),
                MenuItem(
                    id = "dish_butter_masala",
                    name = "Charred Paneer Makhani & Naan",
                    description = "Velvety heirloom tomato fenugreek gravy with garlic butter tandoori naan.",
                    priceCents = 1650,
                    category = "Curries & Breads",
                    veg = true,
                    calories = 720
                ),
                MenuItem(
                    id = "dish_mango_lassi",
                    name = "Alphonso Saffron Lassi",
                    description = "Chilled artisanal yogurt blended with Ratnagiri mango puree and cardamom.",
                    priceCents = 650,
                    category = "Beverages",
                    veg = true,
                    calories = 260
                )
            )
        ),
        Restaurant(
            id = "rest_brass_smash",
            name = "Brass & Brioche Grill",
            cuisine = "Dry-Aged Smashburgers • Truffle Fries",
            category = CuisineCategory.BURGERS,
            rating = 4.6,
            reviewCount = 960,
            deliveryTimeMin = 16,
            deliveryTimeMax = 22,
            distanceKm = 0.8,
            priceForTwoCents = 2800,
            deliveryFeeCents = 99,
            pureVeg = false,
            promoTag = "UNDER 20 MIN",
            neighborhood = "Hayes Valley",
            kitchenAddress = "412 Octavia St",
            hygieneBadge = "Express Kitchen",
            imageRes = R.drawable.img_food_burger,
            menu = listOf(
                MenuItem(
                    id = "dish_double_wagyu",
                    name = "Double Wagyu Smash",
                    description = "Lacy seared double patties, aged Vermont cheddar, caramelized onions, house dill sauce on brioche.",
                    priceCents = 1650,
                    category = "Smashburgers",
                    veg = false,
                    bestseller = true,
                    calories = 810
                ),
                MenuItem(
                    id = "dish_crispy_shroom",
                    name = "Crispy Maitake Brioche Melt",
                    description = "Tempura-fried wild maitake cluster, smoked gouda, spicy herb slaw.",
                    priceCents = 1520,
                    category = "Smashburgers",
                    veg = true,
                    bestseller = true,
                    spicyLevel = 1,
                    calories = 650
                ),
                MenuItem(
                    id = "dish_truffle_fries",
                    name = "Rosemary Parmesan Fries",
                    description = "Twice-cooked kennebec potatoes, crispy rosemary, roasted garlic aioli.",
                    priceCents = 780,
                    category = "Sides",
                    veg = true,
                    calories = 460
                ),
                MenuItem(
                    id = "dish_salted_shake",
                    name = "Salted Caramel Espresso Shake",
                    description = "Jersey cream gelato spun with sea salt caramel and cold-brew reduction.",
                    priceCents = 750,
                    category = "Shakes",
                    veg = true,
                    calories = 440
                )
            )
        ),
        Restaurant(
            id = "rest_verdant_table",
            name = "Verdant Harvest Kitchen",
            cuisine = "100% Plant-Forward • Warm Grain Bowls",
            category = CuisineCategory.HEALTHY,
            rating = 4.8,
            reviewCount = 740,
            deliveryTimeMin = 18,
            deliveryTimeMax = 24,
            distanceKm = 1.3,
            priceForTwoCents = 2900,
            deliveryFeeCents = 149,
            pureVeg = true,
            promoTag = "PURE VEG • 15% OFF",
            neighborhood = "Noe Valley",
            kitchenAddress = "3918 24th St",
            hygieneBadge = "Organic Certified",
            imageRes = R.drawable.img_food_pizza,
            menu = listOf(
                MenuItem(
                    id = "dish_harvest_bowl",
                    name = "Miso Glazed Sweet Potato Bowl",
                    description = "Forbidden black rice, roasted delicata squash, avocado, crispy chickpeas, lemon tahini.",
                    priceCents = 1580,
                    category = "Warm Bowls",
                    veg = true,
                    bestseller = true,
                    calories = 540
                ),
                MenuItem(
                    id = "dish_pistachio_pesto",
                    name = "Charred Cauliflower Flatbread",
                    description = "Ancient grain flatbread, pistachio basil pesto, blistered heirloom tomatoes.",
                    priceCents = 1620,
                    category = "Artisan Flatbreads",
                    veg = true,
                    calories = 560
                ),
                MenuItem(
                    id = "dish_ginger_tonic",
                    name = "Turmeric Ginger Citrus Tonic",
                    description = "Cold-pressed Meyer lemon, raw ginger, wildflower honey, sparkling mineral water.",
                    priceCents = 580,
                    category = "Pressed Tonics",
                    veg = true,
                    calories = 95
                )
            )
        ),
        Restaurant(
            id = "rest_saffron_vegan",
            name = "Amrit Pure Veg Bistro",
            cuisine = "Pure Veg North Indian • Claypot Bowls",
            category = CuisineCategory.INDIAN,
            rating = 4.5,
            reviewCount = 610,
            deliveryTimeMin = 24,
            deliveryTimeMax = 32,
            distanceKm = 1.9,
            priceForTwoCents = 2600,
            deliveryFeeCents = 149,
            pureVeg = true,
            promoTag = "PURE VEG",
            neighborhood = "Sunset District",
            kitchenAddress = "1340 Irving St",
            hygieneBadge = "100% Veg Kitchen",
            imageRes = R.drawable.img_food_biryani,
            menu = listOf(
                MenuItem(
                    id = "dish_dal_bukhara",
                    name = "24-Hour Charcoal Dal & Truffle Kulcha",
                    description = "Slow-simmered black urad lentils finished with cultured butter and stuffed paneer kulcha.",
                    priceCents = 1640,
                    category = "Signature Mains",
                    veg = true,
                    bestseller = true,
                    calories = 710
                ),
                MenuItem(
                    id = "dish_avocado_chaat",
                    name = "Crispy Palak & Avocado Chaat",
                    description = "Flash-fried baby spinach leaves, whipped yogurt, pomegranate, tamarind date chutney.",
                    priceCents = 1120,
                    category = "Street Plates",
                    veg = true,
                    bestseller = true,
                    spicyLevel = 1,
                    calories = 380
                )
            )
        )
    )

    fun findById(id: String): Restaurant? = restaurants.firstOrNull { it.id == id }
}
