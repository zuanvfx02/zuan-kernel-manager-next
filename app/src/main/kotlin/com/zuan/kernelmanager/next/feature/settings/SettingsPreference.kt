/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import com.zuan.kernelmanager.next.ui.theme.ZkmIconShapeStyle
import com.zuan.kernelmanager.next.ui.components.ZkmLite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Enums untuk Navigasi
enum class NavStyle { LIQUID_CAPSULE, LIQUID_CAPSULE_2, CLASSIC_MATERIAL, MODERN_TABS }
enum class AppFontChoice { APP_DEFAULT, DEVICE_SYSTEM, SERIF, MONOSPACE, CUSTOM }
enum class NavLabelState { ALWAYS_SHOW, ONLY_SELECTED, ALWAYS_HIDE }

// Enums untuk Weather Effects (ditambah CLOUDS dan ICE)
enum class WeatherEffect { NONE, FOG, RAIN, SNOW, SUN_RAYS, CLOUDS, ICE }

// UiConfig sekarang punya properti isVideo dan weather effects
data class UiConfig(
    val bgType: BgType = BgType.SYSTEM,
    val solidColor: Int = 0xFF1A1A1A.toInt(),
    val expressiveThemeId: Int = 0,
    val customImageUri: String? = null,
    val isBgBlur: Boolean = false,
    val bgContrast: Float = 0f,
    val cardDarkness: Float = 0.15f,
    val isHazeEnabled: Boolean = true,
    val isVideo: Boolean = false,
    val weatherEffect: WeatherEffect = WeatherEffect.NONE,
    val weatherIntensity: Float = 0.5f,
    val bgSaturation: Float = 1f,
    val blurStrength: Float = 20f,
    val isCustomColor: Boolean = false,
    val customPrimaryColor: Int = Color(0xFF4A6595).toArgb(),
    val customSecondaryColor: Int = Color(0xFF586275).toArgb(),
    val customTertiaryColor: Int = Color(0xFF5E4B8B).toArgb()
)

// Enum Background
enum class BgType { SYSTEM, PRESET_SOLID, EXPRESSIVE, GALLERY }

// [BARU] Style kartu glass: frosted blur (Haze, bawaan lama) atau Android Liquid Glass (Kyant backdrop)
enum class CardGlassStyle { FROSTED_GLASS, LIQUID_GLASS }

// [BARU] Style Toggle/Switch global di seluruh app
enum class ToggleStyle { MATERIAL_EXPRESSIVE, LIQUID_GLASS, CLASSIC, CUSTOM_IMAGE }

// [BARU] Style Slider global di seluruh app
enum class SliderStyle { MATERIAL_EXPRESSIVE, LIQUID_GLASS, CLASSIC, CUSTOM_IMAGE }

// [BARU] Style Dialog global
enum class DialogStyle { HAZE_BLUR, LIQUID_GLASS }

// [BARU] Tingkat kelangkaan bingkai avatar (buat badge di sheet).
enum class DecoTier(val label: String) {
    COMMON("Common"),
    EPIC_PREMIUM("Epic Premium"),
    MYTHIC("Mythic"),
    LEGENDARY("Legendary"),
    DIVINE("Divine")
}

