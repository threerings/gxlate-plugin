//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.gdata.util.ServiceException;

import com.threerings.tools.gxlate.spreadsheet.Row;
import com.threerings.tools.gxlate.spreadsheet.Table;

/**
 * Represents a mapping of spreadsheet headers to string values. Performs logic of updating the
 * spreadsheet.
 */
public class FieldMapping
{
    /**
     * Converts the mapping to a column name to value map for use directly with spreadsheet
     * classes.
     */
    public Map<String, String> toStringMap (Set<Language> languages)
    {
        Map<String, String> map = Maps.newHashMap();
        for (Map.Entry<Field, String> entry : _values.entrySet()) {
            Field field = entry.getKey();
            if (field.isLanguage()) {
                for (Language language : languages) {
                    map.put(field.getColumnName(language),
                            field.modifyValue(entry.getValue(), language));
                }
            } else {
                map.put(field.getColumnName(), entry.getValue());
            }
        }
        return map;
    }

    /**
     * Checks if a spreadsheet row requires an update.
     */
    public boolean needsUpload (Row row)
    {
        for (Field key : Field.values()) {
            if (needsUpload(row, key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates a spreadsheet row to match this mapping's values.
     */
    public void upload (Table table, Row row, Set<Language> languages)
        throws ServiceException, IOException
    {
        for (Field key : Field.values()) {
            if (key.isLanguage()) {
                for (Language language : languages) {
                    table.updateCell(row, key.getColumnName(language),
                            key.modifyValue(_values.get(key), language));
                }
            } else if (needsUpload(row, key) || key == Field.LAST_UPDATED) {
                table.updateCell(row, key.getColumnName(), _values.get(key));
            }
        }
    }

    public void put (Field field, String value)
    {
        _values.put(field, value);
    }

    public String english ()
    {
        return _values.get(Field.ENGLISH);
    }

    public String id ()
    {
        return _values.get(Field.ID);
    }

    /**
     * Creates a new field mapping. This is an internal constructor since normally this class is
     * only created from the {@link ProjectX} factory.
     */
    FieldMapping (Map<Field, String> values)
    {
        _values = values;
    }

    private boolean needsUpload (Row row, Field key)
    {
        return key.getUploadMode().needsUpload(
            row.getValues().get(key.getColumnName()), _values.get(key));
    }

    private Map<Field, String> _values;
}
