package com.example.game

/**
 * Things earned by playing, kept forever. Each fires once — GameProfile.clearedMilestones is what
 * makes that true, since a level-threshold check would otherwise re-fire every single run.
 *
 * [condition] is shown to the player on locked rows. Never leave it vague: a trophy nobody can
 * work out how to earn is not progression, it is a locked door.
 */
enum class Milestone(
    val id: String,
    val label: String,
    val condition: String,
    val grants: String
) {
    REACH_5("reach_level_5", "Off the Beach", "Reach level 5", "handle_oar"),
    FIRST_SIEGE("first_siege", "Breaker of Gates", "Clear your first siege", "handle_plank"),
    BEAT_HAROLD("beat_harold", "The Eye of the King", "Defeat Harold Godwinson", "handle_antler"),
    // Per-run, not lifetime: siegesClearedThisRun resets with the run, so say so plainly.
    THREE_SIEGES("three_sieges", "Castellan", "Clear three sieges in one run", "handle_wheelbarrow"),
    BEAT_HARDRADA("beat_hardrada", "Stamford Bridge", "Defeat Harald Hardrada", "handle_anchor"),
    BEAT_GIANT("beat_giant", "Giant-Slayer", "Defeat Gog or Magog", "handle_femur"),
    BEAT_WILLIAM("beat_william", "The Usurper", "Defeat William the Bastard", "handle_trumpet"),

    // The Strange Relics already exist and are already drawn; earning one makes it selectable
    // rather than attachment-only.
    NAKED_WIN("naked_win", "Shameless", "Win a battle wearing no armour", "head_eel"),
    REACH_20("reach_level_20", "Seasoned", "Reach level 20", "head_cheese"),
    FIVE_HUNDRED_KILLS("five_hundred_kills", "The Long Harvest", "Slay 500 men in all", "head_goose"),
    BARE_FISTED_BOSS("bare_fisted_boss", "Bare-Knuckle Saint", "Defeat any boss with your fists", "head_femur")
}
