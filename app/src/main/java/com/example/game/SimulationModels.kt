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
    SizePreset("tiny",   "Wee Runt",        0.65f, "Fastest. Fragile as wet parchment. Damage: ×0.42"),
    SizePreset("small",  "Nimble Scout",     0.80f, "Quick and evasive. Damage: ×0.64"),
    SizePreset("medium", "Average Norman",   1.00f, "Balanced. The default Hastings experience."),
    SizePreset("large",  "Burly Knight",     1.20f, "Slow but hits hard. Damage: ×1.44. Tankier."),
    SizePreset("huge",   "ABSOLUTE UNIT",    1.45f, "Glacial. Damage: ×2.10. Walking siege tower.")
)

enum class EnemyArchetype {
    FYRD_LEVY, HOUSECARL, ARCHER, SHIELD_WALL, BERSERKER, CAVALRY, CHAMPION
}

data class Ancillary(
    val id: String,
    val name: String,
    val role: String, // e.g., "Squire", "Herald", "Trumpeter", "Cupbearer"
    val description: String,
    val hpBoost: Float = 0f,
    val speedBoost: Float = 0f,
    val color: Color
)

data class GearItem(
    val id: String,
    val name: String,
    val type: ItemType,
    val mass: Float,          // kg
    val defense: Float = 0f,   // flat damage block or % reduction
    val pierce: Float = 0f,    // Armor penetration
    val slash: Float = 0f,     // Slashing damage
    val blunt: Float = 0f,     // Bludgeoning damage
    val speedPenalty: Float = 0f, // Reduction in attack/move speed
    val reach: Float = 0f,     // Melee range
    val isRanged: Boolean = false,
    val description: String,
    val color: Color = Color.Gray
)

