package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.random.Random

// --- ITEM TYPES & DEFINITIONS ---

enum class ItemType {
    WEAPON_HEAD, WEAPON_HANDLE, SHIELD, ARMOR, HEADGEAR
}

data class SizePreset(
    val id: String,
    val label: String,
    val size: Float,
    val description: String
)

val SIZE_PRESETS = listOf(
    SizePreset("tiny",   "Wee Runt",        0.65f, "Fastest. Surprising survivability. Damage: ×0.42"),
    SizePreset("small",  "Nimble Scout",     0.80f, "Quick, evasive, and scrappy. Damage: ×0.64"),
    SizePreset("medium", "Average Norman",   1.00f, "Balanced. The default Hastings experience."),
    SizePreset("large",  "Burly Knight",     1.20f, "Slow but hits hard. Damage: ×1.44. Tankier."),
    SizePreset("huge",   "ABSOLUTE UNIT",    1.45f, "Glacial. Damage: ×2.10. Walking siege tower.")
)

enum class EnemyArchetype {
    FYRD_LEVY, HOUSECARL, ARCHER, SHIELD_WALL, BERSERKER, CAVALRY, CHAMPION,
    // 10 New archetypes
    PEASANT, SLINGER, JAVELINEER, MACEMAN, PIKEMAN,
    KNIGHT_DISMOUNTED, CHARIOT_ARCHER, CHARIOT_LANCER, LORD, KING
}

enum class Ancillary(
    val id: String,
    val ancillaryName: String,
    val role: String, // e.g., "Squire", "Herald", "Trumpeter", "Cupbearer"
    val description: String,
    val hpBoost: Float = 0f,
    val speedBoost: Float = 0f,
    val color: Color
) {
    SQUIRE("anc_squire", "Baldrick", "Squire", "A useless but enthusiastic lad carrying your spare tunics.", hpBoost = 20f, color = Color(0xFF539462)),
    HERALD("anc_herald", "Sir Boast-a-lot", "Herald", "Announces your presence loudly. Intimidates peasants.", speedBoost = 0.2f, color = Color(0xFFB03131)),
    TRUMPETER("anc_trumpeter", "Tooty", "Trumpeter", "Plays off-key trumpet blasts during battle.", hpBoost = 10f, speedBoost = 0.1f, color = Color(0xFFD6A420)),
    CROSSBOWMAN("anc_crossbowman", "Gaston", "Crossbowman", "Slow but devastating ranged cover fire. Pierces mail.", hpBoost = 0f, speedBoost = 0f, color = Color(0xFF3B2F2F)),
    WARHORSE("anc_mount_horse", "Blanche", "Destrier", "A towering Norman warhorse. Grants massive speed and HP.", hpBoost = 80f, speedBoost = 0.5f, color = Color(0xFF452E1B)),
    CUPBEARER("anc_cupbearer", "Geoffrey", "Cupbearer", "Refills your goblet with fine wine mid-swing.", hpBoost = 40f, speedBoost = -0.1f, color = Color(0xFF632873)),
    ARCHER("anc_archer", "Robin", "Longbowman", "Fires covering arrows into the fray. Just mind your back.", hpBoost = 5f, speedBoost = 0f, color = Color(0xFF4C613D)),
    MONK("anc_monk", "Brother Tuck", "Monk", "Blesses you with holy incense. Smells heavenly.", hpBoost = 30f, speedBoost = 0f, color = Color(0xFF5E4B3C)),
    FANATIC("anc_fanatic", "Mad Boris", "Fanatic", "A screaming madman who charges the enemy naked with a huge axe.", hpBoost = 0f, speedBoost = 0f, color = Color(0xFFC02020)),
    CHARIOT("anc_mount_chariot", "The Rattler", "Chariot", "A sturdy wooden chariot. Faster and deadlier than a horse, but hard to turn.", hpBoost = 100f, speedBoost = 0.6f, color = Color(0xFF8B5A2B)),
    STILTS("anc_mount_stilts", "Long Shanks", "Stilts", "Tall wooden poles. Elevates you above the common rabble.", hpBoost = -10f, speedBoost = -0.2f, color = Color(0xFFC2A077)),
    HAG("anc_hag", "Old Maud", "Hag", "Spawns in your backline, lobs mud, applies slow and minor poison.", hpBoost = 0f, speedBoost = 0f, color = Color(0xFF384033)),
    TROJAN_HORSE("anc_trojan_horse", "The Great Horse", "Decoy", "Rolls forward, absorbs hits, spawns 3 knights on death.", hpBoost = 200f, speedBoost = 0f, color = Color(0xFF8B5A2B)),
    LIL_GUY("anc_lil_guy", "Lil Guy", "Backpack Slinger", "Renders on your back, passively shoots projectiles.", hpBoost = 5f, speedBoost = 0f, color = Color(0xFFC78440)),
    SURGEON("anc_surgeon", "Sawbones Silas", "Surgeon", "Spawns behind you, applies passive slow HP regen.", hpBoost = 10f, speedBoost = 0f, color = Color(0xFF801A1A)),
    RAVEN("anc_raven", "Munin", "Raven", "Rests on your off-hand, flies out to peck enemy eyes, blinding them.", hpBoost = 0f, speedBoost = 0f, color = Color.Black),
    WARDOG("anc_wardog", "Buster", "Wardog", "Charges fast, bites enemies, hard to hit, sometimes trips them.", hpBoost = 25f, speedBoost = 0.2f, color = Color(0xFF452E1B)),
    PLAGUE_PEASANT("anc_plague_peasant", "Wretched Aldwin", "Plague-Bearer", "A dying peasant who sprints at the foe. His pestilence spreads to ALL who come near — there is a small chance YOU catch it too.", hpBoost = 0f, speedBoost = 0f, color = Color(0xFF6B7D4A)),
    GREASER("anc_greaser", "Slippery Sam", "Greaser", "Lobs pots of rendered fat from your backline. Foes skid over and flounder in the muck.", hpBoost = 0f, speedBoost = 0f, color = Color(0xFFD9C77A))
}

/** Divine intervention, called down from the tapestry border once per battle-ish. */
enum class DivineWeather(val id: String, val label: String, val description: String) {
    LIGHTNING("weather_lightning", "Divine Bolt", "The heavens smite your mightiest foe."),
    FLOOD("weather_flood", "The Deluge", "A wall of water sweeps enemies from the field."),
    HAIL("weather_hail", "Hailstorm", "Fist-sized hail batters every foe to the ground."),
    FROST("weather_frost", "Killing Frost", "Ice underfoot — the enemy host slips and falls.")
}

