package com.example.game

/**
 * Compile-time feature switches. One place to find every "not yet, but soon" toggle, so shipping
 * a feature is flipping a flag here rather than hunting the call sites that gate it.
 */
object Features {

    /**
     * The storefront "REMOVE ADS" button in the main menu header.
     *
     * Off until launch: the button is real and takes real money, and a half-finished build is not
     * where you want a player to succeed at paying you. The billing wiring stays live behind it —
     * only the entry point is hidden, so flipping this to `true` is the whole release step.
     *
     * Note this does NOT govern whether ads themselves show; that is AdGate.adsAllowedNow().
     */
    const val SHOW_REMOVE_ADS_BUTTON = false
}
