package com.threerings.tools.gxlate;

/**
 * A language supported by the project x translation spreadsheet.
 */
public enum Language {
    EN, FR, IT, DE, ES, ZH, KO, JA;

    /**
     * The name of this language's column in the translation spreadsheet.
     */
    public String getHeaderStem ()
    {
        return name().toUpperCase();
    }

    /**
     * Finds the language with the given two letter code.
     * @throws IllegalArgumentException if no such language is found
     */
    public static Language findByCode (String shortCode)
    {
        return valueOf(shortCode.toUpperCase());
    }
}
