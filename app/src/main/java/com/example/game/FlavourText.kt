package com.example.game

import kotlin.random.Random

enum class Perf { FLAWLESS, STANDARD, PYRRHIC }
enum class BarkKind { VICTORY, DEFEAT, LEVEL_UP }

object FlavourText {
    internal val ADJ = listOf(
        "Muddy", "Lamentable", "Glorious", "Soggy", "Thunderous", "Ignoble", "Spirited", "Grievous",
        "Merry", "Dreadful", "Unseemly", "Valiant", "Wretched", "Bloody-Minded", "Ill-Advised",
        "Damp", "Peevish", "Boisterous", "Calamitous", "Reckless", "Undignified", "Hasty",
        "Stubborn", "Woeful", "Rowdy", "Bruising", "Graceless", "Frantic", "Sullen", "Gallant",
        "Ruinous", "Unholy", "Ferocious", "Squalid", "Pitiless", "Ragged", "Fractious", "Solemn"
    )
    internal val NOUN = listOf(
        "Kerfuffle", "Scuffle", "Fracas", "Melee", "Tussle", "Affray", "Reckoning", "Set-To",
        "Rumpus", "Donnybrook", "Barney", "Ruckus", "Brawl", "Skirmish", "Altercation", "Punch-Up",
        "Contretemps", "Disagreement", "Bother", "Shambles", "Squabble", "Rout", "Stramash",
        "Misunderstanding", "Dust-Up", "Bust-Up", "Hullabaloo", "Argy-Bargy", "Business", "Debacle",
        "Unpleasantness", "To-Do", "Carry-On", "Free-For-All", "Slaughter", "Contest"
    )
    internal val PLACE = listOf(
        "Pevensey Marsh", "Hastings Field", "Senlac Ridge", "the Old Mill", "Caldbec Hill",
        "the Salt Fen", "Bulverhythe", "the Orchard", "Telham Down", "the Fish Market",
        "the Duck Pond", "Crowhurst", "the Turnip Field", "Ninfield", "the Tanner's Yard",
        "Whatlington", "the Sheep Fold", "Battle Abbey", "the Pig Pen", "Netherfield",
        "the Ford", "Winchelsea", "the Millpond", "Ashburnham", "the Goose Green",
        "the Bishop's Vineyard", "Herstmonceux", "the Charcoal Burn", "the Broken Bridge",
        "the Abbot's Cabbages", "Icklesham", "the Wash-House", "Fairlight", "the Hollow Way",
        // Not on any map of Sussex, and all the better for it
        "Wallopham Down", "Knucklington Parish", "Bashington Underbelt",
        "the Field of Disagreement"
    )

    /** Half the places are "the Old Mill" — they need a capital when they open the sentence. */
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /** "An Unholy Bust-Up", not "A Unholy Bust-Up". */
    private fun an(word: String) = if (word.first().lowercaseChar() in "aeiou") "An" else "A"

    /**
     * Sentence patterns, not just words. The vocabulary was already large; every battle still read
     * "The X Y at Z", which is what made them feel the same.
     */
    private val NAME_PATTERNS: List<(String, String, String) -> String> = listOf(
        { adj, noun, place -> "The $adj $noun at $place" },
        { _, noun, place -> "The $noun of ${place.removePrefix("the ")}" },
        { adj, noun, place -> "${cap(place)}: ${an(adj)} $adj $noun" },
        { adj, noun, place -> "The $adj $noun of ${place.removePrefix("the ")}" },
        { adj, _, place -> "That $adj Business at $place" },
        { adj, noun, place -> "A Most $adj $noun near $place" }, // "A Most" always: the article agrees with "Most"
        { _, noun, place -> "The $noun at $place, As Sung By Monks" },
        { adj, noun, _ -> "The $adj $noun (Location Disputed)" },
        { adj, noun, place -> "$noun at $place, Widely Called $adj" }
    )
    private val L_VERB = listOf("PERCUSSIT", "TUMULTUAVIT", "CLAMAVIT", "BRAWLAVIT", "SMASHIVIT", "WALLOPAVIT")
    private val L_PLACE = listOf("HASTINGAM", "PEVENSAE", "SENLACUM", "MOLENDINUM", "COLLEM CALDBEC", "FORUM PISCIUM")
    private val L_HERO = listOf("WILLELMUS", "MILES NOSTER", "EQUES PARVUS", "BRAWLERUS")

