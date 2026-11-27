package com.talviom.bodycamhorror.client;

import java.util.Map;

/**
 * Лобби 1.0: тексты инструктажа из lang-файлов, общие для листов на стенах (BriefSheetRenderer) и папки (BriefingScreen).
 * Памятка темы: bh.brief.&lt;тема&gt;.title и .body (абзацы через «|»). Записка: bh.brief.note.&lt;id&gt;.
 * Про то, что ждёт в здании, только намёки: ни имени, ни правил, ни того, как оно нападает.
 */
public final class BriefText {
    /** Темы стендов по порядку (номер стенда = место в списке + 1). */
    public static final String[] TOPICS = {"task", "match", "gear", "light", "fear", "noise", "comms", "death"};

    /** Записки от руки, приколотые к каждой теме. */
    public static final Map<String, String[]> NOTES = Map.of(
            "task", new String[]{"task_a"},
            "match", new String[]{"match_a"},
            "gear", new String[]{"gear_a"},
            "light", new String[]{"light_a", "light_b"},
            "fear", new String[]{"fear_a"},
            "noise", new String[]{"noise_a"},
            "comms", new String[]{"comms_a"},
            "death", new String[]{"death_a"},
            "missing", new String[]{"missing_a", "missing_b", "missing_c", "missing_d"});

    private BriefText() {}

    public static String title(String topic) {
        return Vhs.tr("bh.brief." + topic + ".title");
    }

    public static String[] body(String topic) {
        return Vhs.trList("bh.brief." + topic + ".body");
    }

    public static String note(String id) {
        return Vhs.tr("bh.brief.note." + id);
    }

    public static String[] notes(String topic) {
        return NOTES.getOrDefault(topic, new String[0]);
    }
}