object GameData {
    val WEAPON_HEADS = listOf(
        GearItem("head_bare", "Fists of Fury", ItemType.WEAPON_HEAD, 0.0f, blunt = 5f, reach = 1.0f, description = "No weapon head, just plain bare Saxon-bashing knuckles.", color = Color(0xFFCBB69E)),
        GearItem("head_pike", "Crude Pike Head", ItemType.WEAPON_HEAD, 1.2f, pierce = 18f, reach = 2.5f, description = "A sharp leaf-shaped iron point. Splendid for keeping foes at bay.", color = Color(0xFF8C969E)),
        GearItem("head_axe", "Bearded Axe Head", ItemType.WEAPON_HEAD, 2.5f, slash = 22f, blunt = 6f, reach = 1.5f, description = "An iconic curved axe head designed to hook shields and cleave mail.", color = Color(0xFF7A868C)),
        GearItem("head_sword", "Spatha Sword Blade", ItemType.WEAPON_HEAD, 1.0f, slash = 16f, pierce = 10f, reach = 1.6f, description = "A carefully pattern-welded iron sword blade. Rare and prestigious.", color = Color(0xFFA6B0B5)),
        GearItem("head_morningstar", "Spiked Morningstar", ItemType.WEAPON_HEAD, 3.2f, blunt = 15f, pierce = 12f, reach = 1.3f, description = "A brutal iron ball adorned with crude spikes. Hard to swing, devastating to meet.", color = Color(0xFF535C61)),
        GearItem("head_maul", "Heavy Iron Mallet", ItemType.WEAPON_HEAD, 4.5f, blunt = 25f, reach = 1.2f, description = "A giant chunk of lead and iron. Turns Anglo-Saxon mail into metallic soup.", color = Color(0xFF4A4E51)),
        GearItem("head_spear", "Leaf Spearpoint", ItemType.WEAPON_HEAD, 0.8f, pierce = 14f, reach = 2.2f, description = "Lightweight thrusting spearhead. A staple of Hastings shoreline skirmishes.", color = Color(0xFF909BA0)),
        GearItem("head_dagger", "Seax Dagger Blade", ItemType.WEAPON_HEAD, 0.4f, slash = 8f, pierce = 8f, reach = 0.8f, description = "A short single-edged saxon knife. Quick, sneaky, but dangerously short range.", color = Color(0xFF788387)),
        GearItem("head_bow", "Yew Shortbow", ItemType.WEAPON_HEAD, 0.9f, pierce = 15f, reach = 8.0f, isRanged = true, description = "A simple curved wooden bow. Shooteth cloth-yard shafts across the field.", color = Color(0xFF947B5C)),
        GearItem("head_slingshot", "Shepherd Slingshot", ItemType.WEAPON_HEAD, 0.2f, blunt = 10f, reach = 6.0f, isRanged = true, description = "A leather pouch for hurling pebbles. Humble, lightweight, and surprisingly painful.", color = Color(0xFF8A7156)),
        GearItem("head_halberd", "Halberd Blade", ItemType.WEAPON_HEAD, 2.8f, slash = 20f, pierce = 15f, reach = 2.0f, description = "An elegant combination of axe and spike. Devastating from a distance.", color = Color(0xFF8C969E)),
        GearItem("head_flail", "Spiked Flail", ItemType.WEAPON_HEAD, 2.0f, blunt = 18f, pierce = 5f, reach = 1.5f, description = "A spiked ball on a chain. Unpredictable and hard to block.", color = Color(0xFF535C61)),
        GearItem("head_longbow", "Welsh Longbow", ItemType.WEAPON_HEAD, 1.2f, pierce = 25f, reach = 10.0f, isRanged = true, description = "A massive yew bow that can punch through chainmail at long range.", color = Color(0xFF5D4831)),
        GearItem("head_claymore", "Highland Claymore", ItemType.WEAPON_HEAD, 3.5f, slash = 30f, pierce = 10f, reach = 2.0f, description = "A massive two-handed sword. Cleaves shields in twain.", color = Color(0xFF9AA0A3)),
        GearItem("head_scythe", "War Scythe", ItemType.WEAPON_HEAD, 2.2f, slash = 25f, pierce = 10f, reach = 2.5f, description = "A farmer's tool turned lethal weapon. Reaches around shields.", color = Color(0xFF6C7175)),
        GearItem("head_crossbow", "Heavy Crossbow", ItemType.WEAPON_HEAD, 1.5f, pierce = 65f, reach = 12.0f, isRanged = true, description = "A mechanical bow. High armor piercing and fast to crank.", color = Color(0xFF4A3B2C)),
        GearItem("head_mace", "Iron Mace", ItemType.WEAPON_HEAD, 2.0f, blunt = 20f, pierce = 3f, reach = 1.4f, description = "A simple but devastating flanged mace. Crushes helmets.", color = Color(0xFF636A6E)),
        GearItem("head_javelin", "Throwing Javelin", ItemType.WEAPON_HEAD, 0.6f, pierce = 12f, reach = 7.0f, isRanged = true, description = "A light throwing spear. Short range for a missile, but fast.", color = Color(0xFF8C969E)),
        GearItem("head_war_flail", "Twin War Flail", ItemType.WEAPON_HEAD, 3.5f, blunt = 22f, pierce = 8f, reach = 1.8f, description = "Two spiked balls on branching chains. Absolute chaos.", color = Color(0xFF535C61))
    )