interface GearItem {
    val id: String
    val itemName: String
    val type: ItemType
    val mass: Float          // kg
    val defense: Float   // flat damage block or % reduction
    val pierce: Float    // Armor penetration
    val slash: Float     // Slashing damage
    val blunt: Float     // Bludgeoning damage
    val speedPenalty: Float // Reduction in attack/move speed
    val reach: Float     // Melee range
    val isRanged: Boolean
    val description: String
    val color: Color
}

object GameData {
    enum class WeaponHead(
        override val id: String,
        override val itemName: String,
        override val mass: Float,
        override val defense: Float = 0f,
        override val pierce: Float = 0f,
        override val slash: Float = 0f,
        override val blunt: Float = 0f,
        override val speedPenalty: Float = 0f,
        override val reach: Float = 0f,
        override val isRanged: Boolean = false,
        override val description: String,
        override val color: Color = Color.Gray
    ) : GearItem {
        BARE("head_bare", "Fists of Fury", 0.0f, blunt = 5f, reach = 1.0f, description = "No weapon head, just plain bare Saxon-bashing knuckles.", color = Color(0xFFCBB69E)),
        PIKE("head_pike", "Crude Pike Head", 1.2f, pierce = 18f, reach = 2.5f, description = "A sharp leaf-shaped iron point. Splendid for keeping foes at bay.", color = Color(0xFF8C969E)),
        AXE("head_axe", "Bearded Axe Head", 2.5f, slash = 22f, blunt = 6f, reach = 1.5f, description = "An iconic curved axe head designed to hook shields and cleave mail.", color = Color(0xFF7A868C)),
        SWORD("head_sword", "Spatha Sword Blade", 1.0f, slash = 16f, pierce = 10f, reach = 1.6f, description = "A carefully pattern-welded iron sword blade. Rare and prestigious.", color = Color(0xFFA6B0B5)),
        MORNINGSTAR("head_morningstar", "Spiked Morningstar", 3.2f, blunt = 15f, pierce = 12f, reach = 1.3f, description = "A brutal iron ball adorned with crude spikes. Hard to swing, devastating to meet.", color = Color(0xFF535C61)),
        MAUL("head_maul", "Heavy Iron Mallet", 4.5f, blunt = 25f, reach = 1.2f, description = "A giant chunk of lead and iron. Turns Anglo-Saxon mail into metallic soup.", color = Color(0xFF4A4E51)),
        SPEAR("head_spear", "Leaf Spearpoint", 0.8f, pierce = 14f, reach = 2.2f, description = "Lightweight thrusting spearhead. A staple of Hastings shoreline skirmishes.", color = Color(0xFF909BA0)),
        DAGGER("head_dagger", "Seax Dagger Blade", 0.4f, slash = 8f, pierce = 8f, reach = 0.8f, description = "A short single-edged saxon knife. Quick, sneaky, but dangerously short range.", color = Color(0xFF788387)),
        BOW("head_bow", "Yew Shortbow", 0.9f, pierce = 15f, reach = 8.0f, isRanged = true, description = "A simple curved wooden bow. Shooteth cloth-yard shafts across the field.", color = Color(0xFF947B5C)),
        SLINGSHOT("head_slingshot", "Shepherd Slingshot", 0.2f, blunt = 10f, reach = 6.0f, isRanged = true, description = "A leather pouch for hurling pebbles. Humble, lightweight, and surprisingly painful.", color = Color(0xFF8A7156)),
        HALBERD("head_halberd", "Halberd Blade", 2.8f, slash = 20f, pierce = 15f, reach = 2.0f, description = "An elegant combination of axe and spike. Devastating from a distance.", color = Color(0xFF8C969E)),
        FLAIL("head_flail", "Spiked Flail", 2.0f, blunt = 18f, pierce = 5f, reach = 1.5f, description = "A spiked ball on a chain. Unpredictable and hard to block.", color = Color(0xFF535C61)),
        LONGBOW("head_longbow", "Welsh Longbow", 1.2f, pierce = 25f, reach = 10.0f, isRanged = true, description = "A massive yew bow that can punch through chainmail at long range.", color = Color(0xFF5D4831)),
        CLAYMORE("head_claymore", "Highland Claymore", 3.5f, slash = 30f, pierce = 10f, reach = 2.0f, description = "A massive two-handed sword. Cleaves shields in twain.", color = Color(0xFF9AA0A3)),
        SCYTHE("head_scythe", "War Scythe", 2.2f, slash = 25f, pierce = 10f, reach = 2.5f, description = "A farmer's tool turned lethal weapon. Reaches around shields.", color = Color(0xFF6C7175)),
        CROSSBOW("head_crossbow", "Heavy Crossbow", 1.5f, pierce = 65f, reach = 12.0f, isRanged = true, description = "A mechanical bow. High armor piercing and fast to crank.", color = Color(0xFF4A3B2C)),
        MACE("head_mace", "Iron Mace", 2.0f, blunt = 20f, pierce = 3f, reach = 1.4f, description = "A simple but devastating flanged mace. Crushes helmets.", color = Color(0xFF636A6E)),
        JAVELIN("head_javelin", "Throwing Javelin", 0.6f, pierce = 12f, reach = 7.0f, isRanged = true, description = "A light throwing spear. Short range for a missile, but fast.", color = Color(0xFF8C969E)),
        WAR_FLAIL("head_war_flail", "Twin War Flail", 3.5f, blunt = 22f, pierce = 8f, reach = 1.8f, description = "Two spiked balls on branching chains. Absolute chaos.", color = Color(0xFF535C61)),
        BROADSWORD("head_broadsword", "Broadsword Blade", 1.4f, slash = 18f, pierce = 8f, reach = 1.5f, description = "A wide, brutal iron blade. Chips bones through mail.", color = Color(0xFF949B9E)),
        PITCHFORK("head_pitchfork", "Pitchfork", 1.1f, pierce = 16f, slash = 2f, reach = 2.1f, description = "Three rusty tines. Perfect for hay or heathen flesh.", color = Color(0xFF817A73)),
        DAGGER_HILT("head_dagger_hilt", "Pommel", 0.3f, blunt = 12f, reach = 0.6f, description = "Ending them rightly with a solid iron pommel.", color = Color(0xFFC4AD6C)),
        LUCERNE("head_lucerne", "Lucerne Hammer", 3.0f, blunt = 22f, pierce = 18f, reach = 2.3f, description = "A horrific combination of beak and hammer. Punctures anything.", color = Color(0xFF7D838A)),
        SABER("head_saber", "Huge Saber", 1.8f, slash = 28f, pierce = 5f, reach = 1.8f, description = "A massive curved blade sweeping across the battlefield.", color = Color(0xFF909BA0)),
        URUMI("head_urumi", "Urumi Whip", 1.5f, slash = 30f, pierce = 5f, reach = 2.5f, description = "A flexible whip-like sword with multiple blades. Unpredictable and deadly.", color = Color(0xFFA6B0B5)),
        WINGED_AXE("head_winged_axe", "Winged Axe", 2.8f, slash = 25f, blunt = 8f, reach = 1.6f, description = "A heavy axe with side wings to prevent over-penetration.", color = Color(0xFF7A868C)),
        SPIKED_MACE("head_spiked_mace", "Spiked Mace", 2.5f, blunt = 22f, pierce = 8f, reach = 1.4f, description = "A mace adorned with vicious iron spikes.", color = Color(0xFF535C61)),
        BASIC_CLUB("head_club", "Basic Club", 1.8f, blunt = 15f, reach = 1.3f, description = "A crude wooden club. Cheap and surprisingly effective.", color = Color(0xFF8A5E38)),
        SAW_1("head_saw_1", "Bone Saw", 1.2f, slash = 18f, pierce = 2f, reach = 1.2f, description = "A crude saw meant for bone, repurposed for Saxon flesh.", color = Color(0xFF8C969E)),
        SAW_2("head_saw_2", "Lumber Saw", 2.0f, slash = 28f, pierce = 5f, reach = 1.8f, description = "A massive two-man saw wielded by a single lunatic.", color = Color(0xFF909BA0));
        override val type: ItemType get() = ItemType.WEAPON_HEAD
    }
    val WEAPON_HEADS = WeaponHead.values().toList()

