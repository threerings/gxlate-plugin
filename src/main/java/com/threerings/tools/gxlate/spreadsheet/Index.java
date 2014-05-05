//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate.spreadsheet;

import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gdata.data.spreadsheet.CellEntry;

/**
 * Allows access to the rows of a {@link Table} using a tuple of column values.
 */
public class Index
{
    /**
     * A tuple of column values that supports equality, hashing and toString.
     */
    public static class Key
    {
        Key (List<String> fieldValues)
        {
            _fieldValues = fieldValues;
        }

        @Override public boolean equals (Object other)
        {
            return other instanceof Key && ((Key)other)._fieldValues.equals(_fieldValues);
        }

        @Override public int hashCode ()
        {
            return _fieldValues.hashCode();
        }

        @Override public String toString ()
        {
            return Joiner.on("::").join(_fieldValues);
        }

        List<String> _fieldValues;
    }

    /**
     * Base class for a problem that prevents correct indexing.
     */
    public static class Error
    {
    }

    /**
     * Represents a duplicate key in a spreadsheet, contains the offending rows.
     */
    public static class DuplicateKeyError extends Error
    {
        public final int row1;
        public final int row2;

        DuplicateKeyError (Row row1, Row row2)
        {
            this.row1 = row1.getNum();
            this.row2 = row2.getNum();
        }
    }

    /**
     * Represents a missing cell error, contains the offending row and the column header that is
     * missing.
     */
    public static class MissingCellError extends Error
    {
        public final int row;
        public final String header;

        MissingCellError (Row row, String header)
        {
            this.row = row.getNum();
            this.header = header;
        }
    }

    /**
     * Thrown when a table could not be indexed.
     */
    public static class IndexError extends Exception
    {
        public final List<Error> errors;

        public IndexError (List<Error> errors)
        {
            this.errors = errors;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Creates a new index for the given table and columns. Index creation stops and an exception
     * is thrown after a fixed number of problems occur.
     * @throws IndexError if there were any problems creating the index.
     */
    public Index (Table table, String... headers)
        throws IndexError
    {
        _rows = Maps.newHashMapWithExpectedSize(table.getRows().size());
        _headers = headers;

        List<Error> errors = Lists.newArrayList();
        for (Row row : table.getRows()) {
            if (row == null) {
                continue;
            }
            Key key = key(row, errors);
            Row old = _rows.put(key, row);
            if (old != null) {
                errors.add(new DuplicateKeyError(old, row));
            }
            if (errors.size() > 10) {
                throw new IndexError(errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new IndexError(errors);
        }
    }

    /**
     * Creates a key for the given field values.
     */
    public Key key (Map<String, String> fieldValues)
    {
        List<String> values = Lists.newArrayList();
        for (String header : _headers) {
            String value = fieldValues.get(header);
            values.add(Preconditions.checkNotNull(value));
        }
        return new Key(values);
    }

    /**
     * Finds the row belonging to the given key, or null if no such row exists.
     */
    public Row lookup (Key key)
    {
        Row row = _rows.get(key);
        if (row != null && row.isDeleted()) {
            _rows.remove(key);
            row = null;
        }
        return row;
    }

    private Key key (Row row, List<Error> errors)
    {
        List<String> key = Lists.newArrayListWithExpectedSize(_headers.length);
        for (String header : _headers) {
            CellEntry cell = row.getCellEntry(header);
            if (cell == null) {
                errors.add(new MissingCellError(row, header));
                continue;
            }
            key.add(cell.getCell().getValue());
        }
        return key.size() == _headers.length ? new Key(key) : null;
    }

    private Map<Key, Row> _rows;
    private String[] _headers;
}
