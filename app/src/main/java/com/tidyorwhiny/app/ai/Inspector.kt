package com.tidyorwhiny.app.ai

import android.graphics.Bitmap
import com.tidyorwhiny.app.speech.VoiceFeatures
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.util.Locale

/** A verdict with its reasons: `why` is what the Inspector saw or heard, `tip` what to do about it. */
data class MessVerdict(val tidy: Boolean, val level: Int, val title: String, val why: List<String>, val tip: String)

enum class WhineKind { WHINING, BEGGING, CHAMP }

data class WhineVerdict(val kind: WhineKind, val level: Int, val title: String, val why: List<String>, val tip: String)

/** The AI Inspector: a photo of a room judged tidy or a mess, a child's words judged whining, begging or a champ's. */
class Inspector(private val qwen: QwenClient = QwenClient()) {

    val enabled get() = qwen.enabled

    suspend fun judgeRoom(photo: Bitmap, onSlow: () -> Unit = {}): MessVerdict {
        val raw = qwen.chat(JSONArray().put(QwenClient.image(photo)).put(QwenClient.text(MESS_PROMPT.withContext())), onSlow)
        val o = jsonIn(raw)
        val level = o.optInt("level", 5).coerceIn(0, 10)
        val verdict = o.optString("verdict").lowercase()
        val tidy = if (verdict.isBlank()) level <= 4 else "tidy" in verdict
        return MessVerdict(tidy, level, o.optString("title").trim(), why(o), o.optString("tip").trim())
    }

    /** The words and, when the phone could measure it, how the voice sounded. */
    suspend fun judgeWords(transcript: String, voice: VoiceFeatures?, onSlow: () -> Unit = {}): WhineVerdict {
        val ask = WHINE_PROMPT.withContext() +
            (if (voice != null) "How it sounded, measured on the phone:\n" + voice.forPrompt() + "\n\n" else "") +
            "Transcript:\n" + transcript.ifBlank { "(no clear words, only voice sounds)" }
        val raw = qwen.chat(JSONArray().put(QwenClient.text(ask)), onSlow)
        val o = jsonIn(raw)
        val verdict = o.optString("verdict").lowercase()
        val kind = when {
            "beg" in verdict -> WhineKind.BEGGING
            "whin" in verdict -> WhineKind.WHINING
            else -> WhineKind.CHAMP
        }
        return WhineVerdict(kind, o.optInt("level", 5).coerceIn(0, 10), o.optString("title").trim(), why(o), o.optString("tip").trim())
    }

    /** The reasons, one short line each; a bare string is taken as one. */
    private fun why(o: JSONObject): List<String> {
        val a = o.optJSONArray("why") ?: return listOfNotNull(o.optString("why").trim().ifBlank { null })
        return (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }.take(5)
    }

    /** The first {...} in a reply, past any <think> the gateway let through. */
    private fun jsonIn(raw: String): JSONObject {
        val text = raw.replace(Regex("<think>[\\s\\S]*?</think>"), "")
        val a = text.indexOf('{')
        val b = text.lastIndexOf('}')
        if (a < 0 || b <= a) throw QwenException("no JSON in the reply: " + text.take(200))
        return JSONObject(text.substring(a, b + 1))
    }

    private fun String.withContext() =
        replace("LANG", Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" })
            .replace("TIME", timeOfDay(LocalTime.now()))

    /** The phone's clock, and what a tip may ask of a child at that hour: nothing lively after 18:00. */
    private fun timeOfDay(now: LocalTime): String {
        val clock = "%02d:%02d".format(now.hour, now.minute)
        return when (now.hour) {
            in 5..11 -> "It is $clock, morning. The tip may suit getting ready for the day: dressing, breakfast, packing a bag."
            in 12..17 -> "It is $clock, daytime. Any tip is fine, active ones included."
            in 18..21 -> "It is $clock, evening: winding down before bed. The tip must be calm and quiet: tidying up quietly, a bath, pyjamas, a book, a cuddle. No dancing, running, jumping, loud games, screens or sweets."
            else -> "It is $clock, night, past bedtime. The tip is to get to bed and sleep, quietly. Nothing active, no games, no screens."
        }
    }

    private companion object {
        const val MESS_PROMPT = """You are "the Inspector", a kind and fair judge in a family app called Tidy or Whiny. A parent has photographed a child's room or play area. Judge how messy it is.

Answer with one JSON object and nothing else:
{"verdict": "tidy" | "mess", "level": 0-10, "title": "...", "why": ["...", "..."], "tip": "..."}

verdict  "mess" if things are left lying around: clothes, toys, dishes, papers on the floor, bed, desk or chairs. Otherwise "tidy".
level    how messy it is: 0 spotless, 10 total chaos.
title    the verdict in 2 to 5 plain words, stated matter-of-factly: no jokes, no puns, no exclamation marks.
why      why you decided so: 2 to 4 short points, each naming one concrete thing you can see in the photo and where it is (for a tidy room, what is in its place).
tip      one short sentence for the child: if it is a mess, the one thing to tidy first; if tidy, a word of praise. Warm and plain, never mean, no jokes.

TIME Never suggest anything that does not suit this time of day, in the tip or anywhere else.

Write title, why and tip in LANG. If the photo is not of a room, judge what you can see and say so kindly in the tip."""

        const val WHINE_PROMPT = """You are "the Inspector", a kind and fair judge in a family app called Tidy or Whiny. A parent recorded a child. Below are measurements of how the voice sounded, when the phone could take them, and the speech-to-text transcript of the words. The transcript may lack punctuation, may be empty when there were only sounds, and drawn-out or repeated words (pleeease, noooo, but but) come through spelled out or repeated.

Judge by both the sound and the words. The sound that gives a whine away is drawn-out vowels: a whine can be told by it even when the words are harmless. Rough guides, to weigh, not rules:
calm talk  the longest vowel under about 0.6 s, little of the voiced time (under about 25%) in vowels held over 0.5 s
whining    vowels held a second or longer, much of the voiced time (over about 50%) in long vowels
begging    often the same drawn-out sound on "pleeease", with words asking for something: the words tell begging from whining
Between those, go mostly by the words.

Decide which it is:
whining  complaining, moaning, protesting, "it's not fair", "I don't want to", sad or grumpy
begging  asking for something again and again: please, can I, buy me, just one more, I want
champ    calm, polite, cheerful or helpful: no whining and no begging

Answer with one JSON object and nothing else:
{"verdict": "whining" | "begging" | "champ", "level": 0-10, "title": "...", "why": ["...", "..."], "tip": "..."}

level    how strong the whining or begging is: 0 none, 10 maximum. For champ, 0 to 2.
title    the verdict in 2 to 5 plain words, stated matter-of-factly: no jokes, no puns, no exclamation marks.
why      why you decided so: 2 to 4 short points, each quoting the words or naming the sound that gave it away (drawn-out vowels, short even syllables). Describe sounds in plain words a parent understands, no numbers or units.
tip      one short sentence for the child: a kind, plain tip, or praise for a champ. Never mean, no jokes.

TIME Never suggest anything that does not suit this time of day, in the tip or anywhere else.

Write title, why and tip in LANG.

"""
    }
}