    enum class WeaponHandle(
        override val id: String,
        override val itemName: String,
        override val mass: Float,
        override val defense: Float = 0f,
        override val pierce: Float = 0f,
        override val slash: Float = 0f,
        override val blunt: Float = 0f,
        override val speedPenalty: Float = 0f,
        override val reach: Float = 0f,
        override val isRanged: Boolean = false,
        override val description: String,
        override val color: Color = Color.Gray
    ) : GearItem {
        FISTS("handle_fists", "Bare Wrists", 0.0f, speedPenalty = 0.0f, description = "Just your hairy hands.", color = Color(0xFFCBB69E)),
        SHORT("handle_short", "Short Ash Grip", 0.4f, speedPenalty = -0.05f, description = "A 1-foot wooden haft. Fast swings, low mass.", color = Color(0xFF9C7D58)),
        MEDIUM("handle_medium", "Hickory Shaft", 0.8f, reach = 0.3f, speedPenalty = 0.05f, description = "A sturdy medium wooden handle. Balanced and reliable.", color = Color(0xFF8C6F47)),
        LONG("handle_long", "Long Ash Pole", 1.8f, reach = 1.2f, speedPenalty = 0.2f, description = "A lengthy 6-foot spear haft. Drastically increases reach but is slow to turn.", color = Color(0xFF735835)),
        IRON("handle_iron", "Iron-shod Haft", 2.5f, reach = 0.5f, speedPenalty = 0.15f, description = "A heavy, iron-reinforced shaft. Hits harder but swings slower.", color = Color(0xFF636A6E)),
        WHEEL("handle_wheel", "Cart Wheel", 3.5f, reach = 0.6f, speedPenalty = 0.4f, blunt = 15f, description = "A literal wooden cart wheel as a handle. Ludicrously heavy, but incredible momentum.", color = Color(0xFF6E5536)),
        PICK("handle_pick", "Mining Pick Handle", 1.2f, reach = 0.4f, speedPenalty = 0.08f, description = "An angled wooden pick handle. Grants weird but effective striking angles.", color = Color(0xFF7A654C)),
        CHAIN("handle_chain", "Bayeux Iron Chain", 1.5f, reach = 0.8f, speedPenalty = 0.25f, description = "An iron chain linking your grip to the weapon. Swings wildly in a floppy arc! Slower, but hits with high momentum.", color = Color(0xFF4C5154)),
        DOUBLE_ENDED("handle_double_ended", "Double-Ended Pole", 2.0f, reach = 1.0f, speedPenalty = 0.35f, description = "A wooden pole allowing heads on BOTH ends! Slower, but covers both ends and deals 1.5x damage.", color = Color(0xFF5D4831)),
        FLAIL_CHAIN("handle_flail_chain", "Flail Chain", 1.2f, reach = 1.0f, speedPenalty = 0.30f, description = "A short iron chain with a swivel joint. Makes any head a flail. Bypasses shields.", color = Color(0xFF4C5154)),
        BLESSED_BRANCH("handle_blessed_branch", "Blessed Branch", 1.0f, reach = 0.8f, speedPenalty = 0.05f, description = "A twisted branch blessed by the monks. Smites heathens with holy splinters.", color = Color(0xFF8A5E38)),
        STUMP("handle_stump", "Tree Stump", 5.0f, reach = 0.5f, speedPenalty = 0.6f, blunt = 20f, description = "An entire tree stump. Hilariously heavy and completely impractical.", color = Color(0xFF5E4B3C)),
        RAM("handle_ram", "Battering Ram", 8.0f, reach = 1.5f, speedPenalty = 0.8f, blunt = 35f, description = "A solid iron-capped ram log. Swings with glacial speed but catastrophic force.", color = Color(0xFF452E1B)),
        PLOUGH("handle_plough", "Plough Shaft", 3.0f, reach = 1.2f, speedPenalty = 0.25f, blunt = 10f, description = "The splintered wooden shaft of a farming plough.", color = Color(0xFF735835)),
        DAGGER("handle_dagger", "Dagger Grip", 0.2f, reach = 0.1f, speedPenalty = -0.25f, description = "A stubby leather-bound grip. Blindingly fast strikes, but you must be nose-to-nose to land them.", color = Color(0xFF6B4A33)),
        PIKE_HANDLE("handle_pike_long", "Pike Handle", 3.2f, reach = 2.4f, speedPenalty = 0.55f, description = "A ludicrously long 12-foot pole. Glacial charge-up, but with a spearpoint it skewers whole ranks of Saxons at once.", color = Color(0xFF6E5536));
        override val type: ItemType get() = ItemType.WEAPON_HANDLE
    }
    val WEAPON_HANDLES = WeaponHandle.values().toList()

