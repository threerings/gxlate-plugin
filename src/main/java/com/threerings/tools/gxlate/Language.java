package com.threerings.tools.gxlate;

/**
 * A language supported by the project x translation spreadsheet.
 */
public enum Language {
    FRENCH("fr"), ITALIAN("it"), GERMAN("de"), SPANISH("es"), CHINESE("zh"), KOREAN("ko"),
        JAPANESE("ja");

    /**
     * The two letter code for this language.
     */
    public final String code;

    /**
     * The name of this language's column in the translation spreadsheet.
     */
    public String getHeaderStem ()
    {
        return code.toUpperCase();
    }

    /**
     * Finds the language with the given two letter code.
     * @throws IllegalArgumentException if no such language is found
     */
    public static Language findByCode (String shortCode)
    {
        for (Language l : values()) {
            if (l.code.equals(shortCode)) {
                return l;
            }
        }
        throw new IllegalArgumentException("No language found with short code " + shortCode);
    }

    Language (String code)
    {
        this.code = code;
    }
}
