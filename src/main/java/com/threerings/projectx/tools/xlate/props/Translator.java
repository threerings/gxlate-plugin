package com.threerings.projectx.tools.xlate.props;

/**
 * Defines how to translate strings.
 */
public abstract class Translator
{
    /**
     * Translates a string with the given id. The source is provided for logging or error
     * handling.
     * @param source The canonical English string
     */
    public abstract String translate (String id, String source);
}