    enum class Shield(
        override val id: String,
        override val itemName: String,
        override val mass: Float,
        override val defense: Float = 0f,
        override val pierce: Float = 0f,
        override val slash: Float = 0f,
        override val blunt: Float = 0f,
        override val speedPenalty: Float = 0f,
        override val reach: Float = 0f,
        override val isRanged: Boolean = false,
        override val description: String,
        override val color: Color = Color.Gray
    ) : GearItem {
        NONE("shield_none", "No Shield (Two-Handing)", 0.0f, description = "Wield nothing in the off-hand. High vulnerability, double weapon speed!", color = Color.Transparent),
        BUCKLER("shield_buckler", "Saxon Buckler", 1.5f, defense = 15f, speedPenalty = 0.02f, description = "A small wooden fist-shield with an iron boss. Lightweight parrying tool.", color = Color(0xFFAC8054)),
        KITE("shield_kite", "Norman Kite Shield", 4.5f, defense = 45f, speedPenalty = 0.12f, description = "The iconic teardrop shield. Excellent leg protection. Painted with bold stripes.", color = Color(0xFF9E3624)),
        TOWER("shield_tower", "Saxon Wall Shield", 8.0f, defense = 75f, speedPenalty = 0.28f, description = "A massive wooden shield used in shield walls. Heavy as an iron gate.", color = Color(0xFF4C613D)),
        HEATER("shield_heater", "Heater Shield", 3.5f, defense = 35f, speedPenalty = 0.08f, description = "A highly maneuverable shield favoured by cavalry.", color = Color(0xFF265063));
        override val type: ItemType get() = ItemType.SHIELD
    }
    val SHIELDS = Shield.values().toList()

    enum class ArmorPiece(
        override val id: String,
        override val itemName: String,
        override val mass: Float,
        override val defense: Float = 0f,
        override val pierce: Float = 0f,
        override val slash: Float = 0f,
        override val blunt: Float = 0f,
        override val speedPenalty: Float = 0f,
        override val reach: Float = 0f,
        override val isRanged: Boolean = false,
        override val description: String,
        override val color: Color = Color.Gray
    ) : GearItem {
        BARE("armor_bare", "Naked Norman", 0.0f, defense = 0f, speedPenalty = -0.15f, description = "FIGHT IN YOUR TUNIC UNDERGARMENTS! Absolute maximum score multiplier (x10.0), but one hit will pierce your fleshy bits.", color = Color(0xFFEFE6D4)),
        FELT_GAMBESON("armor_felt", "Felt Gambeson", 2.0f, defense = 12f, speedPenalty = 0.02f, description = "Layers of boiled felt. Cheaper than proper padded armor.", color = Color(0xFFBCA683)),
        PADDED("armor_padded", "Padded Gambeson", 3.5f, defense = 15f, speedPenalty = 0.04f, description = "Stuffed layers of raw linen. Surprisingly effective against slashing.", color = Color(0xFFD6C39F)),
        FUR_JACKET("armor_fur", "Wolf Fur Jacket", 2.5f, defense = 18f, speedPenalty = 0.05f, description = "A heavy pelt taken from a large wolf. Surprisingly warm and decent protection.", color = Color(0xFF6B513C)),
        LEATHER("armor_leather", "Leather Jerkin", 5.0f, defense = 22f, speedPenalty = 0.08f, description = "Tough boiled leather hides. Smells like grease and wet dog.", color = Color(0xFF7D583F)),
        LAMELLAR("armor_lamellar", "Lamellar Armor", 10.0f, defense = 50f, speedPenalty = 0.20f, description = "Small metal plates laced together in rows. Often brought back by Byzantine mercenaries.", color = Color(0xFF60665B)),
        CHAINMAIL("armor_chainmail", "Rings of Hauberk", 12.0f, defense = 55f, speedPenalty = 0.22f, description = "Thousands of interlocking iron rings. Heavy defense against sword edges.", color = Color(0xFF717A80)),
        SCALE("armor_scale", "Iron Scale Armor", 16.0f, defense = 70f, speedPenalty = 0.35f, description = "Overlapping iron scales sewn to leather. Exceptional protection, exhausting to wear.", color = Color(0xFF5D666B)),
        GAUNTLETS("armor_gauntlets", "Iron Gauntlets", 1.5f, defense = 15f, speedPenalty = 0.02f, description = "Heavy iron gloves that protect the hands.", color = Color(0xFF6B747A)),
        BOOTS("armor_boots", "Iron-shod Boots", 2.0f, defense = 15f, speedPenalty = 0.03f, description = "Heavy boots protecting the feet from lowly Saxon spears.", color = Color(0xFF5D666B)),
        EXTRA_COIF("armor_coif", "Mail Coif Layer", 2.0f, defense = 20f, speedPenalty = 0.02f, description = "An extra coif draped over your shoulders and neck.", color = Color(0xFF868C91)),
        JESTER_OUTFIT("armor_jester", "Jester's Motley", 0.5f, defense = 0f, speedPenalty = 0.0f, description = "A full set of colorful motley. Complete lack of protection. High score multi!", color = Color(0xFF3C5CD6));
        override val type: ItemType get() = ItemType.ARMOR
    }
    val ARMOR_PIECES = ArmorPiece.values().toList()

