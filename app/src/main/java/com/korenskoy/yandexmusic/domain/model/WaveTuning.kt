package com.korenskoy.yandexmusic.domain.model

/** My Wave tuning, as the server names it (rotor settings2). */
enum class WaveDiversity(val wire: String) {
    FAVORITE("favorite"),
    DISCOVER("discover"),
    POPULAR("popular"),
    ANY("default")
}

enum class WaveMood(val wire: String) {
    ACTIVE("active"),
    FUN("fun"),
    CALM("calm"),
    SAD("sad"),
    ANY("all")
}

enum class WaveLanguage(val wire: String) {
    RUSSIAN("russian"),
    FOREIGN("not-russian"),
    NO_WORDS("without-words"),
    ANY("any")
}

data class WaveTuning(
    val diversity: WaveDiversity = WaveDiversity.ANY,
    val mood: WaveMood = WaveMood.ANY,
    val language: WaveLanguage = WaveLanguage.ANY
) {
    val isDefault: Boolean
        get() = diversity == WaveDiversity.ANY && mood == WaveMood.ANY && language == WaveLanguage.ANY

    companion object {
        fun fromWire(diversity: String?, mood: String?, language: String?) = WaveTuning(
            WaveDiversity.entries.firstOrNull { it.wire == diversity } ?: WaveDiversity.ANY,
            WaveMood.entries.firstOrNull { it.wire == mood } ?: WaveMood.ANY,
            WaveLanguage.entries.firstOrNull { it.wire == language } ?: WaveLanguage.ANY
        )
    }
}
