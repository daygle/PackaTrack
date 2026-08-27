package com.packatrack.core.model

/** Normalized parcel status shown to the user. */
enum class ShipmentStatus(val isFinal: Boolean) {
    LABEL_CREATED(false),
    IN_TRANSIT(false),
    OUT_FOR_DELIVERY(false),
    PICKUP_AVAILABLE(false),
    DELIVERED(true),
    EXCEPTION(false),
    UNKNOWN(false);

    companion object {
        fun fromCode(code: String?): ShipmentStatus =
            when {
                code == null -> UNKNOWN
                code.equals("DELIVERED", true) -> DELIVERED
                code.equals("PICKUP_AVAILABLE", true) -> PICKUP_AVAILABLE
                code.equals("OUT_FOR_DELIVERY", true) -> OUT_FOR_DELIVERY
                code.startsWith("EXCEPTION", true) -> EXCEPTION
                code.equals("IN_TRANSIT", true) ||
                    code.equals("ARRIVED", true) ||
                    code.equals("DEPARTED", true) ||
                    code.equals("ACCEPTED", true) -> IN_TRANSIT
                code.equals("LABEL_CREATED", true) -> LABEL_CREATED
                else -> UNKNOWN
            }
    }
}