    enum class HeadgearPiece(
        override val id: String,
        override val itemName: String,
        override val mass: Float,
        override val defense: Float = 0f,
        override val pierce: Float = 0f,
        override val slash: Float = 0f,
        override val blunt: Float = 0f,
        override val speedPenalty: Float = 0f,
        override val reach: Float = 0f,
        override val isRanged: Boolean = false,
        override val description: String,
        override val color: Color = Color.Gray
    ) : GearItem {
        COIF("helm_coif", "Linen Mail Coif", 2.0f, defense = 18f, speedPenalty = 0.02f, description = "A close-fitting hood made of woven chainmail rings.", color = Color(0xFF868C91)),
        CERVELLIERE("helm_cervelliere", "Iron Skull Cap", 2.5f, defense = 25f, speedPenalty = 0.03f, description = "A simple iron bowl for your brain. Better than nothing.", color = Color(0xFF9EA3A8)),
        NONE("helm_none", "Bare Head", 0.0f, defense = 0f, speedPenalty = -0.05f, description = "Feel the wind in your hair. And arrows in your skull. Higher score multiplier!", color = Color.Transparent),
        CONICAL("helm_conical", "Nasal Conical Helm", 3.5f, defense = 40f, speedPenalty = 0.05f, description = "The authentic iron conical helmet with a bold nose-guard. Legendary silhouette.", color = Color(0xFF96A0A8)),
        SPANGEN("helm_spangen", "Spangenhelm", 4.0f, defense = 45f, speedPenalty = 0.06f, description = "Metal strips riveting plates together. A classic medieval bruiser helm.", color = Color(0xFF7A8389)),
        KETTLE("helm_kettle", "Kettle Hat", 4.5f, defense = 50f, speedPenalty = 0.08f, description = "Wide brimmed hat of steel. Protects against arrows from above.", color = Color(0xFF8B9298)),
        MASK("helm_mask", "Masked Helm", 5.0f, defense = 58f, speedPenalty = 0.10f, description = "An enclosed helm with a menacing iron faceplate.", color = Color(0xFF7B858B)),
        GREAT("helm_great", "Great Helm", 6.0f, defense = 65f, speedPenalty = 0.15f, description = "A massive flat-topped steel bucket. Perfect protection, terrible visibility.", color = Color(0xFF6B747A)),
        PHRYGIAN("helm_phrygian", "Phrygian Helm", 3.0f, defense = 35f, speedPenalty = 0.04f, description = "A forward-curling iron cap, as worn by the Normans on the tapestry itself.", color = Color(0xFF8C959B)),
        MITRE("helm_mitre", "Bishop's Mitre", 1.0f, defense = 12f, speedPenalty = 0.0f, description = "Odo himself wore no sword. Cloth of gold turns few blades, but God is watching.", color = Color(0xFFD8C48A)),
        STRAW("helm_straw", "Straw Hat", 0.2f, defense = 3f, speedPenalty = -0.03f, description = "A farmhand's wide straw brim. Keeps the sun off. Keeps nothing else off.", color = Color(0xFFD9B871)),
        JESTER_HAT("helm_jester", "Jester's Cap", 0.1f, defense = 0f, speedPenalty = 0.0f, description = "A colorful motley cap with bells on. Unbelievably foolish. Score x20!", color = Color(0xFFD63C3C)),
        CROWN("helm_crown", "King's Crown", 0.5f, defense = 10f, speedPenalty = 0.0f, description = "A golden crown fit for a king.", color = Color(0xFFFFD700));
        override val type: ItemType get() = ItemType.HEADGEAR
    }
    val HEADGEAR_PIECES = HeadgearPiece.values().toList()
    
    val ANCILLARIES = Ancillary.values().toList()
}

// --- SIMULATION ENTITY REPRESENTATIONS ---

data class LevelUpChoice(
    val id: String,          // Unique ID for the level up option
    val title: String,       // Human readable option title
    val description: String, // Comedic and informative description
    val type: String,        // "follower", "attachment", "extension", "armor"
    val itemId: String       // Underlying GearItem or Ancillary ID
)

data class EmbeddedProjectile(
    val type: String,
    val isBallista: Boolean,
    val hasSpikes: Boolean,
    val offsetX: Float,
    val offsetY: Float,
    val angle: Float
)

enum class BackgroundObjectType {
    SHIP, FORT_PALACE, FORT_DINAN, BUILDING_BOSHAM, BUILDING_MANOR, FORT_TOWER, FORT_MOTTE, BROKEN_CHARIOT,
    BUILDING_BAYEUX, TOWER_SPIRAL,

    /** Drawn entirely from assets/art/<artId>.json. New art needs no new enum value. */
    VECTOR
}

data class BackgroundObject(
    val id: String,
    val type: BackgroundObjectType,
    val posX: Float,
    val posY: Float = 0f,
    val width: Float,
    var hp: Float,
    val maxHp: Float,
    var isDestroyed: Boolean = false,
    val artId: String? = null, // for type VECTOR: which assets/art/<id>.json to draw
    val seed: Int = Random.nextInt(),
    // Only arrow-like projectile hits leave visible shafts, split by firing direction
    var stuckArrowsFromLeft: Int = 0,
    var stuckArrowsFromRight: Int = 0
)

