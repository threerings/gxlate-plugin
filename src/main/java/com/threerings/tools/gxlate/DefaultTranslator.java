//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

import com.threerings.tools.gxlate.props.PropsFile;
import com.threerings.tools.gxlate.props.Translator;
import com.threerings.tools.gxlate.spreadsheet.Index;
import com.threerings.tools.gxlate.spreadsheet.Row;
import com.threerings.tools.gxlate.spreadsheet.Table;

import static java.lang.String.format;

public class DefaultTranslator extends Translator
{
    public static String checkBraces (String source, Set<Integer> braces)
    {
        for (int pos = 0;;pos++) {
            int open = source.indexOf('{', pos);
            if (open == -1) {
                break;
            }
            pos = source.indexOf('}', open);
            if (pos == -1) {
                return "has unclosed brace at character " + open;
            }
            String param = source.substring(open + 1, pos);
            Integer iparam;
            try {
                iparam = Integer.valueOf(param);
            } catch (NumberFormatException ex) {
                return "has invalid brace parameter '" + param + "' at character " + open;
            }
            braces.add(iparam);
        }
        return null;
    }

    public DefaultTranslator (Table table, Index index,
        Map<Index.Key, Domain.Row> generatedFields, Language language,
        PropsFile existingProps)
    {
        _table = table;
        _index = index;
        _generatedFields = generatedFields;
        _language = language;
        _existingProps = existingProps;
        _placeholderPrefix = String.format("[%s] ", language.lowerName());
        _log = new SystemStreamLog();
    }

    public DefaultTranslator setLog (Log log)
    {
        _log = log;
        return this;
    }

    public DefaultTranslator setGwt (boolean gwt)
    {
        _gwt = gwt;
        return this;
    }

    public DefaultTranslator setCheckOnly (boolean checkOnly)
    {
        _checkOnly = checkOnly;
        return this;
    }

    @Override
    public String translate (String id, String sourceStr)
    {
        Index.Key key = generateKey(id);

        Domain.Row genRow = _generatedFields.get(key);
        if (genRow.status == Rules.Status.IGNORE) {
            // the generator wants this to not be translated, it is probably a url or image
            return sourceStr;
        }
        if (genRow.status == Rules.Status.OMIT) {
            // the generator wants to leave this string out entirely
            return null;
        }

        final String placeholder = generatePlaceholder(sourceStr);
        Row row = _index.lookup(key);
        if (row == null) {
            _error.apply(format("Row for %s not yet in spreadsheet, using placeholder", key));
            _placeholders++;
            return placeholder;
        }
        String stem = _language.getHeaderStem();
        String newTranslation = row.getValues().get(stem);
        String oldTranslation = _existingProps == null ? null : _existingProps.getValue(id);

        String verify = row.getValues().get(Field.VERIFY.getColumnName(_language));
        String error = null;
        Function<String, Void> log = _info;
        if (newTranslation == null) {
            error = "untranslated";
        } else if (verify != null && verify.trim().length() > 0) {
            error = "unverified";
        } else {
            error = validate(sourceStr, newTranslation);
            log = _error;
        }

        if (error != null) {
            // download is blocked, return either a placeholder or the previous string and log
            // an appropriate message
            if (oldTranslation == null) {
                log.apply(format("String %s %s; using placeholder", key, error));
                _placeholders++;
                return placeholder;
            } else if (oldTranslation.equals(placeholder)) {
                log.apply(format("String %s %s; retaining placeholder", key, error));
                _placeholders++;
                return placeholder;
            } else if (isPreviousPlaceholder(oldTranslation)) {
                log.apply(format("String %s %s and EN changed; updating placeholder", key, error));
                _placeholders++;
                return placeholder;
            } else if (newTranslation == null) {
                // flag error, it's odd that the translation was all good then went away
                _error.apply(format("Translation for %s has disappeared; retaining '%s'",
                    key, oldTranslation));
                return oldTranslation;
            } else {
                log.apply(format("String %s %s; retaining previous", key, error));
                _retained++;
                return oldTranslation;
            }
        }

        newTranslation = newTranslation.trim();

        if (newTranslation.isEmpty()) {
            _error.apply(format("String %s has blank translation; using placeholder", key));
            _placeholders++;
            return placeholder;
        } else {
            if (_gwt) {
                newTranslation = newTranslation.replace("'", "''");
            }
            if (!newTranslation.equals(oldTranslation)) {
                String lastImportedHeader = _language.getHeaderStem() + "LastImported";
                if (_checkOnly) {
                    _log.info(format("Found new translation for %s", key));
                } else {
                    _log.info(format("Found new translation for %s, updating", key));
                    try {
                        _table.updateCell(row, lastImportedHeader, Table.googleNow());
                    } catch (Exception e) {
                        _error.apply(format("Unable to update the %s for row %d",
                            lastImportedHeader, row.getNum()));
                    }
                }
            }
            return newTranslation;
        }
    }

    public String generatePlaceholder (String sourceStr)
    {
        return String.format("%s%s", _placeholderPrefix, sourceStr.trim());
    }

    public Index.Key generateKey (String id)
    {
        return _index.key(ImmutableMap.of(Field.ID.getColumnName(), id));        
    }

    public boolean isPreviousPlaceholder (String previousStr)
    {
        return previousStr.startsWith(_placeholderPrefix);
    }

    public String validate (String english, String foreign)
    {
        Set<Integer> englishBraces = Sets.newHashSet();
        String error = checkBraces(english, englishBraces);
        if (error != null) {
            return error;
        }

        Set<Integer> foreignBraces = Sets.newHashSet();
        error = checkBraces(foreign, foreignBraces);
        if (error != null) {
            _errors++;
            return error;
        }

        Set<Integer> diff = Sets.difference(englishBraces, foreignBraces);
        if (diff.size() > 0) {
            _errors++;
            return "is missing parameter(s): " + Joiner.on(",").join(diff);
        }
        diff = Sets.difference(foreignBraces, englishBraces);
        if (diff.size() > 0) {
            _errors++;
            return "has extra parameter(s): " + Joiner.on(",").join(diff);
        }

        return null;
    }

    public int placeholders ()
    {
        return _placeholders;
    }

    public int errors ()
    {
        return _errors;
    }

    public int retained ()
    {
        return _retained;
    }

    protected Function<String, Void> _error = new Function<String, Void> () {
        @Override public Void apply (String msg) {
            _log.error(msg);
            _errors++;
            return null;
        }
    };

    protected Function<String, Void> _info = new Function<String, Void> () {
        @Override public Void apply (String msg) {
            _log.info(msg);
            return null;
        }
    };

    protected final Table _table;
    protected final Index _index;
    protected final Map<Index.Key, Domain.Row> _generatedFields;
    protected final Language _language;
    protected final PropsFile _existingProps;
    protected boolean _gwt;
    protected boolean _checkOnly;
    protected Log _log;
    protected String _placeholderPrefix;
    protected int _errors;
    protected int _placeholders;
    protected int _retained;
}