    private fun rng(seed: Long, level: Int) = Random(seed * 31L + level * 7L)

    fun battleName(seed: Long, level: Int): String {
        val r = rng(seed, level)
        val adj = ADJ[r.nextInt(ADJ.size)]
        val noun = NOUN[r.nextInt(NOUN.size)]
        val place = PLACE[r.nextInt(PLACE.size)]
        return NAME_PATTERNS[r.nextInt(NAME_PATTERNS.size)](adj, noun, place)
    }

    fun latinHeadline(seed: Long, level: Int): String {
        val r = rng(seed, level * 13 + 1)
        return "HIC ${L_HERO[r.nextInt(L_HERO.size)]} ${L_VERB[r.nextInt(L_VERB.size)]} AD ${L_PLACE[r.nextInt(L_PLACE.size)]}"
    }

    fun latinHeadline(seed: Long, level: Int, boss: BossType?): String =
        if (boss == null) latinHeadline(seed, level)
        else "HIC ${boss.latinName} PRO CORONA PUGNAT"

    private val VICTORY_FLAWLESS = listOf(
        "Not a scratch upon thee! The chroniclers shall struggle to make this sound difficult.",
        "A flawless rout! Even the tapestry weavers gasped.",
        "Untouched and unbothered. Harold's men are filing a complaint."
    )
    private val VICTORY_STANDARD = listOf(
        "Thy valiant knight hath carried the field! The score multiplier did its glorious work.",
        "The field is thine! Somewhere, a monk writes this down approvingly.",
        "Victory! The geese of Pevensey honk thy name."
    )
    private val VICTORY_PYRRHIC = listOf(
        "Victory - though thy surgeon requests a word. And bandages.",
        "Won by a whisker! The tapestry shall depict thee limping heroically.",
        "The day is thine, barely. Perhaps sturdier armour next time?"
    )

