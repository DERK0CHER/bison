package net.bison.data

import android.content.Context

/** The handful of switches the app has, in the platform's own preference store */
class Settings(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Whether a tone plays on each answer */
    var soundOn: Boolean
        get() = preferences.getBoolean(KEY_SOUND, true)
        set(value) = preferences.edit().putBoolean(KEY_SOUND, value).apply()

    private companion object {
        const val NAME = "bison"
        const val KEY_SOUND = "sound"
    }
}
