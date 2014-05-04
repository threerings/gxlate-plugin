package com.threerings.tools.gxlate;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gdata.util.common.base.Objects;

public class Bundle
{
    public static Pattern PROPS = Pattern.compile("(.*?)(_(..))?(\\.properties(\\.in)?)$");

    public static boolean isEnglish (String file)
    {
        Matcher m = PROPS.matcher(file);
        if (!m.matches()) {
            return false;
        }
        String lang = Objects.firstNonNull(m.group(3), "");
        return lang.isEmpty() || lang.equalsIgnoreCase(Language.EN.name());
    }

    public static String setLanguage (String name, Language language)
    {
        Matcher m = PROPS.matcher(name);
        if (!m.matches()) {
            throw new RuntimeException("setLanguage called with invalid name " + name);
        }
        return String.format("%s_%s%s", m.group(1), language.lowerName(), m.group(4));
    }

    public static File setLanguage (File source, Language language)
    {
        return new File(source.getParent(), setLanguage(source.getName(), language));
    }

    public static String baseName (File source)
    {
        return baseName(source.getName());
    }

    public static String baseName (String name)
    {
        Matcher m = PROPS.matcher(name);
        if (!m.matches()) {
            throw new RuntimeException("baseName called with invalid name " + name);
        }
        return m.group(1);
    }

    public static String extension (String name)
    {
        Matcher m = PROPS.matcher(name);
        if (!m.matches()) {
            throw new RuntimeException("extension called with invalid name " + name);
        }
        return m.group(4);
    }
}
