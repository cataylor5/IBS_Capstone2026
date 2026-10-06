package io.github.cataylor5.ibs

data class Restroom(
    // This describes the information we currently need for one restroom.

    val id: String,
    // This gives each restroom a consistent identifier.

    val name: String,
    // This holds the name displayed on its card.

    val accessType: String,
    // This identifies public, customers-only, or unknown access.

    val feeRequired: Boolean?
    // This is true for a fee, false for no fee, or null when unknown.
)

val sampleRestrooms = listOf(
    // These are fictional records used while we build the interface.

    Restroom(
        id = "demo-library",
        name = "Demo Library",
        accessType = "public",
        feeRequired = false
    ),

    Restroom(
        id = "demo-cafe",
        name = "Demo Café",
        accessType = "customers_only",
        feeRequired = false
    ),

    Restroom(
        id = "demo-transit",
        name = "Demo Transit Center",
        accessType = "public",
        feeRequired = true
    )
)