package com.threerings.tools.gxlate;

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
        return String.format("%s_%s%s", m.group(1), language.name().toLowerCase(), m.group(4));
    }

    public static String baseName (String name)
    {
        Matcher m = PROPS.matcher(name);
        return m.matches() ? m.group(1) : null;
    }

    public static String extension (String name)
    {
        Matcher m = PROPS.matcher(name);
        return m.matches() ? m.group(4) : null;
    }
}
