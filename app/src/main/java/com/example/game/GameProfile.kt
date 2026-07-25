package com.example.game

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.profileStore by preferencesDataStore(name = "bayeux_profile")

/**
 * Everything that survives between runs. Deliberately tiny: a couple of string sets and three
 * scalars. Room is in the version catalog and is the wrong tool for this.
 *
 * Initialised from MainActivity like VectorAsset and MedievalAudioSynth, because GameViewModel is
 * a plain ViewModel with no Context.
 */
object GameProfile {

    data class Profile(
        val unlockedItemIds: Set<String> = emptySet(),
        val clearedMilestones: Set<String> = emptySet(),
        val highscore: Int = 0,
        val totalDeaths: Int = 0,
        val adFreePurchased: Boolean = false,
        /** Boss ids ever defeated, across all runs. Drives the "beat both giants" milestone. */
        val beatenBosses: Set<String> = emptySet(),
        /** A count, not a set: the winged helm wants William dead twice. */
        val williamKills: Int = 0
    )

    private val KEY_ITEMS = stringSetPreferencesKey("unlocked_item_ids")
    private val KEY_MILESTONES = stringSetPreferencesKey("cleared_milestones")
    private val KEY_HIGHSCORE = intPreferencesKey("highscore")
    private val KEY_DEATHS = intPreferencesKey("total_deaths")
    private val KEY_AD_FREE = booleanPreferencesKey("ad_free_purchased")
    private val KEY_BEATEN_BOSSES = stringSetPreferencesKey("beaten_bosses")
    private val KEY_WILLIAM_KILLS = intPreferencesKey("william_kills")

    /**
     * Recorded once, whether or not it grants anything, so the check never runs twice.
     * See [migrateBatchAHandles].
     */
    const val MIGRATION_BATCH_A_HANDLES = "migration_batch_a_handles"

    private var appContext: Context? = null

    /** Last loaded profile, so the game loop can read it without suspending. */
    @Volatile
    var cached: Profile = Profile()
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Forget the context, so every call below becomes a no-op again.
     *
     * Test support. This is a process-wide singleton, so a test that calls [init] otherwise leaves a
     * Robolectric Application here after its sandbox has been torn down; the next test class to
     * construct a GameViewModel then does DataStore I/O against a dead classloader and deadlocks the
     * whole run. Any test that calls [init] must call this in an @After.
     */
    fun shutdown() {
        appContext = null
        cached = Profile()
    }

    suspend fun load(): Profile {
        val ctx = appContext ?: return Profile()
        val prefs = ctx.profileStore.data.first()
        return Profile(
            unlockedItemIds = prefs[KEY_ITEMS].orEmpty(),
            clearedMilestones = prefs[KEY_MILESTONES].orEmpty(),
            highscore = prefs[KEY_HIGHSCORE] ?: 0,
            totalDeaths = prefs[KEY_DEATHS] ?: 0,
            adFreePurchased = prefs[KEY_AD_FREE] ?: false,
            beatenBosses = prefs[KEY_BEATEN_BOSSES].orEmpty(),
            williamKills = prefs[KEY_WILLIAM_KILLS] ?: 0
        ).also { cached = it }
    }

    /**
     * Batch A shipped the seven handles free, marked "earned by deeds in a later age". This is that
     * age, and gating them would strip them from anyone already using them.
     *
     * Batch A persisted nothing at all, so an empty profile is indistinguishable from a fresh
     * install by its contents alone. The only evidence a player was here before is the install
     * record: an upgraded package has [android.content.pm.PackageInfo.lastUpdateTime] later than
     * its firstInstallTime, a first-time install has them equal.
     *
     * The milestone is written either way, so this decides once per install and never revisits it.
     */
    suspend fun migrateBatchAHandles() {
        val ctx = appContext ?: return
        if (MIGRATION_BATCH_A_HANDLES in load().clearedMilestones) return
        val upgraded = try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            info.lastUpdateTime > info.firstInstallTime
        } catch (e: Exception) {
            false // Can't tell: treat as a new install rather than hand out free gear.
        }
        ctx.profileStore.edit { prefs ->
            prefs[KEY_MILESTONES] = prefs[KEY_MILESTONES].orEmpty() + MIGRATION_BATCH_A_HANDLES
            if (upgraded) {
                prefs[KEY_ITEMS] = prefs[KEY_ITEMS].orEmpty() + GameData.UNLOCKABLE_HANDLE_IDS
            }
        }
        load()
    }

    /**
     * Wipe the saved profile. Test support only — no game flow resets progress, and the two string
     * sets are additive everywhere else. Exists because DataStore is a singleton bound to the
     * Application instance, which Robolectric reuses between test methods in a class.
     */
    suspend fun clearForTest() {
        val ctx = appContext ?: return
        ctx.profileStore.edit { it.clear() }
        load()
    }

    /** Record one milestone and the item it awards. Both sets are additive; nothing is ever removed. */
    suspend fun grant(itemId: String, milestoneId: String) {
        val ctx = appContext ?: return
        ctx.profileStore.edit { prefs ->
            prefs[KEY_ITEMS] = prefs[KEY_ITEMS].orEmpty() + itemId
            prefs[KEY_MILESTONES] = prefs[KEY_MILESTONES].orEmpty() + milestoneId
        }
        load()
    }

    /**
     * Remember a boss the player has put down, for milestones that span runs. William gets a counter
     * as well as a set entry, because "twice" cannot be expressed by a set.
     */
    suspend fun recordBossKill(bossId: String) {
        val ctx = appContext ?: return
        ctx.profileStore.edit { prefs ->
            prefs[KEY_BEATEN_BOSSES] = prefs[KEY_BEATEN_BOSSES].orEmpty() + bossId
            if (bossId == WILLIAM_BOSS_ID) {
                prefs[KEY_WILLIAM_KILLS] = (prefs[KEY_WILLIAM_KILLS] ?: 0) + 1
            }
        }
        load()
    }

    /** The one boss id with a counter attached; kept here so callers cannot misspell it. */
    const val WILLIAM_BOSS_ID = "boss_william_the_bastard"

    suspend fun recordDeath() {
        val ctx = appContext ?: return
        ctx.profileStore.edit { it[KEY_DEATHS] = (it[KEY_DEATHS] ?: 0) + 1 }
        load()
    }

    suspend fun setHighscore(score: Int) {
        val ctx = appContext ?: return
        ctx.profileStore.edit { it[KEY_HIGHSCORE] = maxOf(it[KEY_HIGHSCORE] ?: 0, score) }
        load()
    }

    suspend fun setAdFree(purchased: Boolean) {
        val ctx = appContext ?: return
        ctx.profileStore.edit { it[KEY_AD_FREE] = purchased }
        load()
    }
}