data class FighterState(
    val id: String,
    val name: String,
    val isPlayer: Boolean,
    var maxHp: Float,
    var hp: Float,
    var ghostHp: Float = hp,
    
    // Equipped gear
    var weaponHead: GameData.WeaponHead,
    var weaponHandle: GameData.WeaponHandle,
    var shield: GameData.Shield,
    var armor: GameData.ArmorPiece,
    var headgear: GameData.HeadgearPiece,
    var isDualWielding: Boolean = false,

    // Dynamic state
    var posX: Float, // 0 to 1000 representing the scrollable battlefield
    var targetX: Float,
    var velocityX: Float = 0f,
    var isAttacking: Boolean = false,
    var isDying: Boolean = false,
    var isDead: Boolean = false,
    var hasLandedStrike: Boolean = false,
    var attackCooldown: Float = 0f, // in seconds
    var lastAttackTime: Long = 0,
    var whirlCounterClockwise: Boolean = false, // double-ended: this swing reverses the overhead whirl
    var headSquashed: Boolean = false, // heavy blunt hit drove an enemy's head into his shoulders. It stays there.
    var swingProgress: Float = 0f, // 0 to 1 during swing
    var damageIndicator: String? = null,
    var damageIndicatorTimer: Float = 0f,
    
    // Animation/facing
    var facingRight: Boolean = true,
    var animFrame: Float = 0f,
    
    // Customization
    val size: Float = 1.0f,
    val hairColor: Color = Color(0xFF5A442E),
    val hairStyle: String = "short",
    val faceNoseShape: Int = (0..3).random(), // 0: normal, 1: hook, 2: bulbous, 3: pointy
    val faceBiteShape: Int = (0..3).random(), // 0: normal, 1: underbite, 2: overbite, 3: lantern jaw
    val faceForehead: Int = (0..2).random(), // 0: normal, 1: big, 2: sloped
    val faceMustache: Int = (0..3).random(),
    val warPaint: Int = 0, // 0 none, 1 blue woad
    val level: Int = 1,

    // Death tracking
    var deathType: DeathType = DeathType.FALL_BACK,
    var deathTime: Long = 0L,
    
    // Status effects
    var missingArm: Boolean = false,
    var isCrumpled: Boolean = false,
    
    val speedBoost: Float = 0f,

    // Roguelike attachments and layers (Level Up Upgrades)
    val extraAttachments: List<GearItem> = emptyList(),
    var extraArmors: List<GearItem> = emptyList(), // var: the retinue panoply reward layers gauntlets on at spawn
    val handleExtensionCount: Int = 0,
    val rangedUpgrades: List<String> = emptyList(),
    val shieldUpgrades: List<String> = emptyList(),
    val brawlerUpgrades: List<String> = emptyList(),
    var shieldHp: Float = 0f,
    var poisonDuration: Float = 0f,
    var bleedDuration: Float = 0f,
    var slowDuration: Float = 0f,
    var diseaseDuration: Float = 0f,
    var isContagious: Boolean = false,
    var dotDebt: Float = 0f, // sub-1 damage-over-time carried between ticks; see CombatEngine.applyDotDamage
    var isMounted: Boolean = false,
    var mountHp: Float = 0f,
    var isChariot: Boolean = false,
    var isStilts: Boolean = false,
    var isLord: Boolean = false,
    var hasSilkenGarments: Boolean = false, // lightens armour weight without losing protection
    val isWarPriest: Boolean = false, // never attacks; heals the worst-hurt foe near him
    var grapplerId: String? = null,
    var stolenWeaponOwnerId: String? = null, // a brawler holding a foe's stolen weapon; dropped when that foe dies
    var activeWrestlingMove: WrestlingMove? = null,
    var crumpleDuration: Float = 0f,
    var visualOffsetY: Float = 0f,
    var pallbearerIndex: Int = -1,
    var trampleCooldown: Float = 0f,
    var kills: Int = 0,
    var stuckProjectiles: MutableList<StuckProj> = mutableListOf(),
    var lateGameMultiplier: Float = 1f,
    var bandagesCount: Int = 0,
    val bloodDecals: MutableList<Triple<Float, Float, Int>> = mutableListOf()
) {
    // Simulated Base Stats
    val totalMass: Float
        get() {
            // Silken Garments genuinely lighten armour (same protection), so the reduced kg flows into
            // move speed, the weight readout, and the chariot check. Tunable factor.
            val silkFactor = if (hasSilkenGarments) 0.45f else 1f
            val gearBase = (weaponHead.mass + weaponHandle.mass + shield.mass) * size
            val armourBase = (armor.mass + headgear.mass) * size * silkFactor
            val attachmentsMass = extraAttachments.sumOf { it.mass.toDouble() }.toFloat()
            val armorsMass = extraArmors.sumOf { it.mass.toDouble() }.toFloat() * silkFactor
            val extensionMass = handleExtensionCount * 0.5f
            var shieldUpgradesMass = 0f
            if (shieldUpgrades.contains("oak_reinforcing")) shieldUpgradesMass += 2.5f
            if (shieldUpgrades.contains("iron_plating")) shieldUpgradesMass += 5.0f
            if (shieldUpgrades.contains("shield_helmet")) shieldUpgradesMass += 3.0f
            return gearBase + armourBase + attachmentsMass + armorsMass + extensionMass + shieldUpgradesMass
        }

    val totalArmor: Float
        get() {
            val base = (armor.defense + headgear.defense) * size
            val extraDef = extraArmors.sumOf { it.defense.toDouble() }.toFloat()
            // Level-up scaling: Player gets tougher with each survived level to face stronger Saxon hosts
            val lvlDef = if (isPlayer) (level - 1) * 3f else 0f
            return base + extraDef + lvlDef
        }

    // Compute weapon properties
    val isRanged: Boolean
        get() = weaponHead.isRanged

    // True bare-knuckle build: the only fighters allowed to wrestle
    val isBrawler: Boolean
        get() = weaponHead == GameData.WeaponHead.BARE && weaponHandle == GameData.WeaponHandle.FISTS

    val isFists: Boolean
        get() = weaponHead.id == "head_bare"

    val reach: Float
        get() {
            if (missingArm) return 0.2f * size + if (isMounted) 1.5f else 0f
            val baseReach = (weaponHead.reach + weaponHandle.reach) * size
            val extensionReach = handleExtensionCount * 0.35f
            val mountReach = if (isMounted) 1.5f else 0f
            return baseReach + extensionReach + mountReach
        }

    val baseDamage: Float
        get() {
            val base = (weaponHead.slash + weaponHead.pierce + weaponHead.blunt) * size * size
            val attachmentsDmg = extraAttachments.sumOf { (it.slash + it.pierce + it.blunt).toDouble() * 0.5 }.toFloat()
            // Player gets 12% extra base damage per level survived to scale up against high level mobs
            val scaleLvl = if (isPlayer) 1.0f + (level - 1) * 0.12f else 1.0f
            val doubleEndedMultiplier = if (isPlayer && weaponHandle.id == "handle_double_ended") 1.5f else 1.0f
            if (missingArm) {
                // Reduced to fist-fighting
                val fist = GameData.WEAPON_HEADS.first { it.id == "head_bare" }
                return fist.blunt * size * size * scaleLvl
            }
            return (base + attachmentsDmg) * scaleLvl * doubleEndedMultiplier
        }

    val damagePierce: Float 
        get() {
            if (missingArm) return 0f
            val base = weaponHead.pierce * size * size
            val att = extraAttachments.sumOf { it.pierce.toDouble() * 0.5 }.toFloat()
            val brawlerBonus = if (weaponHead.id == "head_bare" && weaponHandle.id == "handle_fists" && brawlerUpgrades.contains("spiked_wraps")) 8f else 0f
            val scaleLvl = if (isPlayer) 1.0f + (level - 1) * 0.12f else 1.0f
            return (base + att + brawlerBonus) * scaleLvl * lateGameMultiplier * nakedBoldness
        }

    val damageSlash: Float 
        get() {
            if (missingArm) return 0f
            val base = weaponHead.slash * size * size
            val att = extraAttachments.sumOf { it.slash.toDouble() * 0.5 }.toFloat()
            val scaleLvl = if (isPlayer) 1.0f + (level - 1) * 0.12f else 1.0f
            return (base + att) * scaleLvl * lateGameMultiplier * nakedBoldness
        }

    val damageBlunt: Float 
        get() {
            val base = weaponHead.blunt * size * size
            val att = extraAttachments.sumOf { it.blunt.toDouble() * 0.5 }.toFloat()
            val brawlerBonus = if (weaponHead.id == "head_bare" && weaponHandle.id == "handle_fists" && brawlerUpgrades.contains("brass_knuckles")) 15f else 0f
            val scaleLvl = if (isPlayer) 1.0f + (level - 1) * 0.12f else 1.0f
            return (base + att + brawlerBonus) * scaleLvl * lateGameMultiplier * nakedBoldness
        }

    // Attack delay influenced by total mass & handle speed penalty
    val attackSpeedDelay: Float
        get() {
            val baseDelay = if (isRanged) {
                if (weaponHead.id == "head_slingshot") {
                    if (isDualWielding) 0.9f else 1.8f
                }
                else if (weaponHead.id == "head_longbow") 2.5f
                else 2.0f
            } else if (weaponHead.id == "head_flail" || weaponHead.id == "head_war_flail" || weaponHandle.id == "handle_flail_chain") {
                0.75f // Flails are fast and unpredictable
            } else 1.1f
            // Weight slows you down slightly, but being little makes you attack much faster
            val sizeScale = if (isRanged) (0.2f + size * 0.8f) else (0.25f + size * 0.75f)
            val weightFactor = (1f + (totalMass * 0.01f)) * sizeScale // vastly reduced mass penalty
            // Two-handing (no shield) doubles weapon speed! Missing an arm means you can't two-hand.
            val shieldFactor = if (missingArm) 1.0f else if (shield.id == "shield_none" && !isDualWielding) 0.5f else if (isDualWielding) 0.6f else 1.0f
            // Speed penalty from handle choices
            val handleSpeedPenalty = weaponHandle.speedPenalty + (if (weaponHandle.id == "handle_double_ended") 0.15f else 0f)
            val crumpleFactor = if (crumpleDuration > 0f) 1.5f else 1.0f
            val finalDelay = baseDelay * weightFactor * shieldFactor * (1f + handleSpeedPenalty) * crumpleFactor
            return max(0.3f, finalDelay) // lower cap
        }

    /**
     * The naked Norman: fighting in nothing but your trousers is madness, and madness is rewarded.
     * Hits harder and moves faster — you are carrying nothing and you have nothing to lose.
     */
    val nakedBoldness: Float
        get() = if (isPlayer && armor.id == "armor_bare" && extraArmors.isEmpty()) 1.25f else 1f

    // Move speed multiplier based on mass and speed penalties
    val moveSpeed: Float
        get() {
            val baseSpeed = if (isPlayer) 75f else 60f // Pixels per second
            // Bigger characters move slower base speed
            val sizeSpeed = baseSpeed / size
            val crumplePenalty = if (crumpleDuration > 0f) 0.5f else 1.0f
            val slowPenalty = if (slowDuration > 0f) 0.4f else 1.0f
            val penaltyFactor = 1f - (totalMass * 0.01f).coerceIn(0f, 0.45f) // weight isn't so immobilizing
            return (sizeSpeed * penaltyFactor) * (1f + speedBoost) * crumplePenalty * slowPenalty * lateGameMultiplier * nakedBoldness
        }

    // Multiplier for points: naked = high risk = huge bonus points!
    val scoreMultiplier: Float
        get() {
            if (!isPlayer) return 1.0f
            var mult = 1.0f
            if (armor.id == "armor_bare") mult += 4.5f
            if (headgear.id == "helm_none") mult += 1.5f
            if (shield.id == "shield_none") mult += 2.0f
            
            // Lightweight weapon bonus
            val weaponMass = weaponHead.mass + weaponHandle.mass
            if (weaponMass < 1.0f) {
                mult += 1.0f
            }
            if (headgear.id == "helm_jester" || armor.id == "armor_jester" || extraArmors.any { it.id == "armor_jester" }) {
                mult += 20.0f
            }
            return mult
        }
}

