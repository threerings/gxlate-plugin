//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

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

    public String lowerName ()
    {
        return name().toLowerCase();
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