// [BARU] Dekorasi bingkai avatar profil (ala Discord Nitro).
// Frame awal (7 pertama) oleh KIMI AI; frame 2D & 3D tambahan oleh Claude Sonnet 5.5.
// Koleksi Epic Premium & Mythic (30 frame: 10 2D + 10 3D + 10 statis) oleh Kimi K3.
// Koleksi Legendary & Divine (18 frame), lalu Ultimate Avatar Studio v10 (+30 frame: 10 2D + 10 3D + 10 statis).
// [is3D] = gaya 3D sungguhan (proyeksi + oklusi depan/belakang avatar).
// [isStatic] = gaya diam (tidak beranimasi, jam animasi dimatikan -> hemat daya).
// [tier] = tingkat kelangkaan untuk badge (COMMON tidak menampilkan badge).
// [contentScale] = ukuran nominal foto/video; renderer menyisakan ruang bleed agar ornamen terlihat lebih ekspresif.
enum class AvatarDecorationStyle(
    val label: String,
    val is3D: Boolean = false,
    val isStatic: Boolean = false,
    val tier: DecoTier = DecoTier.COMMON,
    val contentScale: Float = if (is3D) 0.76f else 0.80f
) {
    NONE("None"),

    // --- 2D (KIMI AI) ---
    NEON_PULSE("Neon Pulse"),
    RGB_STORM("RGB Storm"),
    SAKURA_BLOOM("Sakura Bloom"),
    INFERNO_RING("Inferno Ring"),
    FROST_CRYSTAL("Frost Crystal"),
    GALAXY_ORBIT("Galaxy Orbit"),
    GOLD_ROYALE("Gold Royale"),

    // --- 2D (Claude Sonnet 5.5) ---
    AURORA_VEIL("Aurora Veil"),
    CYBER_HUD("Cyber HUD"),
    ELECTRIC_STORM("Electric Storm"),
    OCEAN_WAVE("Ocean Wave"),
    LOVE_PULSE("Love Pulse"),
    COMET_TRAIL("Comet Trail"),
    RUNE_CIRCLE("Rune Circle"),
    PIXEL_RETRO("Pixel Retro"),
    NEKO_EARS("Neko Ears"),
    ANGEL_HALO("Angel Halo"),

    // --- 3D (Claude Sonnet 5.5) ---
    GYRO_CORE("Gyro Core", is3D = true),
    SATURN_HALO("Saturn Halo", is3D = true),
    QUANTUM_ATOM("Quantum Atom", is3D = true),
    HYPER_CUBE("Hyper Cube", is3D = true, contentScale = 0.70f),
    HELIX_RING("Helix Ring", is3D = true),
    PRISM_SHARDS("Prism Shards", is3D = true),
    GLASS_GLOBE("Glass Globe", is3D = true),
    CHROME_TORUS("Chrome Torus", is3D = true),
    MINI_SOLAR("Mini Solar", is3D = true),
    HEX_HOLO("Hex Hologram", is3D = true),

    // --- 2D Epic Premium / Mythic (Kimi K3) ---
    VOID_VORTEX("Void Vortex", tier = DecoTier.EPIC_PREMIUM),
    PHOENIX_FLAME("Phoenix Flame", tier = DecoTier.EPIC_PREMIUM),
    TOXIC_SLIME("Toxic Slime", tier = DecoTier.EPIC_PREMIUM),
    DIGITAL_MATRIX("Digital Matrix", tier = DecoTier.EPIC_PREMIUM),
    TECH_CIRCUIT("Tech Circuit", tier = DecoTier.EPIC_PREMIUM),
    BUTTERFLY_DREAM("Butterfly Dream", tier = DecoTier.EPIC_PREMIUM),
    LAVA_FLOW("Lava Flow", tier = DecoTier.EPIC_PREMIUM),
    HOLO_FOIL("Holo Foil", tier = DecoTier.EPIC_PREMIUM),
    BLOOD_MOON("Blood Moon", tier = DecoTier.MYTHIC),
    SERPENT_COIL("Serpent Coil", tier = DecoTier.MYTHIC),

    // --- 3D Epic Premium / Mythic (Kimi K3) ---
    PLASMA_ORB("Plasma Orb", is3D = true, tier = DecoTier.EPIC_PREMIUM),
    HYPER_PYRAMID("Hyper Pyramid", is3D = true, contentScale = 0.70f, tier = DecoTier.EPIC_PREMIUM),
    PORTAL_GATE("Portal Gate", is3D = true, tier = DecoTier.EPIC_PREMIUM),
    GRAVITY_WELL("Gravity Well", is3D = true, tier = DecoTier.EPIC_PREMIUM),
    WARP_TUNNEL("Warp Tunnel", is3D = true, tier = DecoTier.EPIC_PREMIUM),
    ORBITAL_BLADES("Orbital Blades", is3D = true, tier = DecoTier.EPIC_PREMIUM),
    METEOR_BELT("Meteor Belt", is3D = true, tier = DecoTier.EPIC_PREMIUM),
    NEBULA_SPHERE("Nebula Sphere", is3D = true, tier = DecoTier.EPIC_PREMIUM),
    DRONE_SWARM("Drone Swarm", is3D = true, tier = DecoTier.MYTHIC),
    CRYSTAL_CROWN("Crystal Crown", is3D = true, tier = DecoTier.MYTHIC),

    // --- Static Epic Premium / Mythic (Kimi K3) — tidak beranimasi ---
    OBSIDIAN_EDGE("Obsidian Edge", isStatic = true, tier = DecoTier.EPIC_PREMIUM),
    JADE_DYNASTY("Jade Dynasty", isStatic = true, tier = DecoTier.EPIC_PREMIUM),
    STEAMPUNK_GEAR("Steampunk Gear", isStatic = true, tier = DecoTier.EPIC_PREMIUM),
    PEARL_LACE("Pearl Lace", isStatic = true, tier = DecoTier.EPIC_PREMIUM),
    CARBON_CORE("Carbon Core", isStatic = true, tier = DecoTier.EPIC_PREMIUM),
    MARBLE_LUXE("Marble Luxe", isStatic = true, tier = DecoTier.EPIC_PREMIUM),
    TRIBAL_SPIRIT("Tribal Spirit", isStatic = true, tier = DecoTier.EPIC_PREMIUM),
    SILVER_WINGS("Silver Wings", isStatic = true, tier = DecoTier.EPIC_PREMIUM),
    ROYAL_CREST("Royal Crest", isStatic = true, tier = DecoTier.MYTHIC),
    ONYX_CROWN("Onyx Crown", isStatic = true, tier = DecoTier.MYTHIC),

    // --- 2D NEW LEGENDARY & DIVINE (7 Efek Baru) ---
    SUPERNOVA_BURST("Supernova Burst", is3D = false, isStatic = false, tier = DecoTier.DIVINE, contentScale = 0.76f),
    CYBERPUNK_GLITCH("Cyberpunk Glitch", is3D = false, isStatic = false, tier = DecoTier.LEGENDARY, contentScale = 0.80f),
    DRAGON_SOUL("Dragon Soul", is3D = false, isStatic = false, tier = DecoTier.MYTHIC, contentScale = 0.77f),
    ABYSSAL_KRAKEN("Abyssal Kraken", is3D = false, isStatic = false, tier = DecoTier.MYTHIC, contentScale = 0.78f),
    ETHEREAL_WINGS("Ethereal Wings", is3D = false, isStatic = false, tier = DecoTier.DIVINE, contentScale = 0.74f),
    QUANTUM_SINGULARITY("Quantum Singularity", is3D = false, isStatic = false, tier = DecoTier.MYTHIC, contentScale = 0.78f),
    SAKURA_NIGHTFALL("Sakura Nightfall", is3D = false, isStatic = false, tier = DecoTier.LEGENDARY, contentScale = 0.80f),

    // --- 3D NEW LEGENDARY & DIVINE (7 Efek Baru) ---
    CELESTIAL_ASTROLABE("Celestial Astrolabe", is3D = true, isStatic = false, tier = DecoTier.DIVINE, contentScale = 0.76f),
    BLACK_HOLE_ACCRETION("Black Hole Accretion", is3D = true, isStatic = false, tier = DecoTier.MYTHIC, contentScale = 0.75f),
    NEON_TESSERACT("Neon Tesseract", is3D = true, isStatic = false, tier = DecoTier.LEGENDARY, contentScale = 0.78f),
    INFINITY_OUROBOROS("Infinity Ouroboros", is3D = true, isStatic = false, tier = DecoTier.MYTHIC, contentScale = 0.76f),
    SOLAR_CORONA("Solar Corona", is3D = true, isStatic = false, tier = DecoTier.LEGENDARY, contentScale = 0.77f),
    VALKYRIE_AEGIS("Valkyrie Aegis", is3D = true, isStatic = false, tier = DecoTier.DIVINE, contentScale = 0.75f),
    DNA_CYBER_SYNTH("DNA Cyber Synth", is3D = true, isStatic = false, tier = DecoTier.LEGENDARY, contentScale = 0.78f),

    // --- STATIC NEW LEGENDARY & DIVINE (4 Efek Baru) ---
    DIVINE_IMPERIAL_CROWN("Divine Imperial Crown", is3D = false, isStatic = true, tier = DecoTier.DIVINE, contentScale = 0.74f),
    VOID_REAPER_SCYTHE("Void Reaper Scythe", is3D = false, isStatic = true, tier = DecoTier.MYTHIC, contentScale = 0.76f),
    SOLARIS_EMBLEM("Solaris Emblem", is3D = false, isStatic = true, tier = DecoTier.LEGENDARY, contentScale = 0.76f),
    CYBER_EXOSKELETON("Cyber Exoskeleton", is3D = false, isStatic = true, tier = DecoTier.LEGENDARY, contentScale = 0.78f),

    // --- ZKM NEXT AVATAR STUDIO: ULTIMATE 2D COLLECTION (10) ---
    AETHER_WINGS("Aether Wings", tier = DecoTier.DIVINE, contentScale = 0.74f),
    THUNDER_CREST("Thunder Crest", tier = DecoTier.LEGENDARY, contentScale = 0.78f),
    FROSTBITE_SIGIL("Frostbite Sigil", tier = DecoTier.MYTHIC, contentScale = 0.79f),
    SOLAR_LANCE("Solar Lance", tier = DecoTier.DIVINE, contentScale = 0.77f),
    ROSE_VORTEX("Rose Vortex", tier = DecoTier.LEGENDARY, contentScale = 0.78f),
    ARCANE_TAROT("Arcane Tarot", tier = DecoTier.MYTHIC, contentScale = 0.80f),
    CRIMSON_BLOOM("Crimson Bloom", tier = DecoTier.MYTHIC, contentScale = 0.78f),
    DIGITAL_PHANTOM("Digital Phantom", tier = DecoTier.LEGENDARY, contentScale = 0.80f),
    LUNAR_ECLIPSE("Lunar Eclipse", tier = DecoTier.DIVINE, contentScale = 0.79f),
    STARDUST_CROWN("Stardust Crown", tier = DecoTier.DIVINE, contentScale = 0.74f),

    // --- ZKM NEXT AVATAR STUDIO: ULTIMATE DEPTH-RENDERED 3D COLLECTION (10) ---
    PRISMATIC_REACTOR("Prismatic Reactor", is3D = true, tier = DecoTier.LEGENDARY, contentScale = 0.76f),
    CELESTIAL_ARMILLARY("Celestial Armillary", is3D = true, tier = DecoTier.DIVINE, contentScale = 0.75f),
    NEBULA_DRAGON("Nebula Dragon", is3D = true, tier = DecoTier.MYTHIC, contentScale = 0.76f),
    TITAN_RING("Titan Ring", is3D = true, tier = DecoTier.LEGENDARY, contentScale = 0.78f),
    AETHER_CUBE("Aether Cube", is3D = true, tier = DecoTier.DIVINE, contentScale = 0.75f),
    ORBITAL_CROWN("Orbital Crown", is3D = true, tier = DecoTier.MYTHIC, contentScale = 0.76f),
    CRYSTAL_HEART("Crystal Heart", is3D = true, tier = DecoTier.DIVINE, contentScale = 0.76f),
    ECLIPSE_ENGINE("Eclipse Engine", is3D = true, tier = DecoTier.LEGENDARY, contentScale = 0.77f),
    PHOENIX_NEBULA("Phoenix Nebula", is3D = true, tier = DecoTier.DIVINE, contentScale = 0.75f),
    QUANTUM_TRIDENT("Quantum Trident", is3D = true, tier = DecoTier.MYTHIC, contentScale = 0.77f),

    // --- ZKM NEXT AVATAR STUDIO: ULTIMATE STATIC COLLECTION (10; NO ANIMATION) ---
    AMETHYST_DIADEM("Amethyst Diadem", isStatic = true, tier = DecoTier.DIVINE, contentScale = 0.74f),
    TITANIUM_WREATH("Titanium Wreath", isStatic = true, tier = DecoTier.LEGENDARY, contentScale = 0.78f),
    OBSIDIAN_DRAGON("Obsidian Dragon", isStatic = true, tier = DecoTier.MYTHIC, contentScale = 0.76f),
    FROSTED_SAPPHIRE("Frosted Sapphire", isStatic = true, tier = DecoTier.LEGENDARY, contentScale = 0.78f),
    CELESTIAL_RELIC("Celestial Relic", isStatic = true, tier = DecoTier.DIVINE, contentScale = 0.77f),
    CRIMSON_SIGIL("Crimson Sigil", isStatic = true, tier = DecoTier.MYTHIC, contentScale = 0.78f),
    ECLIPSE_MEDALLION("Eclipse Medallion", isStatic = true, tier = DecoTier.LEGENDARY, contentScale = 0.79f),
    ROSE_GOLD_FILIGREE("Rose Gold Filigree", isStatic = true, tier = DecoTier.EPIC_PREMIUM, contentScale = 0.79f),
    MYSTIC_RUNESTONE("Mystic Runestone", isStatic = true, tier = DecoTier.MYTHIC, contentScale = 0.79f),
    IMPERIAL_PHOENIX("Imperial Phoenix", isStatic = true, tier = DecoTier.DIVINE, contentScale = 0.74f);

    companion object {
        fun fromName(name: String?): AvatarDecorationStyle =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

// Data Class Warna
data class AppThemeColor(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

// Expanded color palette - 16 warna + dynamic
val availableColors = listOf(
    AppThemeColor("Blue", Color(0xFF4A6595), Color(0xFF586275), Color(0xFF5E4B8B)),
    AppThemeColor("Green", Color(0xFF556B2F), Color(0xFF4F6A55), Color(0xFF356A4F)),
    AppThemeColor("Purple", Color(0xFF7B52AB), Color(0xFF685675), Color(0xFF8B4C70)),
    AppThemeColor("Orange", Color(0xFFB36B36), Color(0xFF8B6B55), Color(0xFF9C551F)),
    AppThemeColor("Pink", Color(0xFFBC4C73), Color(0xFF9C4C66), Color(0xFF8B4C66)),
    AppThemeColor("Gray", Color(0xFF6B6B6B), Color(0xFF555555), Color(0xFF3E3E3E)),
    AppThemeColor("Yellow", Color(0xFF9C8B1F), Color(0xFF7B6B1F), Color(0xFF6B551F)),
    AppThemeColor("Light Green", Color(0xFF7CB342), Color(0xFF689F38), Color(0xFF558B2F)),
    AppThemeColor("Dark Green", Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C)),
    AppThemeColor("Teal", Color(0xFF00897B), Color(0xFF00796B), Color(0xFF00695C)),
    AppThemeColor("Cyan", Color(0xFF00ACC1), Color(0xFF0097A7), Color(0xFF00838F)),
    AppThemeColor("Light Blue", Color(0xFF29B6F6), Color(0xFF03A9F4), Color(0xFF0288D1)),
    AppThemeColor("Indigo", Color(0xFF5C6BC0), Color(0xFF3F51B5), Color(0xFF3949AB)),
    AppThemeColor("Deep Purple", Color(0xFF7E57C2), Color(0xFF673AB7), Color(0xFF5E35B1)),
    AppThemeColor("Magenta", Color(0xFFD81B60), Color(0xFFC2185B), Color(0xFFAD1457)),
    AppThemeColor("Rose", Color(0xFFF06292), Color(0xFFEC407A), Color(0xFFE91E63)),
    AppThemeColor("Coral", Color(0xFFFF7043), Color(0xFFFF5722), Color(0xFFF4511E)),
    AppThemeColor("Amber", Color(0xFFFFB300), Color(0xFFFFA000), Color(0xFFFF8F00)),
    AppThemeColor("Lime", Color(0xFFAFB42B), Color(0xFF9E9D24), Color(0xFF827717)),
    AppThemeColor("Mint", Color(0xFF26A69A), Color(0xFF4DB6AC), Color(0xFF80CBC4)),
    AppThemeColor("Navy", Color(0xFF1A237E), Color(0xFF283593), Color(0xFF303F9F)),
    AppThemeColor("Slate", Color(0xFF455A64), Color(0xFF546E7A), Color(0xFF607D8B)),
    AppThemeColor("Charcoal", Color(0xFF37474F), Color(0xFF455A64), Color(0xFF263238)),
    AppThemeColor("Burgundy", Color(0xFF880E4F), Color(0xFFAD1457), Color(0xFFC2185B)),
    AppThemeColor("Peach", Color(0xFFFFAB91), Color(0xFFFF8A65), Color(0xFFFF7043))
)

class SettingsPreference(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
    }.getOrNull() ?: object : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any?>()
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = false
        override fun edit(): SharedPreferences.Editor = DummyEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private class DummyEditor : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor = this
        override fun clear(): SharedPreferences.Editor = this
        override fun commit(): Boolean = true
        override fun apply() {}
    }

    // --- STATES ---
    // --- VERSI LITE (Lite vs Pro) ---
    // Nilai asli pilihan user di Wallpaper Style Screen TIDAK pernah diubah oleh Lite.
    // Flow di bawah yang berfungsi sebagai "fitur berat" diekspos lewat [liteGate]: selama
    // Lite aktif nilainya dipaksa ke versi ringan, begitu Lite dimatikan (Pro) nilai asli
    // user otomatis kembali. Semua layar yang membaca flow ini ikut ter-downgrade tanpa
    // perlu diubah satu per satu.
    private val liteScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val _liteMode = MutableStateFlow(prefs.getBoolean(LITE_MODE_KEY, false))
    /** true = Versi Lite (ringan), false = Pro (normal). */
    val liteMode: StateFlow<Boolean> = _liteMode.asStateFlow()

    init { ZkmLite.active = _liteMode.value }

    fun getLiteMode(): Boolean = _liteMode.value
    fun setLiteMode(enabled: Boolean) {
        prefs.edit { putBoolean(LITE_MODE_KEY, enabled) }
        _liteMode.value = enabled
        ZkmLite.active = enabled
    }

    private fun <T> liteGate(raw: StateFlow<T>, lite: (T) -> T): StateFlow<T> =
        combine(raw, _liteMode) { value, isLite -> if (isLite) lite(value) else value }
            .stateIn(
                liteScope,
                SharingStarted.Eagerly,
                if (_liteMode.value) lite(raw.value) else raw.value
            )

    /** Nilai asli pilihan user (tanpa override Lite) — dipakai hanya bila memang perlu. */
    fun isVideoWallpaperRaw(): Boolean = _isVideoWallpaper.value

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _themeColorName = MutableStateFlow(getThemeColorName())
    val themeColorName: StateFlow<String> = _themeColorName.asStateFlow()

    private val _isDynamicColor = MutableStateFlow(getDynamicColorEnabled())
    val isDynamicColor: StateFlow<Boolean> = _isDynamicColor.asStateFlow()

    private val _pollingInterval = MutableStateFlow(getPollingInterval())
    val pollingInterval: StateFlow<Long> = liteGate(_pollingInterval) { if (it < LITE_MIN_POLLING_MS) LITE_MIN_POLLING_MS else it }

    private val _appDpi = MutableStateFlow(getAppDpi())
    val appDpi: StateFlow<Int> = _appDpi.asStateFlow()

    // --- VISUAL STATES ---
    private val _isCustomBackground = MutableStateFlow(getCustomBackgroundEnabled())
    val isCustomBackground: StateFlow<Boolean> = _isCustomBackground.asStateFlow()

    private val _backgroundImageUri = MutableStateFlow(getBackgroundImageUri())
    val backgroundImageUri: StateFlow<String?> = _backgroundImageUri.asStateFlow()

    private val _isVideoWallpaper = MutableStateFlow(getIsVideoWallpaper())
    val isVideoWallpaper: StateFlow<Boolean> = liteGate(_isVideoWallpaper) { false }

    private val _useSystemWallpaper = MutableStateFlow(getUseSystemWallpaper())
    val useSystemWallpaper: StateFlow<Boolean> = _useSystemWallpaper.asStateFlow()

    private val _navBarTransparency = MutableStateFlow(getNavBarTransparency())
    val navBarTransparency: StateFlow<Float> = liteGate(_navBarTransparency) { 1f }

    private val _cardDarkness = MutableStateFlow(getCardDarkness())
    val cardDarkness: StateFlow<Float> = liteGate(_cardDarkness) { 1f }

    private val _isBgBlur = MutableStateFlow(getBgBlurEnabled())
    val isBgBlur: StateFlow<Boolean> = liteGate(_isBgBlur) { false }

    private val _bgContrast = MutableStateFlow(getBgContrast())
    val bgContrast: StateFlow<Float> = _bgContrast.asStateFlow()
    
    private val _isHazeEnabled = MutableStateFlow(getHazeEnabled())
    val isHazeEnabled: StateFlow<Boolean> = liteGate(_isHazeEnabled) { false }

    // --- [BARU] CARD GLASS STYLE (LIQUID GLASS) STATES ---
    private val _cardGlassStyle = MutableStateFlow(getCardGlassStyle())
    val cardGlassStyle: StateFlow<CardGlassStyle> = liteGate(_cardGlassStyle) { CardGlassStyle.FROSTED_GLASS }

    private val _liquidCornerRadius = MutableStateFlow(getLiquidCornerRadius())
    val liquidCornerRadius: StateFlow<Float> = _liquidCornerRadius.asStateFlow()

    private val _liquidBlurRadius = MutableStateFlow(getLiquidBlurRadius())
    val liquidBlurRadius: StateFlow<Float> = _liquidBlurRadius.asStateFlow()

    private val _liquidRefractionHeight = MutableStateFlow(getLiquidRefractionHeight())
    val liquidRefractionHeight: StateFlow<Float> = _liquidRefractionHeight.asStateFlow()

    private val _liquidRefractionAmount = MutableStateFlow(getLiquidRefractionAmount())
    val liquidRefractionAmount: StateFlow<Float> = _liquidRefractionAmount.asStateFlow()

    private val _liquidChromaticAberration = MutableStateFlow(getLiquidChromaticAberration())
    val liquidChromaticAberration: StateFlow<Boolean> = _liquidChromaticAberration.asStateFlow()

    // --- [BARU] TOGGLE / SLIDER / DIALOG STYLE STATES ---
    private val _toggleStyle = MutableStateFlow(getToggleStyle())
    val toggleStyle: StateFlow<ToggleStyle> = liteGate(_toggleStyle) { if (it == ToggleStyle.LIQUID_GLASS) ToggleStyle.CLASSIC else it }

    private val _sliderStyle = MutableStateFlow(getSliderStyle())
    val sliderStyle: StateFlow<SliderStyle> = liteGate(_sliderStyle) { if (it == SliderStyle.LIQUID_GLASS) SliderStyle.CLASSIC else it }

    private val _dialogStyle = MutableStateFlow(getDialogStyle())
    val dialogStyle: StateFlow<DialogStyle> = liteGate(_dialogStyle) { DialogStyle.HAZE_BLUR }

    private val _customToggleOnImagePath = MutableStateFlow(prefs.getString(CUSTOM_TOGGLE_ON_IMAGE_KEY, null))
    val customToggleOnImagePath: StateFlow<String?> = _customToggleOnImagePath.asStateFlow()

    private val _customToggleOffImagePath = MutableStateFlow(prefs.getString(CUSTOM_TOGGLE_OFF_IMAGE_KEY, null))
    val customToggleOffImagePath: StateFlow<String?> = _customToggleOffImagePath.asStateFlow()

    private val _customSliderThumbImagePath = MutableStateFlow(prefs.getString(CUSTOM_SLIDER_THUMB_IMAGE_KEY, null))
    val customSliderThumbImagePath: StateFlow<String?> = _customSliderThumbImagePath.asStateFlow()

    private val _bgType = MutableStateFlow(getBgType())
    val bgType: StateFlow<BgType> = liteGate(_bgType) { if (it == BgType.EXPRESSIVE) BgType.SYSTEM else it }

    private val _solidColor = MutableStateFlow(getSolidColor())
    val solidColor: StateFlow<Int> = _solidColor.asStateFlow()

    private val _expressiveThemeId = MutableStateFlow(getExpressiveThemeId())
    val expressiveThemeId: StateFlow<Int> = _expressiveThemeId.asStateFlow()

    // --- NAVIGATION STATES ---
    private val _navStyle = MutableStateFlow(getNavStyle())
    val navStyle: StateFlow<NavStyle> = liteGate(_navStyle) { if (it == NavStyle.LIQUID_CAPSULE || it == NavStyle.LIQUID_CAPSULE_2) NavStyle.CLASSIC_MATERIAL else it }

    private val _navLabelState = MutableStateFlow(getNavLabelState())
    val navLabelState: StateFlow<NavLabelState> = _navLabelState.asStateFlow()

    // --- WEATHER EFFECT STATES ---
    private val _weatherEffect = MutableStateFlow(getWeatherEffect())
    val weatherEffect: StateFlow<WeatherEffect> = liteGate(_weatherEffect) { WeatherEffect.NONE }

    // [BARU] Avatar Decoration (bingkai profil ala Discord)
    private val _avatarDecoration = MutableStateFlow(AvatarDecorationStyle.fromName(prefs.getString(AVATAR_DECORATION_KEY, null)))
    val avatarDecoration: StateFlow<AvatarDecorationStyle> = liteGate(_avatarDecoration) { if (it.isStatic) it else AvatarDecorationStyle.NONE }

    private val _weatherIntensity = MutableStateFlow(getWeatherIntensity())
    val weatherIntensity: StateFlow<Float> = _weatherIntensity.asStateFlow()

    // --- BACKGROUND ADJUSTMENT STATES ---
    private val _bgSaturation = MutableStateFlow(getBgSaturation())
    val bgSaturation: StateFlow<Float> = _bgSaturation.asStateFlow()

    private val _blurStrength = MutableStateFlow(getBlurStrength())
    val blurStrength: StateFlow<Float> = _blurStrength.asStateFlow()

    // --- [BETA] APPBAR BANNER PROGRESSIVE ---
    private val _appBarBannerEnabled = MutableStateFlow(prefs.getBoolean(APPBAR_BANNER_ENABLED_KEY, false))
    val appBarBannerEnabled: StateFlow<Boolean> = liteGate(_appBarBannerEnabled) { false }

    private val _appBarBannerUri = MutableStateFlow(prefs.getString(APPBAR_BANNER_URI_KEY, null))
    val appBarBannerUri: StateFlow<String?> = _appBarBannerUri.asStateFlow()

    private val _appBarBannerType = MutableStateFlow(prefs.getString(APPBAR_BANNER_TYPE_KEY, "image") ?: "image")
    val appBarBannerType: StateFlow<String> = _appBarBannerType.asStateFlow()

    private val _appBarBannerDim = MutableStateFlow(prefs.toFloat(APPBAR_BANNER_DIM_KEY, 0.25f))
    val appBarBannerDim: StateFlow<Float> = _appBarBannerDim.asStateFlow()

    // --- CUSTOM COLOR STATES ---
    private val _isCustomColor = MutableStateFlow(getIsCustomColor())
    val isCustomColor: StateFlow<Boolean> = _isCustomColor.asStateFlow()

    private val _customPrimaryColor = MutableStateFlow(getCustomPrimaryColor())
    val customPrimaryColor: StateFlow<Int> = _customPrimaryColor.asStateFlow()

    private val _customSecondaryColor = MutableStateFlow(getCustomSecondaryColor())
    val customSecondaryColor: StateFlow<Int> = _customSecondaryColor.asStateFlow()

    private val _customTertiaryColor = MutableStateFlow(getCustomTertiaryColor())
    val customTertiaryColor: StateFlow<Int> = _customTertiaryColor.asStateFlow()

    private val _iconShapeStyle = MutableStateFlow(getIconShapeStyle())
    val iconShapeStyle: StateFlow<ZkmIconShapeStyle> = _iconShapeStyle.asStateFlow()

    private val _customIconImagePath = MutableStateFlow(prefs.getString(CUSTOM_ICON_IMAGE_KEY, null))
    val customIconImagePath: StateFlow<String?> = _customIconImagePath.asStateFlow()

    private val _customIconImagePaths = MutableStateFlow(loadCustomIconImagePaths())
    val customIconImagePaths: StateFlow<Map<String, String>> = _customIconImagePaths.asStateFlow()

    private val _menuIconShapes = MutableStateFlow(loadMenuIconShapes())
    val menuIconShapes: StateFlow<Map<String, String>> = _menuIconShapes.asStateFlow()

    private val _autoIconImageColor = MutableStateFlow(prefs.getBoolean(AUTO_ICON_IMAGE_COLOR_KEY, false))
    val autoIconImageColor: StateFlow<Boolean> = _autoIconImageColor.asStateFlow()

    private val _appFontChoice = MutableStateFlow(getAppFontChoice())
    val appFontChoice: StateFlow<AppFontChoice> = _appFontChoice.asStateFlow()

    private val _customFontPath = MutableStateFlow(getCustomFontPath())
    val customFontPath: StateFlow<String?> = _customFontPath.asStateFlow()

    private val _highRefreshRate = MutableStateFlow(getHighRefreshRate())
    val highRefreshRate: StateFlow<Boolean> = liteGate(_highRefreshRate) { false }

    // --- INTRO STATES ---
    private val _hasCompletedIntro = MutableStateFlow(getHasCompletedIntro())
    val hasCompletedIntro: StateFlow<Boolean> = _hasCompletedIntro.asStateFlow()
    
    private val _acceptedTerms = MutableStateFlow(getAcceptedTerms())
    val acceptedTerms: StateFlow<Boolean> = _acceptedTerms.asStateFlow()
    
    private val _acceptedRisk = MutableStateFlow(getAcceptedRisk())
    val acceptedRisk: StateFlow<Boolean> = _acceptedRisk.asStateFlow()

    private val _adaptiveEcoMode = MutableStateFlow(getAdaptiveEcoMode())
    val adaptiveEcoMode: StateFlow<Boolean> = _adaptiveEcoMode.asStateFlow()

    fun getAdaptiveEcoMode(): Boolean = prefs.getBoolean(ADAPTIVE_ECO_MODE_KEY, true)
    fun setAdaptiveEcoMode(enabled: Boolean) {
        prefs.edit { putBoolean(ADAPTIVE_ECO_MODE_KEY, enabled) }
        _adaptiveEcoMode.value = enabled
    }

    // --- FPS OVERLAY STATES ---
    private val _fpsOverlayEnabled = MutableStateFlow(getFpsOverlayEnabled())
    val fpsOverlayEnabled: StateFlow<Boolean> = _fpsOverlayEnabled.asStateFlow()

    private val _fpsStyle = MutableStateFlow(getFpsStyle())
    val fpsStyle: StateFlow<Int> = _fpsStyle.asStateFlow()

    private val _fpsOrientation = MutableStateFlow(getFpsOrientation())
    val fpsOrientation: StateFlow<Int> = _fpsOrientation.asStateFlow()

    private val _fpsColor = MutableStateFlow(getFpsColor())
    val fpsColor: StateFlow<String> = _fpsColor.asStateFlow()

    private val _fpsSize = MutableStateFlow(getFpsSize())
    val fpsSize: StateFlow<Float> = _fpsSize.asStateFlow()

    private val _fpsWidthScale = MutableStateFlow(getFpsWidthScale())
    val fpsWidthScale: StateFlow<Float> = _fpsWidthScale.asStateFlow()

    private val _fpsAlpha = MutableStateFlow(getFpsAlpha())
    val fpsAlpha: StateFlow<Float> = _fpsAlpha.asStateFlow()

    private val _fpsShowFps = MutableStateFlow(getFpsShowFps())
    val fpsShowFps: StateFlow<Boolean> = _fpsShowFps.asStateFlow()

    private val _fpsShowCpu = MutableStateFlow(getFpsShowCpu())
    val fpsShowCpu: StateFlow<Boolean> = _fpsShowCpu.asStateFlow()

    private val _fpsShowWatts = MutableStateFlow(getFpsShowWatts())
    val fpsShowWatts: StateFlow<Boolean> = _fpsShowWatts.asStateFlow()

    private val _fpsShowTemp = MutableStateFlow(getFpsShowTemp())
    val fpsShowTemp: StateFlow<Boolean> = _fpsShowTemp.asStateFlow()

    private val _fpsShowRam = MutableStateFlow(getFpsShowRam())
    val fpsShowRam: StateFlow<Boolean> = _fpsShowRam.asStateFlow()

    private val _fpsShowRender = MutableStateFlow(getFpsShowRender())
    val fpsShowRender: StateFlow<Boolean> = _fpsShowRender.asStateFlow()

    private val _fpsShowGpuUsage = MutableStateFlow(getFpsShowGpuUsage())
    val fpsShowGpuUsage: StateFlow<Boolean> = _fpsShowGpuUsage.asStateFlow()

    private val _fpsShowCpuTemp = MutableStateFlow(getFpsShowCpuTemp())
    val fpsShowCpuTemp: StateFlow<Boolean> = _fpsShowCpuTemp.asStateFlow()

    private val _fpsShowCpuFreq = MutableStateFlow(getFpsShowCpuFreq())
    val fpsShowCpuFreq: StateFlow<Boolean> = _fpsShowCpuFreq.asStateFlow()

    private val _fpsShowGpuFreq = MutableStateFlow(getFpsShowGpuFreq())
    val fpsShowGpuFreq: StateFlow<Boolean> = _fpsShowGpuFreq.asStateFlow()

    private val _fpsShowGpuTemp = MutableStateFlow(getFpsShowGpuTemp())
    val fpsShowGpuTemp: StateFlow<Boolean> = _fpsShowGpuTemp.asStateFlow()

    private val _fpsPcVariant = MutableStateFlow(getFpsPcVariant())
    val fpsPcVariant: StateFlow<Int> = _fpsPcVariant.asStateFlow()

    private val _fpsRadius = MutableStateFlow(getFpsRadius())
    val fpsRadius: StateFlow<Float> = _fpsRadius.asStateFlow()

    private val _fpsBgMode = MutableStateFlow(getFpsBgMode())
    val fpsBgMode: StateFlow<Int> = _fpsBgMode.asStateFlow()

    private val _fpsBorder = MutableStateFlow(getFpsBorder())
    val fpsBorder: StateFlow<Boolean> = _fpsBorder.asStateFlow()

    private val _fpsShadow = MutableStateFlow(getFpsShadow())
    val fpsShadow: StateFlow<Boolean> = _fpsShadow.asStateFlow()

    private val _fpsInterval = MutableStateFlow(getFpsInterval())
    val fpsInterval: StateFlow<Long> = _fpsInterval.asStateFlow()

    private val _fpsPosition = MutableStateFlow(getFpsPosition())
    val fpsPosition: StateFlow<String> = _fpsPosition.asStateFlow()

    private val _fpsShowFrameTime = MutableStateFlow(getFpsShowFrameTime())
    val fpsShowFrameTime: StateFlow<Boolean> = _fpsShowFrameTime.asStateFlow()

    private val _fpsShowOnePercent = MutableStateFlow(getFpsShowOnePercent())
    val fpsShowOnePercent: StateFlow<Boolean> = _fpsShowOnePercent.asStateFlow()

    private val _fpsShowZeroOnePercent = MutableStateFlow(getFpsShowZeroOnePercent())
    val fpsShowZeroOnePercent: StateFlow<Boolean> = _fpsShowZeroOnePercent.asStateFlow()

    private val _fpsShowRamPercent = MutableStateFlow(getFpsShowRamPercent())
    val fpsShowRamPercent: StateFlow<Boolean> = _fpsShowRamPercent.asStateFlow()

    private val _fpsShowBattery = MutableStateFlow(getFpsShowBattery())
    val fpsShowBattery: StateFlow<Boolean> = _fpsShowBattery.asStateFlow()

    private val _fpsShowRefresh = MutableStateFlow(getFpsShowRefresh())
    val fpsShowRefresh: StateFlow<Boolean> = _fpsShowRefresh.asStateFlow()

    private val _fpsShowJank = MutableStateFlow(getFpsShowJank())
    val fpsShowJank: StateFlow<Boolean> = _fpsShowJank.asStateFlow()

    private val _fpsShowRecord = MutableStateFlow(getFpsShowRecord())
    val fpsShowRecord: StateFlow<Boolean> = _fpsShowRecord.asStateFlow()

    private val _fpsShowSpark = MutableStateFlow(getFpsShowSpark())
    val fpsShowSpark: StateFlow<Boolean> = _fpsShowSpark.asStateFlow()

    private val _fpsShowSizeControls = MutableStateFlow(getFpsShowSizeControls())
    val fpsShowSizeControls: StateFlow<Boolean> = _fpsShowSizeControls.asStateFlow()

    private val _fpsPosX = MutableStateFlow(getFpsPosX())
    val fpsPosX: StateFlow<Int> = _fpsPosX.asStateFlow()

    private val _fpsPosY = MutableStateFlow(getFpsPosY())
    val fpsPosY: StateFlow<Int> = _fpsPosY.asStateFlow()

    // --- UPDATE CHECK (GitHub releases zuanvfx02/zuan-kernel-manager-next) ---
    private val _checkManagerUpdates = MutableStateFlow(getCheckManagerUpdates())
    val checkManagerUpdates: StateFlow<Boolean> = _checkManagerUpdates.asStateFlow()

    fun getCheckManagerUpdates(): Boolean = prefs.getBoolean(CHECK_MANAGER_UPDATES_KEY, true)
    fun setCheckManagerUpdates(enabled: Boolean) {
        prefs.edit { putBoolean(CHECK_MANAGER_UPDATES_KEY, enabled) }
        _checkManagerUpdates.value = enabled
    }

    private val _checkBetaUpdates = MutableStateFlow(getCheckBetaUpdates())
    val checkBetaUpdates: StateFlow<Boolean> = _checkBetaUpdates.asStateFlow()

    fun getCheckBetaUpdates(): Boolean = prefs.getBoolean(CHECK_BETA_UPDATES_KEY, false)
    fun setCheckBetaUpdates(enabled: Boolean) {
        prefs.edit { putBoolean(CHECK_BETA_UPDATES_KEY, enabled) }
        _checkBetaUpdates.value = enabled
    }

    private val _showUpdateDialog = MutableStateFlow(getShowUpdateDialog())
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    fun getShowUpdateDialog(): Boolean = prefs.getBoolean(SHOW_UPDATE_DIALOG_KEY, true)
    fun setShowUpdateDialog(enabled: Boolean) {
        prefs.edit { putBoolean(SHOW_UPDATE_DIALOG_KEY, enabled) }
        _showUpdateDialog.value = enabled
    }

    private val _lastUpdateCheck = MutableStateFlow(prefs.getLong(LAST_UPDATE_CHECK_KEY, 0L))
    val lastUpdateCheck: StateFlow<Long> = _lastUpdateCheck.asStateFlow()
    fun setLastUpdateCheck(millis: Long) {
        prefs.edit { putLong(LAST_UPDATE_CHECK_KEY, millis) }
        _lastUpdateCheck.value = millis
    }

    companion object {
        private const val FPS_OVERLAY_ENABLED_KEY = "fps_overlay_enabled"
        private const val FPS_STYLE_KEY = "fps_overlay_style"
        private const val FPS_ORIENTATION_KEY = "fps_overlay_orientation"
        private const val FPS_COLOR_KEY = "fps_overlay_color"
        private const val FPS_SIZE_KEY = "fps_overlay_size"
        private const val FPS_WIDTH_SCALE_KEY = "fps_overlay_width_scale"
        private const val FPS_ALPHA_KEY = "fps_overlay_alpha"
        private const val FPS_SHOW_FPS_KEY = "fps_show_fps"
        private const val FPS_SHOW_CPU_KEY = "fps_show_cpu"
        private const val FPS_SHOW_WATTS_KEY = "fps_show_watts"
        private const val FPS_SHOW_TEMP_KEY = "fps_show_temp"
        private const val FPS_SHOW_RAM_KEY = "fps_show_ram"
        private const val FPS_SHOW_RENDER_KEY = "fps_show_render"
        private const val FPS_SHOW_GPU_USAGE_KEY = "fps_show_gpu_usage"
        private const val FPS_SHOW_CPU_TEMP_KEY = "fps_show_cpu_temp"
        private const val FPS_SHOW_CPU_FREQ_KEY = "fps_show_cpu_freq"
        private const val FPS_SHOW_GPU_FREQ_KEY = "fps_show_gpu_freq"
        private const val FPS_SHOW_GPU_TEMP_KEY = "fps_show_gpu_temp"

        private const val FPS_PC_VARIANT_KEY = "fps_overlay_pc_variant"
        private const val FPS_RADIUS_KEY = "fps_overlay_radius"
        private const val FPS_BG_MODE_KEY = "fps_overlay_bg_mode"
        private const val FPS_BORDER_KEY = "fps_overlay_border"
        private const val FPS_SHADOW_KEY = "fps_overlay_shadow"
        private const val FPS_INTERVAL_KEY = "fps_overlay_interval"
        private const val FPS_POSITION_KEY = "fps_overlay_position"
        private const val FPS_SHOW_FRAMETIME_KEY = "fps_show_frametime"
        private const val FPS_SHOW_1LOW_KEY = "fps_show_1low"
        private const val FPS_SHOW_01LOW_KEY = "fps_show_01low"
        private const val FPS_SHOW_RAM_PERCENT_KEY = "fps_show_ram_percent"
        private const val FPS_SHOW_BATTERY_KEY = "fps_show_battery"
        private const val FPS_SHOW_REFRESH_KEY = "fps_show_refresh"
        private const val FPS_SHOW_JANK_KEY = "fps_show_jank"
        private const val FPS_SHOW_RECORD_KEY = "fps_show_record"
        private const val FPS_SHOW_SPARK_KEY = "fps_show_spark"
        private const val FPS_SHOW_SIZE_CONTROLS_KEY = "fps_show_size_controls"
        private const val FPS_POS_X_KEY = "fps_overlay_pos_x"
        private const val FPS_POS_Y_KEY = "fps_overlay_pos_y"

        private const val SHOW_UPDATE_DIALOG_KEY = "show_update_dialog"
        private const val LAST_UPDATE_CHECK_KEY = "last_update_check"
        private const val CHECK_MANAGER_UPDATES_KEY = "check_manager_updates"
        private const val CHECK_BETA_UPDATES_KEY = "check_beta_updates"
        private const val THEME_KEY = "theme_mode"
        private const val THEME_COLOR_KEY = "theme_color_name"
        private const val DYNAMIC_COLOR_KEY = "dynamic_color_enabled"
        private const val POLLING_INTERVAL_KEY = "soc_polling_interval"
        private const val DPI_KEY = "app_custom_dpi"
        
        private const val CUSTOM_BG_KEY = "custom_bg_enabled"
        private const val BG_URI_KEY = "custom_bg_uri"
        private const val IS_VIDEO_KEY = "is_video_wallpaper_flag"
        private const val USE_SYSTEM_WALLPAPER_KEY = "use_system_wallpaper_master"
        
        private const val NAVBAR_TRANS_KEY = "navbar_transparency"
        private const val CARD_DARKNESS_KEY = "card_darkness"
        
        private const val BG_BLUR_KEY = "bg_blur_enabled"
        private const val BG_CONTRAST_KEY = "bg_contrast_value"
        private const val HAZE_ENABLED_KEY = "haze_ui_enabled"

        private const val BG_TYPE_KEY = "bg_type_selection"
        private const val SOLID_COLOR_KEY = "bg_solid_color_value"
        private const val EXPRESSIVE_ID_KEY = "bg_expressive_theme_id"

        private const val NAV_STYLE_KEY = "nav_style_selection"
        private const val NAV_LABEL_KEY = "nav_label_behavior"

        private const val WEATHER_EFFECT_KEY = "weather_effect_type"
        private const val WEATHER_INTENSITY_KEY = "weather_effect_intensity"
        private const val AVATAR_DECORATION_KEY = "avatar_decoration_style"

        private const val BG_SATURATION_KEY = "bg_saturation_value"
        private const val BLUR_STRENGTH_KEY = "blur_strength_value"
        private const val APPBAR_BANNER_ENABLED_KEY = "appbar_banner_enabled"
        private const val APPBAR_BANNER_URI_KEY = "appbar_banner_uri"
        private const val APPBAR_BANNER_TYPE_KEY = "appbar_banner_type"
        private const val APPBAR_BANNER_DIM_KEY = "appbar_banner_dim"

        private const val IS_CUSTOM_COLOR_KEY = "is_custom_color_enabled"
        private const val CUSTOM_PRIMARY_KEY = "custom_primary_color"
        private const val CUSTOM_SECONDARY_KEY = "custom_secondary_color"
        private const val CUSTOM_TERTIARY_KEY = "custom_tertiary_color"
        private const val APP_FONT_KEY = "app_font_choice"
        private const val ICON_SHAPE_STYLE_KEY = "icon_shape_style"
        private const val CUSTOM_ICON_IMAGE_KEY = "custom_icon_image_path"
        private const val CUSTOM_ICON_IMAGE_MENU_PREFIX = "custom_icon_image_menu_"
        private const val MENU_ICON_SHAPE_PREFIX = "menu_icon_shape_"
        private const val AUTO_ICON_IMAGE_COLOR_KEY = "auto_icon_image_color"
        private const val CUSTOM_FONT_PATH_KEY = "custom_font_path"
        private const val HIGH_REFRESH_RATE_KEY = "high_refresh_rate_beta"

        private const val CARD_GLASS_STYLE_KEY = "card_glass_style"
        private const val LIQUID_CORNER_RADIUS_KEY = "liquid_corner_radius"
        private const val LIQUID_BLUR_RADIUS_KEY = "liquid_blur_radius"
        private const val LIQUID_REFRACTION_HEIGHT_KEY = "liquid_refraction_height"
        private const val LIQUID_REFRACTION_AMOUNT_KEY = "liquid_refraction_amount"
        private const val LIQUID_CHROMATIC_ABERRATION_KEY = "liquid_chromatic_aberration"
        private const val TOGGLE_STYLE_KEY = "toggle_style"
        private const val SLIDER_STYLE_KEY = "slider_style"
        private const val DIALOG_STYLE_KEY = "dialog_style"
        private const val CUSTOM_TOGGLE_ON_IMAGE_KEY = "custom_toggle_on_image_path"
        private const val CUSTOM_TOGGLE_OFF_IMAGE_KEY = "custom_toggle_off_image_path"
        private const val CUSTOM_SLIDER_THUMB_IMAGE_KEY = "custom_slider_thumb_image_path"

        private const val HAS_COMPLETED_INTRO_KEY = "has_completed_intro"
        private const val ACCEPTED_TERMS_KEY = "accepted_terms"
        private const val ACCEPTED_RISK_KEY = "accepted_risk"
        private const val ADAPTIVE_ECO_MODE_KEY = "adaptive_eco_mode"
        private const val LITE_MODE_KEY = "lite_mode"
        /** Polling monitor minimal 5 detik di Versi Lite supaya CPU tidak terus bangun. */
        private const val LITE_MIN_POLLING_MS = 5000L

        private const val DEFAULT_POLLING_INTERVAL = 3000L
        private const val DEFAULT_DPI = 0
        
        @Volatile
        private var INSTANCE: SettingsPreference? = null

        fun getInstance(context: Context): SettingsPreference {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsPreference(context).also { INSTANCE = it }
            }
        }
    }

    // --- Getters/Setters ---
    fun setThemeMode(mode: ThemeMode) { prefs.edit { putString(THEME_KEY, mode.name) }; _themeMode.value = mode }
    private fun getThemeMode(): ThemeMode { return try { ThemeMode.valueOf(prefs.getString(THEME_KEY, ThemeMode.SYSTEM_DEFAULT.name).orEmpty()) } catch (e: Exception) { ThemeMode.SYSTEM_DEFAULT } }

    fun setCustomIconImagePath(path: String?) { prefs.edit { if (path == null) remove(CUSTOM_ICON_IMAGE_KEY) else putString(CUSTOM_ICON_IMAGE_KEY, path) }; _customIconImagePath.value = path }

    private fun menuIconKey(menuId: String): String = CUSTOM_ICON_IMAGE_MENU_PREFIX + menuId.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun loadCustomIconImagePaths(): Map<String, String> = runCatching {
        prefs.all
            ?.filterKeys { it.startsWith(CUSTOM_ICON_IMAGE_MENU_PREFIX) }
            ?.mapNotNull { (key, value) ->
                val path = value as? String ?: return@mapNotNull null
                key.removePrefix(CUSTOM_ICON_IMAGE_MENU_PREFIX) to path
            }?.toMap() ?: emptyMap()
    }.getOrDefault(emptyMap())

    fun setCustomIconImagePathForMenu(menuId: String, path: String?) {
        val key = menuIconKey(menuId)
        prefs.edit { if (path == null) remove(key) else putString(key, path) }
        _customIconImagePaths.value = loadCustomIconImagePaths()
    }

    private fun menuShapeKey(menuId: String): String = MENU_ICON_SHAPE_PREFIX + menuId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    private fun loadMenuIconShapes(): Map<String, String> = runCatching {
        prefs.all?.mapNotNull { (key, value) ->
            if (!key.startsWith(MENU_ICON_SHAPE_PREFIX) || value !is String) null
            else key.removePrefix(MENU_ICON_SHAPE_PREFIX) to value
        }?.toMap() ?: emptyMap()
    }.getOrDefault(emptyMap())
    fun setMenuIconShape(menuId: String, style: ZkmIconShapeStyle) {
        prefs.edit { putString(menuShapeKey(menuId), style.name) }
        _menuIconShapes.value = loadMenuIconShapes()
    }
    fun clearMenuIconShape(menuId: String) {
        prefs.edit { remove(menuShapeKey(menuId)) }
        _menuIconShapes.value = loadMenuIconShapes()
    }
    fun setAutoIconImageColor(enabled: Boolean) {
        prefs.edit { putBoolean(AUTO_ICON_IMAGE_COLOR_KEY, enabled) }
        _autoIconImageColor.value = enabled
    }

    fun getCustomIconImagePathForMenu(menuId: String): String? = _customIconImagePaths.value[menuId]

    fun setIconShapeStyle(style: ZkmIconShapeStyle) { prefs.edit { putString(ICON_SHAPE_STYLE_KEY, style.name) }; _iconShapeStyle.value = style }
    private fun getIconShapeStyle(): ZkmIconShapeStyle = ZkmIconShapeStyle.fromName(prefs.getString(ICON_SHAPE_STYLE_KEY, null))

    fun setAppFontChoice(choice: AppFontChoice) { prefs.edit { putString(APP_FONT_KEY, choice.name) }; _appFontChoice.value = choice }
    private fun getAppFontChoice(): AppFontChoice = try { AppFontChoice.valueOf(prefs.getString(APP_FONT_KEY, AppFontChoice.APP_DEFAULT.name).orEmpty()) } catch (_: Exception) { AppFontChoice.APP_DEFAULT }

    fun setCustomFontPath(path: String?) { prefs.edit { if (path == null) remove(CUSTOM_FONT_PATH_KEY) else putString(CUSTOM_FONT_PATH_KEY, path) }; _customFontPath.value = path }
    private fun getCustomFontPath(): String? = prefs.getString(CUSTOM_FONT_PATH_KEY, null)

    fun setHighRefreshRate(enabled: Boolean) { prefs.edit { putBoolean(HIGH_REFRESH_RATE_KEY, enabled) }; _highRefreshRate.value = enabled }
    private fun getHighRefreshRate(): Boolean = prefs.getBoolean(HIGH_REFRESH_RATE_KEY, false)

    fun setThemeColor(name: String) { prefs.edit { putString(THEME_COLOR_KEY, name) }; _themeColorName.value = name }
    private fun getThemeColorName(): String = prefs.getString(THEME_COLOR_KEY, "Green") ?: "Green"

    fun setDynamicColorEnabled(enabled: Boolean) { prefs.edit { putBoolean(DYNAMIC_COLOR_KEY, enabled) }; _isDynamicColor.value = enabled }
    private fun getDynamicColorEnabled(): Boolean = prefs.getBoolean(DYNAMIC_COLOR_KEY, false)

    private fun getPollingInterval(): Long = prefs.getLong(POLLING_INTERVAL_KEY, DEFAULT_POLLING_INTERVAL)

    fun setAppDpi(dpi: Int) { prefs.edit { putInt(DPI_KEY, dpi) }; _appDpi.value = dpi }
    private fun getAppDpi(): Int = prefs.getInt(DPI_KEY, DEFAULT_DPI)

    fun setCustomBackgroundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(CUSTOM_BG_KEY, enabled) }
        _isCustomBackground.value = enabled
    }
    private fun getCustomBackgroundEnabled(): Boolean = prefs.getBoolean(CUSTOM_BG_KEY, false)

    fun setIsVideoWallpaper(isVideo: Boolean) {
        prefs.edit { putBoolean(IS_VIDEO_KEY, isVideo) }
        _isVideoWallpaper.value = isVideo
    }
    private fun getIsVideoWallpaper(): Boolean = prefs.getBoolean(IS_VIDEO_KEY, false)

    fun setBackgroundImageUri(uri: String?) {
        prefs.edit { putString(BG_URI_KEY, uri) }
        _backgroundImageUri.value = uri
        if (uri == null) setIsVideoWallpaper(false)
    }
    private fun getBackgroundImageUri(): String? = prefs.getString(BG_URI_KEY, null)

    fun setUseSystemWallpaper(enabled: Boolean) { prefs.edit { putBoolean(USE_SYSTEM_WALLPAPER_KEY, enabled) }; _useSystemWallpaper.value = enabled }
    private fun getUseSystemWallpaper(): Boolean = prefs.getBoolean(USE_SYSTEM_WALLPAPER_KEY, false)

    fun setNavBarTransparency(value: Float) { prefs.edit { putFloat(NAVBAR_TRANS_KEY, value) }; _navBarTransparency.value = value }
    private fun getNavBarTransparency(): Float = prefs.toFloat(NAVBAR_TRANS_KEY, 0.5f)

    fun setCardDarkness(value: Float) { prefs.edit { putFloat(CARD_DARKNESS_KEY, value) }; _cardDarkness.value = value }
    private fun getCardDarkness(): Float = prefs.toFloat(CARD_DARKNESS_KEY, 0.5f)

    fun setBgBlurEnabled(enabled: Boolean) { prefs.edit { putBoolean(BG_BLUR_KEY, enabled) }; _isBgBlur.value = enabled }
    private fun getBgBlurEnabled(): Boolean = prefs.getBoolean(BG_BLUR_KEY, false)

    fun setBgContrast(value: Float) { prefs.edit { putFloat(BG_CONTRAST_KEY, value) }; _bgContrast.value = value }
    private fun getBgContrast(): Float = prefs.toFloat(BG_CONTRAST_KEY, 0f)

    fun setHazeEnabled(enabled: Boolean) { prefs.edit { putBoolean(HAZE_ENABLED_KEY, enabled) }; _isHazeEnabled.value = enabled }
    private fun getHazeEnabled(): Boolean = prefs.getBoolean(HAZE_ENABLED_KEY, true)

    // --- [BARU] Card Glass Style / Liquid Glass params ---
    fun setCardGlassStyle(style: CardGlassStyle) { prefs.edit { putString(CARD_GLASS_STYLE_KEY, style.name) }; _cardGlassStyle.value = style }
    private fun getCardGlassStyle(): CardGlassStyle = try { CardGlassStyle.valueOf(prefs.getString(CARD_GLASS_STYLE_KEY, CardGlassStyle.FROSTED_GLASS.name).orEmpty()) } catch (_: Exception) { CardGlassStyle.FROSTED_GLASS }

    fun setLiquidCornerRadius(value: Float) { prefs.edit { putFloat(LIQUID_CORNER_RADIUS_KEY, value) }; _liquidCornerRadius.value = value }
    private fun getLiquidCornerRadius(): Float = prefs.toFloat(LIQUID_CORNER_RADIUS_KEY, 20f)

    fun setLiquidBlurRadius(value: Float) { prefs.edit { putFloat(LIQUID_BLUR_RADIUS_KEY, value) }; _liquidBlurRadius.value = value }
    private fun getLiquidBlurRadius(): Float = prefs.toFloat(LIQUID_BLUR_RADIUS_KEY, 8f)

    fun setLiquidRefractionHeight(value: Float) { prefs.edit { putFloat(LIQUID_REFRACTION_HEIGHT_KEY, value) }; _liquidRefractionHeight.value = value }
    private fun getLiquidRefractionHeight(): Float = prefs.toFloat(LIQUID_REFRACTION_HEIGHT_KEY, 12f)

    fun setLiquidRefractionAmount(value: Float) { prefs.edit { putFloat(LIQUID_REFRACTION_AMOUNT_KEY, value) }; _liquidRefractionAmount.value = value }
    private fun getLiquidRefractionAmount(): Float = prefs.toFloat(LIQUID_REFRACTION_AMOUNT_KEY, 12f)

    fun setLiquidChromaticAberration(enabled: Boolean) { prefs.edit { putBoolean(LIQUID_CHROMATIC_ABERRATION_KEY, enabled) }; _liquidChromaticAberration.value = enabled }
    private fun getLiquidChromaticAberration(): Boolean = prefs.getBoolean(LIQUID_CHROMATIC_ABERRATION_KEY, false)

    // --- [BARU] Toggle / Slider / Dialog style ---
    fun setToggleStyle(style: ToggleStyle) { prefs.edit { putString(TOGGLE_STYLE_KEY, style.name) }; _toggleStyle.value = style }
    private fun getToggleStyle(): ToggleStyle = try { ToggleStyle.valueOf(prefs.getString(TOGGLE_STYLE_KEY, ToggleStyle.MATERIAL_EXPRESSIVE.name).orEmpty()) } catch (_: Exception) { ToggleStyle.MATERIAL_EXPRESSIVE }

    fun setSliderStyle(style: SliderStyle) { prefs.edit { putString(SLIDER_STYLE_KEY, style.name) }; _sliderStyle.value = style }
    private fun getSliderStyle(): SliderStyle = try { SliderStyle.valueOf(prefs.getString(SLIDER_STYLE_KEY, SliderStyle.MATERIAL_EXPRESSIVE.name).orEmpty()) } catch (_: Exception) { SliderStyle.MATERIAL_EXPRESSIVE }

    fun setDialogStyle(style: DialogStyle) { prefs.edit { putString(DIALOG_STYLE_KEY, style.name) }; _dialogStyle.value = style }
    private fun getDialogStyle(): DialogStyle = try { DialogStyle.valueOf(prefs.getString(DIALOG_STYLE_KEY, DialogStyle.HAZE_BLUR.name).orEmpty()) } catch (_: Exception) { DialogStyle.HAZE_BLUR }

    fun setCustomToggleOnImagePath(path: String?) { prefs.edit { if (path == null) remove(CUSTOM_TOGGLE_ON_IMAGE_KEY) else putString(CUSTOM_TOGGLE_ON_IMAGE_KEY, path) }; _customToggleOnImagePath.value = path }
    fun setCustomToggleOffImagePath(path: String?) { prefs.edit { if (path == null) remove(CUSTOM_TOGGLE_OFF_IMAGE_KEY) else putString(CUSTOM_TOGGLE_OFF_IMAGE_KEY, path) }; _customToggleOffImagePath.value = path }
    fun setCustomSliderThumbImagePath(path: String?) { prefs.edit { if (path == null) remove(CUSTOM_SLIDER_THUMB_IMAGE_KEY) else putString(CUSTOM_SLIDER_THUMB_IMAGE_KEY, path) }; _customSliderThumbImagePath.value = path }

    fun setBgType(type: BgType) {
        prefs.edit { putString(BG_TYPE_KEY, type.name) }
        _bgType.value = type
        setCustomBackgroundEnabled(type == BgType.GALLERY)
    }
    private fun getBgType(): BgType {
        return try { BgType.valueOf(prefs.getString(BG_TYPE_KEY, BgType.SYSTEM.name).orEmpty()) } catch (e: Exception) { BgType.SYSTEM }
    }

    fun setSolidColor(color: Int) { prefs.edit { putInt(SOLID_COLOR_KEY, color) }; _solidColor.value = color }
    private fun getSolidColor(): Int = prefs.getInt(SOLID_COLOR_KEY, Color(0xFF1A1A1A).toArgb())

    fun setExpressiveThemeId(id: Int) { prefs.edit { putInt(EXPRESSIVE_ID_KEY, id) }; _expressiveThemeId.value = id }
    private fun getExpressiveThemeId(): Int = prefs.getInt(EXPRESSIVE_ID_KEY, 0)

    fun setNavStyle(style: NavStyle) { prefs.edit { putString(NAV_STYLE_KEY, style.name) }; _navStyle.value = style }
    private fun getNavStyle(): NavStyle {
        return try { NavStyle.valueOf(prefs.getString(NAV_STYLE_KEY, NavStyle.LIQUID_CAPSULE.name).orEmpty()) } catch (e: Exception) { NavStyle.LIQUID_CAPSULE }
    }

    fun setNavLabelState(state: NavLabelState) { prefs.edit { putString(NAV_LABEL_KEY, state.name) }; _navLabelState.value = state }
    private fun getNavLabelState(): NavLabelState {
        return try { NavLabelState.valueOf(prefs.getString(NAV_LABEL_KEY, NavLabelState.ALWAYS_SHOW.name).orEmpty()) } catch (e: Exception) { NavLabelState.ALWAYS_SHOW }
    }

    // Weather Effect Getters/Setters
    fun setWeatherEffect(effect: WeatherEffect) { prefs.edit { putString(WEATHER_EFFECT_KEY, effect.name) }; _weatherEffect.value = effect }

    // Avatar Decoration
    fun setAvatarDecoration(style: AvatarDecorationStyle) { prefs.edit { putString(AVATAR_DECORATION_KEY, style.name) }; _avatarDecoration.value = style }
    private fun getWeatherEffect(): WeatherEffect {
        return try { WeatherEffect.valueOf(prefs.getString(WEATHER_EFFECT_KEY, WeatherEffect.NONE.name).orEmpty()) } catch (e: Exception) { WeatherEffect.NONE }
    }

    fun setWeatherIntensity(intensity: Float) { prefs.edit { putFloat(WEATHER_INTENSITY_KEY, intensity) }; _weatherIntensity.value = intensity }
    private fun getWeatherIntensity(): Float = prefs.toFloat(WEATHER_INTENSITY_KEY, 0.5f)

    // Background Adjustment Getters/Setters
    fun setBgSaturation(saturation: Float) { prefs.edit { putFloat(BG_SATURATION_KEY, saturation) }; _bgSaturation.value = saturation }
    private fun getBgSaturation(): Float = prefs.toFloat(BG_SATURATION_KEY, 1f)

    fun setBlurStrength(strength: Float) { prefs.edit { putFloat(BLUR_STRENGTH_KEY, strength) }; _blurStrength.value = strength }
    private fun getBlurStrength(): Float = prefs.toFloat(BLUR_STRENGTH_KEY, 20f)

    // [BETA] Appbar Banner Progressive
    fun setAppBarBannerEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(APPBAR_BANNER_ENABLED_KEY, enabled) }
        _appBarBannerEnabled.value = enabled
    }
    fun setAppBarBanner(uri: String?, type: String) {
        prefs.edit {
            putString(APPBAR_BANNER_URI_KEY, uri)
            putString(APPBAR_BANNER_TYPE_KEY, type)
        }
        _appBarBannerUri.value = uri
        _appBarBannerType.value = type
    }
    fun setAppBarBannerDim(dim: Float) {
        prefs.edit { putFloat(APPBAR_BANNER_DIM_KEY, dim) }
        _appBarBannerDim.value = dim
    }

    // Custom Color Getters/Setters
    fun setIsCustomColor(enabled: Boolean) { prefs.edit { putBoolean(IS_CUSTOM_COLOR_KEY, enabled) }; _isCustomColor.value = enabled }
    private fun getIsCustomColor(): Boolean = prefs.getBoolean(IS_CUSTOM_COLOR_KEY, false)

    fun setCustomPrimaryColor(color: Int) { prefs.edit { putInt(CUSTOM_PRIMARY_KEY, color) }; _customPrimaryColor.value = color }
    private fun getCustomPrimaryColor(): Int = prefs.getInt(CUSTOM_PRIMARY_KEY, Color(0xFF4A6595).toArgb())

    fun setCustomSecondaryColor(color: Int) { prefs.edit { putInt(CUSTOM_SECONDARY_KEY, color) }; _customSecondaryColor.value = color }
    private fun getCustomSecondaryColor(): Int = prefs.getInt(CUSTOM_SECONDARY_KEY, Color(0xFF586275).toArgb())

    fun setCustomTertiaryColor(color: Int) { prefs.edit { putInt(CUSTOM_TERTIARY_KEY, color) }; _customTertiaryColor.value = color }
    private fun getCustomTertiaryColor(): Int = prefs.getInt(CUSTOM_TERTIARY_KEY, Color(0xFF5E4B8B).toArgb())

    // Intro Getters/Setters
    fun setHasCompletedIntro(completed: Boolean) { 
        prefs.edit { putBoolean(HAS_COMPLETED_INTRO_KEY, completed) } 
        _hasCompletedIntro.value = completed 
    }
    private fun getHasCompletedIntro(): Boolean = prefs.getBoolean(HAS_COMPLETED_INTRO_KEY, false)

    fun setAcceptedTerms(accepted: Boolean) { 
        prefs.edit { putBoolean(ACCEPTED_TERMS_KEY, accepted) } 
        _acceptedTerms.value = accepted 
    }
    private fun getAcceptedTerms(): Boolean = prefs.getBoolean(ACCEPTED_TERMS_KEY, false)

    fun setAcceptedRisk(accepted: Boolean) { 
        prefs.edit { putBoolean(ACCEPTED_RISK_KEY, accepted) } 
        _acceptedRisk.value = accepted 
    }
    private fun getAcceptedRisk(): Boolean = prefs.getBoolean(ACCEPTED_RISK_KEY, false)

    // --- FPS OVERLAY GETTERS/SETTERS ---
    fun setFpsOverlayEnabled(enabled: Boolean) { prefs.edit { putBoolean(FPS_OVERLAY_ENABLED_KEY, enabled) }; _fpsOverlayEnabled.value = enabled }
    private fun getFpsOverlayEnabled(): Boolean = prefs.getBoolean(FPS_OVERLAY_ENABLED_KEY, false)

    fun setFpsStyle(style: Int) { prefs.edit { putInt(FPS_STYLE_KEY, style) }; _fpsStyle.value = style }
    private fun getFpsStyle(): Int = prefs.getInt(FPS_STYLE_KEY, 0)

    fun setFpsOrientation(orientation: Int) { prefs.edit { putInt(FPS_ORIENTATION_KEY, orientation) }; _fpsOrientation.value = orientation }
    private fun getFpsOrientation(): Int = prefs.getInt(FPS_ORIENTATION_KEY, 0)

    fun setFpsColor(color: String) { prefs.edit { putString(FPS_COLOR_KEY, color) }; _fpsColor.value = color }
    private fun getFpsColor(): String = prefs.getString(FPS_COLOR_KEY, "#00FF00") ?: "#00FF00"

    fun setFpsSize(size: Float) { prefs.edit { putFloat(FPS_SIZE_KEY, size) }; _fpsSize.value = size }
    private fun getFpsSize(): Float = prefs.getFloat(FPS_SIZE_KEY, 14f)

    fun setFpsWidthScale(scale: Float) { prefs.edit { putFloat(FPS_WIDTH_SCALE_KEY, scale) }; _fpsWidthScale.value = scale }
    private fun getFpsWidthScale(): Float = prefs.getFloat(FPS_WIDTH_SCALE_KEY, 1f)

    fun setFpsAlpha(alpha: Float) { prefs.edit { putFloat(FPS_ALPHA_KEY, alpha) }; _fpsAlpha.value = alpha }
    private fun getFpsAlpha(): Float = prefs.getFloat(FPS_ALPHA_KEY, 0.5f)

    fun setFpsShowFps(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_FPS_KEY, show) }; _fpsShowFps.value = show }
    private fun getFpsShowFps(): Boolean = prefs.getBoolean(FPS_SHOW_FPS_KEY, true)

    fun setFpsShowCpu(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_CPU_KEY, show) }; _fpsShowCpu.value = show }
    private fun getFpsShowCpu(): Boolean = prefs.getBoolean(FPS_SHOW_CPU_KEY, true)

    fun setFpsShowWatts(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_WATTS_KEY, show) }; _fpsShowWatts.value = show }
    private fun getFpsShowWatts(): Boolean = prefs.getBoolean(FPS_SHOW_WATTS_KEY, true)

    fun setFpsShowTemp(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_TEMP_KEY, show) }; _fpsShowTemp.value = show }
    private fun getFpsShowTemp(): Boolean = prefs.getBoolean(FPS_SHOW_TEMP_KEY, true)

    fun setFpsShowRam(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_RAM_KEY, show) }; _fpsShowRam.value = show }
    private fun getFpsShowRam(): Boolean = prefs.getBoolean(FPS_SHOW_RAM_KEY, true)

    fun setFpsShowRender(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_RENDER_KEY, show) }; _fpsShowRender.value = show }
    private fun getFpsShowRender(): Boolean = prefs.getBoolean(FPS_SHOW_RENDER_KEY, false)

    fun setFpsShowGpuUsage(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_GPU_USAGE_KEY, show) }; _fpsShowGpuUsage.value = show }
    private fun getFpsShowGpuUsage(): Boolean = prefs.getBoolean(FPS_SHOW_GPU_USAGE_KEY, false)

    fun setFpsShowCpuTemp(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_CPU_TEMP_KEY, show) }; _fpsShowCpuTemp.value = show }
    private fun getFpsShowCpuTemp(): Boolean = prefs.getBoolean(FPS_SHOW_CPU_TEMP_KEY, false)

    fun setFpsShowCpuFreq(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_CPU_FREQ_KEY, show) }; _fpsShowCpuFreq.value = show }
    private fun getFpsShowCpuFreq(): Boolean = prefs.getBoolean(FPS_SHOW_CPU_FREQ_KEY, false)

    fun setFpsShowGpuFreq(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_GPU_FREQ_KEY, show) }; _fpsShowGpuFreq.value = show }
    private fun getFpsShowGpuFreq(): Boolean = prefs.getBoolean(FPS_SHOW_GPU_FREQ_KEY, false)

    fun setFpsShowGpuTemp(show: Boolean) { prefs.edit { putBoolean(FPS_SHOW_GPU_TEMP_KEY, show) }; _fpsShowGpuTemp.value = show }
    private fun getFpsShowGpuTemp(): Boolean = prefs.getBoolean(FPS_SHOW_GPU_TEMP_KEY, false)

    fun setFpsPcVariant(v: Int) { prefs.edit { putInt(FPS_PC_VARIANT_KEY, v) }; _fpsPcVariant.value = v }
    private fun getFpsPcVariant(): Int = prefs.getInt(FPS_PC_VARIANT_KEY, 1)

    fun setFpsRadius(v: Float) { prefs.edit { putFloat(FPS_RADIUS_KEY, v) }; _fpsRadius.value = v }
    private fun getFpsRadius(): Float = prefs.getFloat(FPS_RADIUS_KEY, 10f)

    fun setFpsBgMode(v: Int) { prefs.edit { putInt(FPS_BG_MODE_KEY, v) }; _fpsBgMode.value = v }
    private fun getFpsBgMode(): Int = prefs.getInt(FPS_BG_MODE_KEY, 0)

    fun setFpsBorder(v: Boolean) { prefs.edit { putBoolean(FPS_BORDER_KEY, v) }; _fpsBorder.value = v }
    private fun getFpsBorder(): Boolean = prefs.getBoolean(FPS_BORDER_KEY, true)

    fun setFpsShadow(v: Boolean) { prefs.edit { putBoolean(FPS_SHADOW_KEY, v) }; _fpsShadow.value = v }
    private fun getFpsShadow(): Boolean = prefs.getBoolean(FPS_SHADOW_KEY, false)

    fun setFpsInterval(v: Long) { prefs.edit { putLong(FPS_INTERVAL_KEY, v) }; _fpsInterval.value = v }
    private fun getFpsInterval(): Long = prefs.getLong(FPS_INTERVAL_KEY, 1000L)

    fun setFpsPosition(v: String) { prefs.edit { putString(FPS_POSITION_KEY, v) }; _fpsPosition.value = v }
    private fun getFpsPosition(): String = prefs.getString(FPS_POSITION_KEY, "TopLeft") ?: "TopLeft"

    fun setFpsShowFrameTime(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_FRAMETIME_KEY, v) }; _fpsShowFrameTime.value = v }
    private fun getFpsShowFrameTime(): Boolean = prefs.getBoolean(FPS_SHOW_FRAMETIME_KEY, false)

    fun setFpsShowOnePercent(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_1LOW_KEY, v) }; _fpsShowOnePercent.value = v }
    private fun getFpsShowOnePercent(): Boolean = prefs.getBoolean(FPS_SHOW_1LOW_KEY, false)

    fun setFpsShowZeroOnePercent(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_01LOW_KEY, v) }; _fpsShowZeroOnePercent.value = v }
    private fun getFpsShowZeroOnePercent(): Boolean = prefs.getBoolean(FPS_SHOW_01LOW_KEY, false)

    fun setFpsShowRamPercent(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_RAM_PERCENT_KEY, v) }; _fpsShowRamPercent.value = v }
    private fun getFpsShowRamPercent(): Boolean = prefs.getBoolean(FPS_SHOW_RAM_PERCENT_KEY, false)

    fun setFpsShowBattery(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_BATTERY_KEY, v) }; _fpsShowBattery.value = v }
    private fun getFpsShowBattery(): Boolean = prefs.getBoolean(FPS_SHOW_BATTERY_KEY, false)

    fun setFpsShowRefresh(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_REFRESH_KEY, v) }; _fpsShowRefresh.value = v }
    private fun getFpsShowRefresh(): Boolean = prefs.getBoolean(FPS_SHOW_REFRESH_KEY, false)

    fun setFpsShowJank(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_JANK_KEY, v) }; _fpsShowJank.value = v }
    private fun getFpsShowJank(): Boolean = prefs.getBoolean(FPS_SHOW_JANK_KEY, false)

    fun setFpsShowRecord(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_RECORD_KEY, v) }; _fpsShowRecord.value = v }
    private fun getFpsShowRecord(): Boolean = prefs.getBoolean(FPS_SHOW_RECORD_KEY, true)

    fun setFpsShowSpark(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_SPARK_KEY, v) }; _fpsShowSpark.value = v }
    private fun getFpsShowSpark(): Boolean = prefs.getBoolean(FPS_SHOW_SPARK_KEY, false)

    fun setFpsShowSizeControls(v: Boolean) { prefs.edit { putBoolean(FPS_SHOW_SIZE_CONTROLS_KEY, v) }; _fpsShowSizeControls.value = v }
    private fun getFpsShowSizeControls(): Boolean = prefs.getBoolean(FPS_SHOW_SIZE_CONTROLS_KEY, true)

    fun setFpsPos(x: Int, y: Int) {
        prefs.edit { 
            putInt(FPS_POS_X_KEY, x)
            putInt(FPS_POS_Y_KEY, y)
        }
        _fpsPosX.value = x
        _fpsPosY.value = y
    }
    private fun getFpsPosX(): Int = prefs.getInt(FPS_POS_X_KEY, 20)
    private fun getFpsPosY(): Int = prefs.getInt(FPS_POS_Y_KEY, 100)
}

private fun SharedPreferences.toFloat(key: String, defValue: Float): Float = try { this.getFloat(key, defValue) } catch (e: Exception) { defValue }