/**
 * The hair palette, in one place.
 *
 * It used to be hard-coded in three (start-screen swatches, newRun preselect, newGame randomiser).
 * They drifted, so the preselected colour matched no swatch and read as "nothing selected". Every
 * site reads this list now — add a colour here and it appears everywhere at once.
 */
val HAIR_COLORS: List<Color> = listOf(
    Color(0xFF888888), // grey
    Color(0xFFC08030), // ginger
    Color(0xFF5A442E), // brown
    Color(0xFF2C2219), // near-black
    Color(0xFFE8D9A0), // flaxen blond
    Color(0xFF8B2500)  // rust red
)

/**
 * True if this fighter is of the given kind, whether he's the only one or one of a pack.
 *
 * Stackable followers spawn as "wardog#0", "wardog#1"… because ids must stay unique, but the
 * renderer and combat rules key off the kind ("wardog" draws a dog, not a man). Always ask with
 * this rather than `id == "wardog"`, or the second dog renders as a human with no bite.
 */
fun FighterState.isKind(kind: String): Boolean = id == kind || id.startsWith("$kind#")

// Grapples a bare-fisted brawler can roll on attack
enum class WrestlingMove { CHOKE_SLAM, BODY_THROW, SUPLEX }

// What a flying missile is — drives art, stuck-shaft rendering and hit sounds
enum class ProjectileType {
    ARROW, BOLT, STONE, JAVELIN, ROCK;
    val isArrowLike: Boolean get() = this == ARROW || this == BOLT || this == JAVELIN
}

// Ragdoll variants. Order matters: the first five are the "tame" deaths used for
// pallbearers; DECAPITATED triggers the blood fountain; CRUMPLED_IN_PLACE is for
// fighters killed while already lying down.
enum class DeathType {
    FALL_BACK, FACEPLANT, CARTWHEEL, PANCAKE, KNOCKED_FLYING, DECAPITATED, KNEEL_KEEL, SKY_LAUNCH, CRUMPLED_IN_PLACE;
    companion object {
        fun randomTame() = entries.take(5).random()
        fun randomAny() = entries.take(8).random()
    }
}

// Simple combat popups
data class CombatPopup(
    val text: String,
    val x: Float,
    val y: Float,
    var age: Float = 0f, // lifetime in seconds
    val color: Color = Color.Red
)

data class StuckProj(
    val type: ProjectileType,
    val size: Float,
    val velocityX: Float,
    val velocityY: Float,
    val inShield: Boolean,
    val isBallista: Boolean = false
)

class BloodParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var age: Float = 0f,
    val maxAge: Float = 4.0f + Random.nextFloat() * 4.0f, // linger longer on the ground!
    val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF9E3624),
    val isSmoke: Boolean = false,
    // Rolled once at spawn. The renderer used to re-roll this every frame, which cost an RNG call
    // per particle per frame and made each droplet shimmer.
    val radius: Float = 2.5f + Random.nextFloat() * 2f
)