    val WEAPON_HANDLES = listOf(
        GearItem("handle_fists", "Bare Wrists", ItemType.WEAPON_HANDLE, 0.0f, speedPenalty = 0.0f, description = "Just your hairy hands.", color = Color(0xFFCBB69E)),
        GearItem("handle_short", "Short Ash Grip", ItemType.WEAPON_HANDLE, 0.4f, speedPenalty = -0.05f, description = "A 1-foot wooden haft. Fast swings, low mass.", color = Color(0xFF9C7D58)),
        GearItem("handle_medium", "Hickory Shaft", ItemType.WEAPON_HANDLE, 0.8f, reach = 0.3f, speedPenalty = 0.05f, description = "A sturdy medium wooden handle. Balanced and reliable.", color = Color(0xFF8C6F47)),
        GearItem("handle_long", "Long Ash Pole", ItemType.WEAPON_HANDLE, 1.8f, reach = 1.2f, speedPenalty = 0.2f, description = "A lengthy 6-foot spear haft. Drastically increases reach but is slow to turn.", color = Color(0xFF735835)),
        GearItem("handle_iron", "Iron-shod Haft", ItemType.WEAPON_HANDLE, 2.5f, reach = 0.5f, speedPenalty = 0.15f, description = "A heavy, iron-reinforced shaft. Hits harder but swings slower.", color = Color(0xFF636A6E)),
        GearItem("handle_wheel", "Cart Wheel", ItemType.WEAPON_HANDLE, 3.5f, reach = 0.6f, speedPenalty = 0.4f, description = "A literal wooden cart wheel as a handle. Ludicrously heavy, but incredible momentum.", color = Color(0xFF6E5536)),
        GearItem("handle_pick", "Mining Pick Handle", ItemType.WEAPON_HANDLE, 1.2f, reach = 0.4f, speedPenalty = 0.08f, description = "An angled wooden pick handle. Grants weird but effective striking angles.", color = Color(0xFF7A654C)),
        GearItem("handle_chain", "Bayeux Iron Chain", ItemType.WEAPON_HANDLE, 1.5f, reach = 0.8f, speedPenalty = 0.25f, description = "An iron chain linking your grip to the weapon. Swings wildly in a floppy arc! Slower, but hits with high momentum.", color = Color(0xFF4C5154)),
        GearItem("handle_double_ended", "Double-Ended Pole", ItemType.WEAPON_HANDLE, 2.0f, reach = 1.0f, speedPenalty = 0.35f, description = "A wooden pole allowing heads on BOTH ends! Slower, but covers both ends and deals 1.5x damage.", color = Color(0xFF5D4831)),
        GearItem("handle_flail_chain", "Flail Chain", ItemType.WEAPON_HANDLE, 1.2f, reach = 1.0f, speedPenalty = 0.30f, description = "A short iron chain with a swivel joint. Makes any head a flail. Bypasses shields.", color = Color(0xFF4C5154))
    )

    val SHIELDS = listOf(
        GearItem("shield_none", "No Shield (Two-Handing)", ItemType.SHIELD, 0.0f, description = "Wield nothing in the off-hand. High vulnerability, double weapon speed!", color = Color.Transparent),
        GearItem("shield_buckler", "Saxon Buckler", ItemType.SHIELD, 1.5f, defense = 15f, speedPenalty = 0.02f, description = "A small wooden fist-shield with an iron boss. Lightweight parrying tool.", color = Color(0xFFAC8054)),
        GearItem("shield_kite", "Norman Kite Shield", ItemType.SHIELD, 4.5f, defense = 45f, speedPenalty = 0.12f, description = "The iconic teardrop shield. Excellent leg protection. Painted with bold stripes.", color = Color(0xFF9E3624)),
        GearItem("shield_tower", "Saxon Wall Shield", ItemType.SHIELD, 8.0f, defense = 75f, speedPenalty = 0.28f, description = "A massive wooden shield used in shield walls. Heavy as an iron gate.", color = Color(0xFF4C613D)),
        GearItem("shield_heater", "Heater Shield", ItemType.SHIELD, 3.5f, defense = 35f, speedPenalty = 0.08f, description = "A highly maneuverable shield favoured by cavalry.", color = Color(0xFF265063))
    )

    val ARMOR_PIECES = listOf(
        GearItem("armor_bare", "Naked Norman", ItemType.ARMOR, 0.0f, defense = 0f, speedPenalty = -0.15f, description = "FIGHT IN YOUR TUNIC UNDERGARMENTS! Absolute maximum score multiplier (x10.0), but one hit will pierce your fleshy bits.", color = Color(0xFFEFE6D4)),
        GearItem("armor_padded", "Padded Gambeson", ItemType.ARMOR, 3.5f, defense = 15f, speedPenalty = 0.04f, description = "Stuffed layers of raw linen. Surprisingly effective against slashing.", color = Color(0xFFD6C39F)),
        GearItem("armor_leather", "Leather Jerkin", ItemType.ARMOR, 5.0f, defense = 22f, speedPenalty = 0.08f, description = "Tough boiled leather hides. Smells like grease and wet dog.", color = Color(0xFF7D583F)),
        GearItem("armor_chainmail", "Rings of Hauberk", ItemType.ARMOR, 12.0f, defense = 55f, speedPenalty = 0.22f, description = "Thousands of interlocking iron rings. Heavy defense against sword edges.", color = Color(0xFF717A80)),
        GearItem("armor_scale", "Iron Scale Armor", ItemType.ARMOR, 16.0f, defense = 70f, speedPenalty = 0.35f, description = "Overlapping iron scales sewn to leather. Exceptional protection, exhausting to wear.", color = Color(0xFF5D666B))
    )

