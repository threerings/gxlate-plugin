package com.threerings.tools.gxlate.props;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Represents the contents of a property file, prepared for translation. A translated properties
 * file has the exact same comment and blank lines and property ordering sequence as the canonical
 * (English) file.
 */
public class PropsFile
{
    /**
     * An entry in the properties file.
     */
    public interface Entry
    {
        /**
         * Gets the property key, e.g. "m.some_message_key".
         */
        String getId ();

        /**
         * Gets the most recent comment that applies to this entry.
         */
        String getComment ();

        /**
         * Gets the value of this entry key.
         */
        String getValue ();
    }

    /**
     * Creates a new file from the given io file, storing enough data to allow translation.
     */
    public PropsFile (File file)
        throws IOException
    {
        _file = file;
        FileReader in = new FileReader(file);
        try {
            read(new BufferedReader(in));
        } finally {
            in.close();
        }

        _properties = new Properties();
        FileInputStream fis = new FileInputStream(file);
        try {
            _properties.load(fis);
        } finally {
            fis.close();
        }

        // check for dupes, we can't cope with those
        Map<String, Line> props = Maps.newHashMap();
        for (Line l : _lines) {
            if (l.getType() != LineType.PROP) {
                continue;
            }
            Line old = props.put(l.getGroup(), l);
            if (old == null) {
                continue;
            }
            throw new RuntimeException("Duplicate property " + l.getGroup() +
                " in file " + file.getName() + ", on lines line " + old._num + " and " + l._num);
        }
    }

    public File getFile ()
    {
        return _file;
    }

    public String getValue (String id)
    {
        return _properties.getProperty(id);
    }

    /**
     * Writes the previously read file, using the given translations. Blank lines and comments are
     * written as in the original. Line continuations are added as needed to keep the length under
     * 100 columns.
     */
    public void write (File output, Translator translator)
        throws IOException
    {
        BufferedWriter writer = new BufferedWriter(new FileWriter(output));
        try {
            for (Line line : _lines) {
                switch(line.getType()) {
                case COMMENT:
                case BLANK:
                case BANNER:
                case PARENT:
                    writer.write(line.getContent());
                    writer.newLine();
                    break;
                case ID_KEYWORD:
                    writer.write(line.getGroup() + "$");
                    writer.newLine();
                    break;
                case PROP:
                    String prop = line.getGroup();
                    String trans = translator.translate(prop, _properties.getProperty(prop));
                    if (trans != null) {
                        writeProp(writer, prop, trans);
                        writer.newLine();
                    }
                    break;
                case CONTINUATION:
                    break;
                }
            }
        } finally {
            writer.close();
        }
    }

    public Iterable<Entry> properties ()
    {
        Predicate<Line> isPropLine = new Predicate<Line>() {
            @Override
            public boolean apply (Line line)
            {
                return line.getType() == LineType.PROP;
            }
        };
        Function<Line, Entry> toEntry = new Function<Line, Entry>() {
            @Override
            public Entry apply (final Line line)
            {
                return new Entry() {
                    @Override
                    public String getId ()
                    {
                        return line.getGroup();
                    }

                    @Override
                    public String getComment ()
                    {
                        return line.getComment();
                    }

                    @Override
                    public String getValue ()
                    {
                        return _properties.getProperty(line.getGroup());
                    }
                };
            }
        };
        return Iterables.transform(Iterables.filter(_lines, isPropLine), toEntry);
    }

    protected void writeProp (BufferedWriter writer, String prop, String value)
        throws IOException
    {
        List<StringBuilder> lines = Lists.newArrayList(new StringBuilder(prop).append(" = "));
        Matcher m = RE_SPECIAL.matcher(value);
        while (m.find()) {
            smartAppend(lines, value, m.regionStart(), m.start());
            switch(m.group().charAt(0)) {
            case '\t':
                smartAppend(lines, "\\t");
                break;
            case '\r':
                smartAppend(lines, "\\r");
                break;
            case '\f':
                smartAppend(lines, "\\f");
                break;
            case '\n':
                smartAppend(lines, "\\n");
                break;
            case '\\':
                smartAppend(lines, "\\\\");
                break;
            case '\u00a0':
                smartAppend(lines, " ");
                break;
            default:
                StringBuilder unicode = new StringBuilder();
                Formatter formatter = new Formatter(unicode);
                formatter.format("\\u%04x", (int)(m.group().charAt(0)));
                smartAppend(lines, unicode.toString());
            }
            m.region(m.end(), value.length());
        }
        smartAppend(lines, value, m.regionStart(), value.length());

        Iterator<StringBuilder> iter = lines.iterator();
        writer.write(iter.next().toString());
        while (iter.hasNext()) {
            writer.newLine();
            writer.write(iter.next().toString());
        }
    }

    protected void smartAppend (List<StringBuilder> lines, String appendage)
    {
        smartAppend(lines, appendage, 0, appendage.length());
    }

