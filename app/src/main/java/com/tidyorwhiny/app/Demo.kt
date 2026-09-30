package com.tidyorwhiny.app

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.tidyorwhiny.app.ai.MessVerdict
import com.tidyorwhiny.app.ai.WhineKind
import com.tidyorwhiny.app.ai.WhineVerdict
import org.json.JSONObject
import java.io.File

/**
 * Debug builds: a verdict screen straight from a JSON file, the AI not asked, for README screenshots.
 * The file and its photo go in the app's external files, demo/:
 *   adb push mess.json room.jpg /sdcard/Android/data/com.tidyorwhiny.app/files/demo/
 *   adb shell am start -S -n com.tidyorwhiny.app/.MainActivity --es demo mess.json
 * {"check": "mess", "photo": "room.jpg", "tidy": false, "level": 5, "title": "...", "why": ["..."], "tip": "..."}
 * {"check": "whine", "kind": "whining", "level": 7, "transcript": "...", "title": "...", "why": ["..."], "tip": "..."}
 */
object Demo {
    fun screen(context: Context, name: String): Screen? = try {
        val dir = File(context.getExternalFilesDir(null), "demo")
        val o = JSONObject(File(dir, name).readText())
        val why = o.optJSONArray("why").let { a -> (0 until (a?.length() ?: 0)).map { a!!.getString(it) } }
        val level = o.optInt("level", 5)
        when (o.getString("check")) {
            "mess" -> Screen.MessResult(
                BitmapFactory.decodeFile(File(dir, o.getString("photo")).path) ?: error("no photo"),
                MessVerdict(o.optBoolean("tidy"), level, o.optString("title"), why, o.optString("tip")),
            )
            else -> Screen.WhineResult(
                o.optString("transcript"),
                WhineVerdict(WhineKind.valueOf(o.optString("kind", "champ").uppercase()), level,
                    o.optString("title"), why, o.optString("tip")),
            )
        }
    } catch (e: Exception) {
        Log.w("Demo", "no demo screen from $name", e)
        null
    }
}
