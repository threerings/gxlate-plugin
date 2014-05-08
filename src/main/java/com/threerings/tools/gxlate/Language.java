//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate;

import java.util.Collection;

/**
 * A language supported by the project x translation spreadsheet.
 */
public class Language {
    public static Language EN = new Language("en");

    public Language (String code)
    {
        _code = code.toUpperCase();
    }

    /**
     * The name of this language's column in the translation spreadsheet.
     */
    public String getHeaderStem ()
    {
        return _code;
    }

    /**
     * The name of this language's column in the translation spreadsheet, usually two capital
     * letters.
     */
    public String code ()
    {
        return _code;
    }

    public String lowerName ()
    {
        return _code.toLowerCase();
    }

    @Override
    public boolean equals (Object o)
    {
        if (!(o instanceof Language)) {
            return false;
        }
        return ((Language)o)._code.equals(_code);
    }

    @Override
    public int hashCode ()
    {
        return _code.hashCode();
    }

    /**
     * Finds the language with the given code.
     * @throws IllegalArgumentException if no such language is found
     */
    public static Language findByCode (Collection<Language> languages, String shortCode)
    {
        shortCode = shortCode.toUpperCase();
        for (Language lang : languages) {
            if (lang._code.equals(shortCode)) {
                return lang;
            }
        }
        throw new IllegalArgumentException("Language not found for code " + shortCode);
    }

    private final String _code;
}