    protected void smartAppend (List<StringBuilder> lines, String appendage, int start, int end)
    {
        // sometimes this happens if a strings ends in \n
        if (start == end) {
            return;
        }

        // start by trying to append to the last line
        StringBuilder last = lines.get(lines.size() - 1);

        // if it ends with \n already, just copy the spaces and append the rest to a new line
        if (last.length() >= 2 && last.lastIndexOf("\\n") == last.length() - 2) {
            while (start < end && appendage.charAt(start) == ' ') {
                last.append(' ');
                start++;
            }
            newLine(lines);
            smartAppend(lines, appendage, start, end);
            return;
        }

        // safe to append without exceeding soft limit
        if (last.length() + end - start <= SOFT_MAX_COLS) {
            last.append(appendage, start, end);
            return;
        }

        // try not to dangle short bits onto the next line (that can still happen though)
        if (end - start < TOLERANCE) {
            last.append(appendage, start, end);
            return;
        }

        // find the last space that would get us under the soft limit
        int breakPos = appendage.lastIndexOf(' ',
            Math.min(end - 1, start + SOFT_MAX_COLS - last.length()));

        // not enough room, put it on a new line (unless we are already at the beginning of a
        // line, in which case just append it
        if (breakPos < start) {
            if (last.length() == INDENT.length() && last.indexOf(INDENT) == 0) {
                last.append(appendage, start, end);
                return;
            }
            // we have to cope with spaces here
            while (appendage.charAt(start) == ' ' && start < end) {
                last.append(' ');
                start++;
            }
            newLine(lines);
            smartAppend(lines, appendage, start, end);
            return;
        }

        // just in case the space is the first in a sequence, move up to just after the last one
        while (breakPos < end && appendage.charAt(breakPos) == ' ') {
            breakPos++;
        }

        // finally, append up to the break
        last.append(appendage, start, breakPos);

        // if there is more, put it on a new line
        if (end > breakPos) {
            newLine(lines);
            smartAppend(lines, appendage, breakPos, end);
        }
    }

    protected void newLine (List<StringBuilder> lines)
    {
        lines.get(lines.size() - 1).append("\\");
        lines.add(new StringBuilder(INDENT));
    }

    protected void read (BufferedReader in)
        throws IOException
    {
        Line line = null;
        while (true) {
            String content = in.readLine();
            if (content == null) {
                break;
            }
            _lines.add(line = new Line(content, line));
        }
    }

    protected static enum LineType {
        COMMENT, BANNER, PROP, CONTINUATION, BLANK, ID_KEYWORD, PARENT
    }

    protected class Line
    {
        Line (String content, Line previous)
        {
            if (previous == null) {
                previous = this;
            }

            _content = content;
            _num = previous._num + 1;

            Map<Pattern, LineType> pats = previous._continued ? CONTINUE_PATS : START_PATS;
            for (Pattern pat : pats.keySet()) {
                Matcher m = pat.matcher(_content);
                if (!m.matches()) {
                    continue;
                }
                _type = pats.get(m.pattern());
                if (m.groupCount() > 0) {
                    _group = m.group(1);
                }
                if (_type == LineType.PROP) {
                    _continued = m.pattern() == RE_PROP_MULT;
                } else if (_type == LineType.CONTINUATION) {
                    _continued = m.pattern() == RE_CONTINUATION;
                }
                _comment =
                    (_type == LineType.COMMENT && _group != null) ? _group : previous._comment;
                return;
            }

            throw new RuntimeException("Line did not match any pattern: \"" + this + "\"");
        }

        String getContent ()
        {
            return _content;
        }

        LineType getType ()
        {
            return _type;
        }

        String getGroup ()
        {
            return _group;
        }

        String getComment ()
        {
            return _comment;
        }

        @Override public String toString ()
        {
            return "Line " + _num + " from file " + _file.getName() + ": "
                + _content.substring(0, Math.min(40, _content.length()));
        }

        LineType _type;
        int _num;
        String _content;
        String _group;
        String _comment;
        boolean _continued;
    }

    protected File _file;
    protected Properties _properties;
    protected List<Line> _lines = Lists.newArrayList();

    protected static Pattern RE_BANNER = Pattern.compile("\\s*#\\.+\\s*");
    protected static Pattern RE_ID_KEYWORD = Pattern.compile("(\\s*#\\s+\\$Id).*\\$");
    protected static Pattern RE_GOOD_COMMENT = Pattern.compile("\\s*# (\\S.*)");
    protected static Pattern RE_COMMENT = Pattern.compile("\\s*#.*");
    protected static Pattern RE_PARENT = Pattern.compile("\\s*__.*");
    protected static Pattern RE_BLANK = Pattern.compile("\\s*$");
    protected static String _PROP_START = "^([a-zA-Z][-._a-zA-Z0-9]+)\\s*=\\s*.*";
    protected static Pattern RE_PROP_MULT = Pattern.compile(_PROP_START + "\\\\$");
    protected static Pattern RE_PROP_SINGLE = Pattern.compile(_PROP_START + "$");
    protected static Pattern RE_CONTINUATION = Pattern.compile(".*\\\\$");
    protected static Pattern RE_CONTINUATION_CLOSE = Pattern.compile(".*$");
    protected static final Map<Pattern, LineType> START_PATS = Maps.newLinkedHashMap();
    protected static final Map<Pattern, LineType> CONTINUE_PATS = Maps.newLinkedHashMap();

    protected static final String SPECIAL = "[\\t\\r\\f\\n\\\\\u0000-\u0019\u007f-\uffff]";
    protected static final Pattern RE_SPECIAL = Pattern.compile(SPECIAL);
    protected static final String INDENT = "  ";
    protected static final int SOFT_MAX_COLS = 95;
    protected static final int TOLERANCE = 25;

    static {
        START_PATS.put(RE_BANNER, LineType.BANNER);
        START_PATS.put(RE_ID_KEYWORD, LineType.ID_KEYWORD);
        START_PATS.put(RE_GOOD_COMMENT, LineType.COMMENT);
        START_PATS.put(RE_COMMENT, LineType.COMMENT);
        START_PATS.put(RE_PARENT, LineType.PARENT);
        START_PATS.put(RE_BLANK, LineType.BLANK);
        START_PATS.put(RE_PROP_MULT, LineType.PROP);
        START_PATS.put(RE_PROP_SINGLE, LineType.PROP);
        CONTINUE_PATS.put(RE_CONTINUATION, LineType.CONTINUATION);
        CONTINUE_PATS.put(RE_CONTINUATION_CLOSE, LineType.CONTINUATION);
    }
}
