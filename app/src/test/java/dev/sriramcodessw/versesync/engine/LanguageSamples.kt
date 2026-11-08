package dev.sriramcodessw.versesync.engine

/**
 * One short phrase per supported language, as it would be saved in a song and as the speech
 * model would report it. Saved lyrics keep punctuation, capitals and diacritics. Speech output
 * is lowercase with no punctuation, and Chinese and Japanese models separate their words with
 * spaces even though written lyrics do not.
 *
 * Keys must match the language codes in `LanguageViewModel.availableLanguages`.
 */
internal data class LanguageSample(
    val name: String,
    val lyrics: String,
    val spoken: String,
)

internal val languageSamples: Map<String, LanguageSample> =
    mapOf(
        "en-us" to LanguageSample("English", "Amazing Grace! How sweet the sound,\nThat saved a wretch like me.", "amazing grace how sweet the sound that saved a wretch like me"),
        "hi" to LanguageSample("Hindi", "सारे जहाँ से अच्छा, हिन्दोस्ताँ हमारा", "सारे जहाँ से अच्छा हिन्दोस्ताँ हमारा"),
        "sa" to LanguageSample("Sanskrit", "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि", "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि"),
        "ta" to LanguageSample("Tamil", "யாதும் ஊரே யாவரும் கேளிர்", "யாதும் ஊரே யாவரும் கேளிர்"),
        "te" to LanguageSample("Telugu", "దేశ భాషలందు తెలుగు లెస్స", "దేశ భాషలందు తెలుగు లెస్స"),
        "mr" to LanguageSample("Marathi", "माझी मराठी मातृभाषा आहे", "माझी मराठी मातृभाषा आहे"),
        "bn" to LanguageSample("Bengali", "আমার সোনার বাংলা, আমি তোমায় ভালোবাসি", "আমার সোনার বাংলা আমি তোমায় ভালোবাসি"),
        "kn" to LanguageSample("Kannada", "ಕನ್ನಡ ನಾಡು ನುಡಿ ಬಹಳ ಸುಂದರ", "ಕನ್ನಡ ನಾಡು ನುಡಿ ಬಹಳ ಸುಂದರ"),
        "ml" to LanguageSample("Malayalam", "മലയാളം നമ്മുടെ മാതൃഭാഷ", "മലയാളം നമ്മുടെ മാതൃഭാഷ"),
        "gu" to LanguageSample("Gujarati", "ગુજરાતી આપણી માતૃભાષા છે", "ગુજરાતી આપણી માતૃભાષા છે"),
        "pa" to LanguageSample("Punjabi", "ਪੰਜਾਬੀ ਸਾਡੀ ਮਾਂ ਬੋਲੀ ਹੈ", "ਪੰਜਾਬੀ ਸਾਡੀ ਮਾਂ ਬੋਲੀ ਹੈ"),
        "ar" to LanguageSample("Arabic", "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", "الحمد لله رب العالمين"),
        "ko" to LanguageSample("Korean", "아리랑 아리랑 아라리요, 아리랑 고개로 넘어간다", "아리랑 아리랑 아라리요 아리랑 고개로 넘어간다"),
        "fr" to LanguageSample("French", "Frère Jacques, Frère Jacques, dormez-vous ? Dormez-vous ?", "frère jacques frère jacques dormez vous dormez vous"),
        "es" to LanguageSample("Spanish", "De colores, de colores se visten los campos en la primavera", "de colores de colores se visten los campos en la primavera"),
        "de" to LanguageSample("German", "Stille Nacht, heilige Nacht, alles schläft, einsam wacht", "stille nacht heilige nacht alles schläft einsam wacht"),
        "ru" to LanguageSample("Russian", "Калинка, калинка, калинка моя, в саду ягода малинка", "калинка калинка калинка моя в саду ягода малинка"),
        "cn" to LanguageSample("Chinese", "好一朵美丽的茉莉花，芬芳美丽满枝桠", "好 一 朵 美丽 的 茉莉花 芬芳 美丽 满 枝桠"),
        "ja" to LanguageSample("Japanese", "さくら さくら、弥生の空は見わたす限り", "さくら さくら 弥生 の 空 は 見わたす 限り"),
        "pt" to LanguageSample("Portuguese", "Ó que saudade da minha terra, onde canta o sabiá", "ó que saudade da minha terra onde canta o sabiá"),
        "tr" to LanguageSample("Turkish", "Ben seni çok seviyorum, canım benim", "ben seni çok seviyorum canım benim"),
        "vn" to LanguageSample("Vietnamese", "Tôi yêu Việt Nam, quê hương của tôi", "tôi yêu việt nam quê hương của tôi"),
        "it" to LanguageSample("Italian", "Tu scendi dalle stelle, o Re del cielo", "tu scendi dalle stelle o re del cielo"),
        "nl" to LanguageSample("Dutch", "Altijd is Kortjakje ziek, midden in de week", "altijd is kortjakje ziek midden in de week"),
        "el" to LanguageSample("Greek", "Η Ελλάδα είναι όμορφη χώρα, με ήλιο και θάλασσα", "η ελλάδα είναι όμορφη χώρα με ήλιο και θάλασσα"),
        "pl" to LanguageSample("Polish", "Sto lat, sto lat, niech żyje, żyje nam", "sto lat sto lat niech żyje żyje nam"),
        "sv" to LanguageSample("Swedish", "Blinka lilla stjärna där, hur jag undrar vem du är", "blinka lilla stjärna där hur jag undrar vem du är"),
        "uk" to LanguageSample("Ukrainian", "Ніч яка місячна, зоряна, ясная", "ніч яка місячна зоряна ясная"),
        "fa" to LanguageSample("Persian", "من ایران را دوست دارم، سرزمین زیبای من", "من ایران را دوست دارم سرزمین زیبای من"),
    )
