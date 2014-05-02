package com.threerings.projectx.tools.xlate.spreadsheet;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.gdata.data.spreadsheet.CellEntry;

/**
 * A row in a google spreadsheet.
 */
public class Row
{
    /**
     * Gets a map of column headers to the cell values for this row. Backed by the underlying
     * google doc entry data.
     */
    public Map<String, String> getValues ()
    {
        return _stringValues;
    }

    /**
     * Gets the cell value entry corresponding a given column header.
     */
    public CellEntry getCellEntry (String key)
    {
        return _values.get(key);
    }

    /**
     * Gets the number of this row. Normally the header row is number 1 and data rows start at 2.
     */
    public int getNum ()
    {
        return _num;
    }

    /**
     * Returns true if the row has been deleted.
     */
    public boolean isDeleted ()
    {
        return _deleted;
    }

    // internal method called during table construction
    Row (int num, List<String> headers, List<CellEntry> cells)
    {
        _num = num;
        _values = Maps.newHashMapWithExpectedSize(cells.size());
        for (CellEntry cell : cells) {
            String header = headers.get(cell.getCell().getCol() - 1);
            _values.put(header, cell);
        }

        _stringValues = Maps.transformValues(_values, new Function<CellEntry, String>() {
            @Override
            public String apply (CellEntry entry)
            {
                return entry != null ? entry.getCell().getValue() : null;
            }
        });
    }

    void newCellInserted(String key, CellEntry cell)
    {
        _values.put(key, cell);
    }

    void setDeleted ()
    {
        _deleted = true;
    }

    void setNum (int num)
    {
        _num = num;
    }

    private int _num;
    private Map<String, CellEntry> _values;
    private Map<String, String> _stringValues;
    private boolean _deleted;
}
