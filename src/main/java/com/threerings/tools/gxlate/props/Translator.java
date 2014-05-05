//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate.props;

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