// Main Game State
data class BattleSimState(
    val level: Int = 1,
    val score: Int = 0,
    val highscore: Int = 0,
    val playerHp: Float = 100f,
    val playerMaxHp: Float = 100f,
    val isBattleActive: Boolean = false,
    val battleWon: Boolean = false,
    val battleLost: Boolean = false,
    val gameCount: Int = 0,
    
    // Environment State
    val cameraX: Float = 0f,
    val levelWidth: Float = 1000f,
    val backgroundObjects: List<BackgroundObject> = emptyList(),
    
    // Selected gear (persistent between levels until customized)
    val weaponHead: GameData.WeaponHead = GameData.WEAPON_HEADS.first { it.id == "head_broadsword" },
    val weaponHandle: GameData.WeaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
    val shield: GameData.Shield = GameData.SHIELDS.random(),
    val armor: GameData.ArmorPiece = GameData.ARMOR_PIECES.random(),
    val headgear: GameData.HeadgearPiece = GameData.HEADGEAR_PIECES.random(),
    val characterSize: Float = 1.0f,
    val hairColor: Color = Color(0xFF5A442E),
    val hairStyle: String = "short", // short, long, bald
    val faceNoseShape: Int = (0..3).random(),
    val faceBiteShape: Int = (0..3).random(),
    val faceForehead: Int = (0..2).random(),
    val faceMustache: Int = (0..3).random(),
    // The name is stored as its parts, never as one string that gets re-parsed. The old code kept
    // only the display string and recovered the given name with split(" ").first() — so the default
    // "Syr William" yielded a given name of "Syr", and the real name was lost on the first rename.
    val honorific: String = "", // may be blank: not every man is a "Syr"
    val givenName: String = "William",
    val byname: String = "the Bastard",
    val unlockedGearIds: Set<String> = emptySet(),
    // A List, not a Set: duplicates ARE the feature — Twins/Thrice-Blessed add repeat copies,
    // which a Set silently deduped (stacking did nothing at the type level).
    val unlockedAncillaries: List<com.example.game.Ancillary> = emptyList(),
    val activeMount: Ancillary? = null,
    val isDualWielding: Boolean = false,
    val hasSilkenGarments: Boolean = false,
    /** Retinue panoply reward: every body-on-field follower gets helm, mail and gauntlets. */
    val hasRetinuePanoply: Boolean = false,
    val divineWeathers: List<DivineWeather> = emptyList(),
    val weatherCooldowns: Map<String, Float> = emptyMap(), // id -> seconds remaining; charged at battle start
    val hasShieldbreaker: Boolean = false,
    val hasArmorPiercing: Boolean = false,
    // Counters the player has actually met. Drives which "out" card gets added to the reward pool.
    val seenCounters: Set<String> = emptySet(),
    
    // Roguelike level upgrades persistent state
    val extraAttachments: List<String> = emptyList(), // stores GearItem IDs of extra blades/heads
    val extraArmors: List<String> = emptyList(),       // stores GearItem IDs of extra layers of armor
    val handleExtensionCount: Int = 0,
    val rangedUpgrades: List<String> = emptyList(),
    val shieldUpgrades: List<String> = emptyList(),
    val brawlerUpgrades: List<String> = emptyList(),
    val bandagesCount: Int = 0,
    val hasThroneOption: Boolean = false,
    val isThroneMode: Boolean = false,
    // Whether the throne was ever actually taken this run. hasThroneOption is only the offer roll,
    // so gating the mount picklist on it listed a throne the player never accepted.
    val hasTakenThrone: Boolean = false,
    
    // Level Up Choice State
    val pendingLevelUpChoices: List<LevelUpChoice> = emptyList(),
    val showLevelUpScreen: Boolean = false,
    val totalKills: Int = 0,

    // Music decision state
    val showMusicDecision: Boolean = false,
    val pendingMusicOptions: List<String> = emptyList(),
    val appliedMusicMoods: List<String> = emptyList(), // accumulated player music choices
    // Sticky for the run: set when any battle starts with fists selected, cleared on run failure.
    // Drives the medieval-speed-metal music variant ("Brawl" mood).
    val brawlMode: Boolean = false,

    val pendingSkipBonus: Int = 0, // score to award on next dismiss of level-up screen when skipped
    val performanceScore: Float = 0.5f // dynamic difficulty: 0=struggling, 1=dominating
) {
    /** The name as it is written on the tapestry. Derived, so it can never drift from its parts. */
    val playerName: String
        get() = listOf(honorific, givenName, byname).filter { it.isNotBlank() }.joinToString(" ")

    val totalHpBoost: Float
        get() = unlockedAncillaries.sumOf { it.hpBoost.toDouble() }.toFloat()

    val totalSpeedBoost: Float
        get() = unlockedAncillaries.sumOf { it.speedBoost.toDouble() }.toFloat()

    val scoreMultiplier: Float
        get() {
            val dummyPlayer = FighterState(
                id = "dummy", name = "Player", isPlayer = true, 
                maxHp = 100f, hp = 100f, ghostHp = 100f,
                weaponHead = weaponHead, weaponHandle = weaponHandle, 
                shield = shield, armor = armor, headgear = headgear,
                isDualWielding = isDualWielding,
                posX = 0f, targetX = 0f, size = characterSize, 
                hairColor = hairColor, hairStyle = hairStyle,
                extraAttachments = extraAttachments.mapNotNull { id -> GameData.WEAPON_HEADS.find { it.id == id } },
                extraArmors = extraArmors.mapNotNull { id -> GameData.ARMOR_PIECES.find { it.id == id } },
                handleExtensionCount = handleExtensionCount,
                rangedUpgrades = rangedUpgrades,
                shieldUpgrades = shieldUpgrades,
                brawlerUpgrades = brawlerUpgrades
            )
            return dummyPlayer.scoreMultiplier
        }
}

// Comedic medieval Latin combat slogans
object LatinShouts {
    val SLASH_SHOUTS = listOf("SLICUS!", "SECTUS!", "HASTINGS!", "COLPUS!", "SECATE!")
    val BLUNT_SHOUTS = listOf("THWACKUS!", "CLANGUS!", "CRUSHUS!", "BASHUS!", "FUSTIS!")
    val PIERCE_SHOUTS = listOf("PUNCTUS!", "FODIO!", "ACUS!", "TRANSFIGO!")
    val DAMAGE_SHOUTS = listOf("OUCHUS!", "VULNUS!", "HIC DOLOR!", "HEU!", "PRO DI IMMORTALES!", "VÆ MIHI!")
    val BLOCK_SHOUTS = listOf("DEFENSUS!", "SCUTUM!", "OBSTACULUM!", "NIL PENETRAT!")
    val VICTORY_SHOUTS = listOf("HUZZAH!", "VICTORIA!", "DEUS VULT!", "NORMANDI REGNAT!", "TRIUMPHUS!")

    fun getRandomShout(type: SoundType, strikeType: String = ""): String {
        return when (type) {
            SoundType.CLANG -> BLOCK_SHOUTS.random()
            SoundType.THWACK, SoundType.CRUNCH -> {
                when (strikeType) {
                    "pierce" -> PIERCE_SHOUTS.random()
                    "slash" -> SLASH_SHOUTS.random()
                    else -> BLUNT_SHOUTS.random()
                }
            }
            SoundType.OUCH -> DAMAGE_SHOUTS.random()
            SoundType.DRUM_ROLL -> "*RUMBLING DRUMS*"
            SoundType.SWOOSH -> "SWOOSHUS!"
            else -> ""
        }
    }
}