    val HEADGEAR_PIECES = listOf(
        GearItem("helm_none", "Bare Dome", ItemType.HEADGEAR, 0.0f, defense = 0f, description = "No helmet. Exposes your magnificent Norman tonsure.", color = Color(0xFFE5C09F)),
        GearItem("helm_coif", "Linen Mail Coif", ItemType.HEADGEAR, 2.0f, defense = 18f, speedPenalty = 0.02f, description = "A close-fitting hood made of woven chainmail rings.", color = Color(0xFF868C91)),
        GearItem("helm_cervelliere", "Iron Skull Cap", ItemType.HEADGEAR, 2.5f, defense = 25f, speedPenalty = 0.03f, description = "A simple iron bowl for your brain. Better than nothing.", color = Color(0xFF9EA3A8)),
        GearItem("helm_conical", "Nasal Conical Helm", ItemType.HEADGEAR, 3.5f, defense = 40f, speedPenalty = 0.05f, description = "The authentic iron conical helmet with a bold nose-guard. Legendary silhouette.", color = Color(0xFF96A0A8)),
        GearItem("helm_spangen", "Spangenhelm", ItemType.HEADGEAR, 4.0f, defense = 45f, speedPenalty = 0.06f, description = "Metal strips riveting plates together. A classic medieval bruiser helm.", color = Color(0xFF7A8389)),
        GearItem("helm_kettle", "Kettle Hat", ItemType.HEADGEAR, 4.5f, defense = 50f, speedPenalty = 0.08f, description = "Wide brimmed hat of steel. Protects against arrows from above.", color = Color(0xFF8B9298)),
        GearItem("helm_mask", "Masked Helm", ItemType.HEADGEAR, 5.0f, defense = 58f, speedPenalty = 0.10f, description = "An enclosed helm with a menacing iron faceplate.", color = Color(0xFF7B858B)),
        GearItem("helm_great", "Great Helm", ItemType.HEADGEAR, 6.0f, defense = 65f, speedPenalty = 0.15f, description = "A massive flat-topped steel bucket. Perfect protection, terrible visibility.", color = Color(0xFF6B747A))
    )
    
