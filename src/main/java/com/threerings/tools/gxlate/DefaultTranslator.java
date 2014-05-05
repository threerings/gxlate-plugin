//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

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
    public final List<Exception> failures = Lists.newArrayList();

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
            _log.error(format("Row for %s not yet in spreadsheet, using placeholder", key));
            return placeholder;
        }
        String stem = _language.getHeaderStem();
        String newTranslation = row.getValues().get(stem);
        String oldTranslation = _existingProps == null ? null : _existingProps.getValue(id);

        // don't change translations that have not been verified
        String verify = row.getValues().get(Field.VERIFY.getColumnName(_language));
        if (verify != null && verify.trim().length() > 0) {
            newTranslation = null;
        }
        if (newTranslation != null) {
            newTranslation = newTranslation.trim();
        }
        if (newTranslation == null) {
            if (oldTranslation == null) {
                _log.info(format("String %s not yet translated, using placeholder", key));
                return placeholder;
            } else if (oldTranslation.equals(placeholder)) {
                _log.debug(format("String %s not yet translated, retaining placeholder", key));
                return placeholder;
            } else if (isPreviousPlaceholder(oldTranslation)) {
                _log.debug(format("Untranslated string %s changed, updating placeholder", key));
                return placeholder;
            } else {
                _log.info(format("Translation for %s has disappeared, retaining %s",
                    key, oldTranslation));
                return oldTranslation;
            }
        } else if (newTranslation.isEmpty()) {
            _log.error(format("String %s has blank translation, using placeholder", key));
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
                        _log.error(format("Unable to update the %s for row %d",
                            lastImportedHeader, row.getNum()));
                        failures.add(e);
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

    protected final Table _table;
    protected final Index _index;
    protected final Map<Index.Key, Domain.Row> _generatedFields;
    protected final Language _language;
    protected final PropsFile _existingProps;
    protected boolean _gwt;
    protected boolean _checkOnly;
    protected Log _log;
    protected String _placeholderPrefix;
}