    fun victoryQuote(seed: Long, level: Int, perf: Perf): String {
        val pool = when (perf) { Perf.FLAWLESS -> VICTORY_FLAWLESS; Perf.STANDARD -> VICTORY_STANDARD; Perf.PYRRHIC -> VICTORY_PYRRHIC }
        return pool[rng(seed, level * 17 + 3).nextInt(pool.size)]
    }

private val DEFEAT = listOf(
        "\"Time and tide wait for no man.\"\n- Geoffrey Chaucer",
        "\"All good things must come to an end.\"\n- Geoffrey Chaucer",
        "\"Patience is a conquering virtue.\"\n- Geoffrey Chaucer",
        "\"Nothing ventured, nothing gained.\"\n- Geoffrey Chaucer",
        "\"He who falls in the mud may yet rise smelling of glory. Eventually.\"\n- A Passing Monk",
        "\"The arrow finds the knight who forgets to duck.\"\n- Wace, probably",
        "\"It is no shame to fall. It is shame only to lie there complaining.\"\n- The Venerable Bede (apocryphal)",
        "\"Get up. The tapestry looks better with thee in it.\"\n- Ye Olde Proverbe",
        "\"The life so short, the craft so long to learn.\"\n- Geoffrey Chaucer",
        "\"I am shaved as close as any friar.\"\n- Geoffrey Chaucer",
        "\"Lost money is not lost beyond recall, but loss of time brings on the loss of all.\"\n- Geoffrey Chaucer",
        "\"Since I from Love escaped am so fat, I never think to be in his prison lean.\"\n- Geoffrey Chaucer",
        "\"Thank God, it will soon be dark.\"\n- Anonymous Scribe (Marginalia, 9th Century)",
        "\"This parchment is hairy.\"\n- Anonymous Monk (Marginalia)",
        "\"I wrote this sitting like a vulture.\"\n- Anonymous Scribe (Marginalia)",
        "\"Cursed be the pesty cat that urinated over this book during the night.\"\n- Anonymous Monk (Marginalia, 15th Century)",
        "\"New parchment, bad ink; I say nothing more.\"\n- Anonymous Scribe (Marginalia)",
        "\"Let me now be blamed for the script, for the ink is bad, and the vellum defective, and the day is dark.\"\n- Anonymous Irish Monk (Marginalia)",
        "\"The whole ends. Pour, and give me drink. Hallelujah.\"\n- Anonymous Scribe (Marginalia)",
        "\"A marsh of stinking and stagnant mud.\" [on the fens]\n- William of Malmesbury",
        "\"If gold rusts, what then can iron do?\"\n- Geoffrey Chaucer",
        "\"No empty handed man can lure a bird.\"\n- Geoffrey Chaucer",
        "\"Woe to the cook whose sauce has no sting.\"\n- Geoffrey Chaucer",
        "\"People are so impressionable, they can die of imagination.\"\n- Geoffrey Chaucer",
        "\"St. Patrick of Armagh, deliver me from writing.\"\n- Anonymous Scribe (Marginalia)",
        "\"Writing is excessive drudgery. It crooks your back, it dims your sight, it twists your stomach and your sides.\"\n- Anonymous Scribe (Marginalia)",
        "\"As the harbor is welcome to the sailor, so is the last line to the scribe.\"\n- Anonymous Monk (Marginalia)",
        "\"Oh, my hand.\"\n- Anonymous Scribe (Marginalia)",
        "\"While I wrote I froze, and what I could not write by the beams of the sun I finished by candlelight.\"\n- Anonymous Scribe (Marginalia)",
        "\"Now I've written the whole thing give me a drink!\"\n- Anonymous Monk (Marginalia)",
        "\"The dread of enemies, a woman of enlarged soul.\"\n- William of Malmesbury",
        "\"Thou art come! A matter of lamentation to many a mother art thou come!\" [yelling at a comet]\n- Eilmer of Malmesbury (via William of Malmesbury)",
        "\"Whatever evil or malicious thing that can be found in any part of the world, you will find in that one city.\" [on London]\n- Richard of Devizes",
        "\"There is a lake in that region which makes anyone who bathes in it completely bald.\"\n- Gerald of Wales",
        "\"A pig of enormous size, which they say had thirty teeth.\"\n- Matthew Paris",
        "\"I am entirely consumed by the fleas.\"\n- Anonymous Monk (Marginalia)",
        "\"To the devil with this pen!\"\n- Anonymous Scribe (Marginalia)",
        "\"I am very cold.\"\n- Anonymous Scribe (Marginalia)",
        "\"A great and terrible thunder was heard, so that men thought the end of the world was come.\"\n- Orderic Vitalis",
        "\"The water of this spring has a strange property... whoever washes in it, his hair turns instantly white.\"\n- Gerald of Wales"
    )
    fun defeatQuote(): String = DEFEAT[Random.nextInt(DEFEAT.size)]

    /**
     * The epitaph: "Slain by Cerdic the Immovable, wielding a Dane Axe on a Hickory Shaft."
     * Shared by the defeat panel and the shareable tale so the two never disagree.
     * Null when nothing was recorded — a retirement, or a death with no author.
     */
    fun slainByLine(name: String?, weapon: String?): String? = when {
        name != null && weapon != null -> "Slain by $name, wielding $weapon."
        name != null -> "Slain by $name."
        weapon != null -> "Felled by $weapon."
        else -> null
    }

    private val BARK_VICTORY = listOf("VICTORIA!", "DEUS VULT!", "GLORIA MAXIMA!", "HUZZAH ETERNUM!")
    private val BARK_DEFEAT = listOf("MORTIS!", "LACRIMAE!", "O TEMPORA!", "CATASTROPHUS!")
    private val BARK_LEVEL = listOf("ASCENDIMUS!", "GLORIA CRESCIT!", "SURSUM CORDA!")

    fun bark(kind: BarkKind, seed: Long, level: Int): String {
        val pool = when (kind) { BarkKind.VICTORY -> BARK_VICTORY; BarkKind.DEFEAT -> BARK_DEFEAT; BarkKind.LEVEL_UP -> BARK_LEVEL }
        return pool[rng(seed, level * 23 + kind.ordinal).nextInt(pool.size)]
    }

    val POPUPS_NORMAL = listOf("CLANGUS", "THWACKUS", "BONKUS", "WALLOPUS", "SMACKETH", "CLONKUS")
    val POPUPS_CRIT = listOf("MAXIMUS CRUNCHIMUS", "ULTIMA BONKA", "CATASTROPHUS TOTALIS")
}