    val ANCILLARIES = listOf(
        Ancillary("anc_squire", "Baldrick", "Squire", "A useless but enthusiastic lad carrying your spare tunics.", hpBoost = 20f, color = Color(0xFF539462)),
        Ancillary("anc_herald", "Sir Boast-a-lot", "Herald", "Announces your presence loudly. Intimidates peasants.", speedBoost = 0.2f, color = Color(0xFFB03131)),
        Ancillary("anc_trumpeter", "Tooty", "Trumpeter", "Plays off-key trumpet blasts during battle.", hpBoost = 10f, speedBoost = 0.1f, color = Color(0xFFD6A420)),
        Ancillary("anc_crossbowman", "Gaston", "Crossbowman", "Slow but devastating ranged cover fire. Pierces mail.", hpBoost = 0f, speedBoost = 0f, color = Color(0xFF3B2F2F)),
        Ancillary("anc_mount_horse", "Warhorse", "Destrier", "A towering Norman warhorse. Grants massive speed and HP.", hpBoost = 80f, speedBoost = 0.5f, color = Color(0xFF452E1B)),
        Ancillary("anc_cupbearer", "Geoffrey", "Cupbearer", "Refills your goblet with fine wine mid-swing.", hpBoost = 40f, speedBoost = -0.1f, color = Color(0xFF632873)),
        Ancillary("anc_archer", "Robin", "Longbowman", "Fires covering arrows into the fray. Just mind your back.", hpBoost = 5f, speedBoost = 0f, color = Color(0xFF4C613D)),
        Ancillary("anc_monk", "Brother Tuck", "Monk", "Blesses you with holy incense. Smells heavenly.", hpBoost = 30f, speedBoost = 0f, color = Color(0xFF5E4B3C))
    )
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

data class FighterState(
    val id: String,
    val name: String,
    val isPlayer: Boolean,
    val maxHp: Float,
    var hp: Float,
    var ghostHp: Float = hp,
    
    // Equipped gear
    var weaponHead: GearItem,
    var weaponHandle: GearItem,
    var shield: GearItem,
    var armor: GearItem,
    var headgear: GearItem,
    var isDualWielding: Boolean = false,

    // Dynamic state
    var posX: Float, // 0 to 1000 representing the scrollable battlefield
    var targetX: Float,
    var velocityX: Float = 0f,
    var isAttacking: Boolean = false,
    var isDying: Boolean = false,
    var isDead: Boolean = false,
    var attackCooldown: Float = 0f, // in seconds
    var lastAttackTime: Long = 0,
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
    val level: Int = 1,
    
    // Death tracking
    var deathType: Int = 0,
    var deathTime: Long = 0L,
    
    // Status effects
    var missingArm: Boolean = false,
    var isCrumpled: Boolean = false,
    
    val speedBoost: Float = 0f,

    // Roguelike attachments and layers (Level Up Upgrades)
    val extraAttachments: List<GearItem> = emptyList(),
    val extraArmors: List<GearItem> = emptyList(),
    val handleExtensionCount: Int = 0,
    val rangedUpgrades: List<String> = emptyList(),
    var poisonDuration: Float = 0f,
    var bleedDuration: Float = 0f,
    var isMounted: Boolean = false,
    var trampleCooldown: Float = 0f,
    var kills: Int = 0,
    var stuckProjectiles: MutableList<StuckProj> = mutableListOf()
) {
    // Simulated Base Stats
    val totalMass: Float
        get() {
            val base = (weaponHead.mass + weaponHandle.mass + shield.mass + armor.mass + headgear.mass) * size
            val attachmentsMass = extraAttachments.sumOf { it.mass.toDouble() }.toFloat()
            val armorsMass = extraArmors.sumOf { it.mass.toDouble() }.toFloat()
            val extensionMass = handleExtensionCount * 0.5f
            return base + attachmentsMass + armorsMass + extensionMass
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
            val scaleLvl = if (isPlayer) 1.0f + (level - 1) * 0.12f else 1.0f
            return (base + att) * scaleLvl
        }

    val damageSlash: Float 
        get() {
            if (missingArm) return 0f
            val base = weaponHead.slash * size * size
            val att = extraAttachments.sumOf { it.slash.toDouble() * 0.5 }.toFloat()
            val scaleLvl = if (isPlayer) 1.0f + (level - 1) * 0.12f else 1.0f
            return (base + att) * scaleLvl
        }

    val damageBlunt: Float 
        get() {
            val base = weaponHead.blunt * size * size
            val att = extraAttachments.sumOf { it.blunt.toDouble() * 0.5 }.toFloat()
            val scaleLvl = if (isPlayer) 1.0f + (level - 1) * 0.12f else 1.0f
            return (base + att) * scaleLvl
        }

    // Attack delay influenced by total mass & handle speed penalty
    val attackSpeedDelay: Float
        get() {
            val baseDelay = if (isRanged) {
                if (weaponHead.id == "head_slingshot") 1.8f // Slower slingshot
                else if (weaponHead.id == "head_longbow") 2.5f
                else 2.0f
            } else 1.1f
            // Weight slows you down slightly
            val weightFactor = 1f + (totalMass * 0.035f) + (size - 1f) * 0.5f
            // Two-handing (no shield) doubles weapon speed! Missing an arm means you can't two-hand.
            val shieldFactor = if (missingArm) 1.0f else if (shield.id == "shield_none" && !isDualWielding) 0.5f else if (isDualWielding) 0.6f else 1.0f
            // Speed penalty from handle choices
            val handleSpeedPenalty = weaponHandle.speedPenalty + (if (weaponHandle.id == "handle_double_ended") 0.15f else 0f)
            val crumpleFactor = if (isCrumpled) 1.5f else 1.0f
            val finalDelay = baseDelay * weightFactor * shieldFactor * (1f + handleSpeedPenalty) * crumpleFactor
            return max(0.4f, finalDelay)
        }

    // Move speed multiplier based on mass and speed penalties
    val moveSpeed: Float
        get() {
            val baseSpeed = if (isPlayer) 75f else 60f // Pixels per second
            // Bigger characters move slower base speed
            val sizeSpeed = baseSpeed / size
            val crumplePenalty = if (isCrumpled) 0.5f else 1.0f
            val penaltyFactor = 1f - (totalMass * 0.025f).coerceIn(0f, 0.6f)
            return (sizeSpeed * penaltyFactor) * (1f + speedBoost) * crumplePenalty
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
            return mult
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
    val type: String, // "arrow", "bolt", "stone"
    val size: Float,
    val velocityX: Float,
    val velocityY: Float,
    val inShield: Boolean
)

class BloodParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var age: Float = 0f,
    val maxAge: Float = 4.0f + Random.nextFloat() * 4.0f // linger longer on the ground!
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
    
    // Selected gear (persistent between levels until customized)
    val weaponHead: GearItem = GameData.WEAPON_HEADS.random(),
    val weaponHandle: GearItem = GameData.WEAPON_HANDLES.random(),
    val shield: GearItem = GameData.SHIELDS.random(),
    val armor: GearItem = GameData.ARMOR_PIECES.random(),
    val headgear: GearItem = GameData.HEADGEAR_PIECES.random(),
    val characterSize: Float = 1.0f,
    val hairColor: Color = Color(0xFF5A442E),
    val hairStyle: String = "short", // short, long, bald
    val faceNoseShape: Int = (0..3).random(),
    val faceBiteShape: Int = (0..3).random(),
    val faceForehead: Int = (0..2).random(),
    val faceMustache: Int = (0..3).random(),
    val playerName: String = "Syr William",
    val unlockedGearIds: Set<String> = emptySet(),
    val unlockedAncillaries: Set<String> = emptySet(),
    val isDualWielding: Boolean = false,
    
    // Roguelike level upgrades persistent state
    val extraAttachments: List<String> = emptyList(), // stores GearItem IDs of extra blades/heads
    val extraArmors: List<String> = emptyList(),       // stores GearItem IDs of extra layers of armor
    val handleExtensionCount: Int = 0,
    val rangedUpgrades: List<String> = emptyList(),
    
    // Level Up Choice State
    val pendingLevelUpChoices: List<LevelUpChoice> = emptyList(),
    val showLevelUpScreen: Boolean = false,
    val totalKills: Int = 0,

    // Music decision state
    val showMusicDecision: Boolean = false,
    val pendingMusicOptions: List<String> = emptyList(),
    val appliedMusicMoods: List<String> = emptyList(), // accumulated player music choices

    val pendingSkipBonus: Int = 0, // score to award on next dismiss of level-up screen when skipped
    val performanceScore: Float = 0.5f // dynamic difficulty: 0=struggling, 1=dominating
) {
    val totalHpBoost: Float
        get() = unlockedAncillaries.sumOf { id -> GameData.ANCILLARIES.find { it.id == id }?.hpBoost?.toDouble() ?: 0.0 }.toFloat()

    val totalSpeedBoost: Float
        get() = unlockedAncillaries.sumOf { id -> GameData.ANCILLARIES.find { it.id == id }?.speedBoost?.toDouble() ?: 0.0 }.toFloat()

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
                rangedUpgrades = rangedUpgrades
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
