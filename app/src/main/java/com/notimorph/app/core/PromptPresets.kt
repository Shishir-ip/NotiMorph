package com.notimorph.app.core

/**
 * Reusable prompt presets. Used both for the global default prompt and for
 * per-app custom prompts, so users can start from a good base and tweak.
 */
data class Preset(
    val id: String,
    val title: String,
    val desc: String,
    val prompt: String,
)

object PromptPresets {

    /** Rules appended to every prompt so replies are watch-friendly and clean. */
    const val CORE_RULES =
        "You rewrite phone notifications so they display well on small smartwatch screens. " +
            "Reply with ONLY the final notification text: no quotes, no markdown, no explanations, " +
            "no extra line before or after. Preserve real names, numbers, amounts, OTP codes and links. " +
            "Keep the result under 300 characters unless stated otherwise."

    val DEFAULT_PROMPT =
        "Rewrite this notification in clean, simple English. Fix any broken or garbled characters. " +
            "Keep the sender, the key details (amounts, times, codes, links) and the core meaning. " +
            "Make it short and readable on a watch."

    val list: List<Preset> = listOf(
        Preset(
            "smart", "Smart Clean", "Best for everything: readable, complete, tidy",
            "Rewrite this notification in clean, simple English. Fix any broken or garbled characters. " +
                "Keep the sender, the key details (amounts, times, codes, links) and the core meaning. " +
                "Make it short and readable on a watch.",
        ),
        Preset(
            "short", "Ultra Short", "Squeeze into one tiny line",
            "Compress this notification into ONE very short line of at most 80 characters. " +
                "Keep only the single most important information.",
        ),
        Preset(
            "summary", "One-line Summary", "Full meaning, one smart sentence",
            "Summarize this notification in one clear, informative sentence. " +
                "Keep the most important facts and numbers.",
        ),
        Preset(
            "english", "Translate → English", "Any language becomes English",
            "Translate this notification into natural, simple English. Keep names, numbers and links unchanged.",
        ),
        Preset(
            "banglish", "Translate → Banglish", "Bengali meaning in English letters",
            "Translate this notification into Banglish: the Bengali (Bangla) language written in Latin/English " +
                "letters, the way people type casually (example: 'Kemon acho? Taka pathaisi?'). " +
                "Keep the tone natural and friendly. Keep names, numbers and links unchanged.",
        ),
        Preset(
            "bengali", "Translate → বাংলা", "Full Bengali script (watch must support Bengali font)",
            "Translate this notification into Bengali (Bangla) script. " +
                "Keep names, numbers and links unchanged.",
        ),
        Preset(
            "sim", "SIM / Offer SMS Decoder", "Operator SMS: decodes offers, codes, validity",
            "This is usually a promotional or service SMS from a mobile operator, often written in a local " +
                "language mixed with codes. Translate it fully to English, then clearly explain: what the " +
                "offer or message actually means, any dial/activation code, price, data/bundle amount and " +
                "validity. Format: first one short line with the core meaning, then the details.",
        ),
        Preset(
            "otp", "OTP Extractor", "Pull the verification code to the front",
            "Extract the one-time password / verification code from this notification. " +
                "Reply exactly in this format: 'CODE: <the code> · <service or sender>'. " +
                "If there is no code, reply with the cleaned original message.",
        ),
        Preset(
            "txn", "Bank / Transaction", "Amount, sender, balance at a glance",
            "This is a bank or payment notification. Reply in this format: " +
                "'<currency+amount> <sent|received> <to|from> <name> — <balance or date if present>'. " +
                "Keep the original currency symbol. Omit marketing text.",
        ),
        Preset(
            "fix", "Fix Broken Text", "Repair mojibake and encoding garbage",
            "This notification contains broken or wrongly-encoded text (mojibake, boxes, question marks). " +
                "Repair it into readable text in the ORIGINAL language of the message. Do not translate.",
        ),
        Preset(
            "strip", "Emoji & Noise Stripper", "Plain text only — perfect for tiny screens",
            "Remove all emojis, decorative symbols, repeated punctuation and marketing fluff from this " +
                "notification. Reply with plain, clean text that keeps every real detail.",
        ),
        Preset(
            "privacy", "Privacy Mask", "Hide sensitive numbers behind ✱",
            "Rewrite this notification but MASK sensitive data: replace OTP codes, full phone numbers, " +
                "email addresses and card/account numbers with ✱ (one ✱ per character, keep last 2 digits). " +
                "Keep the rest of the message readable.",
        ),
        Preset(
            "simple", "Explain Simply", "Jargon, links and codes explained plainly",
            "Rewrite this notification in very simple words that anyone can understand. " +
                "Briefly explain any jargon, short codes or strange formatting in plain language.",
        ),
    )

    fun byId(id: String): Preset? = list.firstOrNull { it.id == id }

    fun systemFor(prompt: String): String = prompt.trim() + "\n\n" + CORE_RULES
}
