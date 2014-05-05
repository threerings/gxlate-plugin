//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate.spreadsheet;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gdata.client.spreadsheet.CellQuery;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;

/**
 * A google spreadsheet in table form. A table has a row of cells at the top which are the
 * headers, or keys, for the rest of the data. Subsequent rows define a sequence of cells with a
 * value for each header column.
 */
public class Table
{
    /**
     * Returns a string formatted for google spreadsheet insertion of the current date and time.
     * Time zones are not considered.
     */
    public static String googleNow ()
    {
        return DATE_FORMAT.format(new Date());
    }

    /**
     * Creates a new table from the given worksheet entry. A worksheet is a single tab on a google
     * spreadsheet.
     */
    public Table (WorksheetEntry worksheet)
        throws ServiceException, IOException
    {
        _worksheet = worksheet;
        _rows = Lists.newArrayListWithExpectedSize(worksheet.getRowCount());
        process(worksheet.getService().getFeed(worksheet.getCellFeedUrl(), CellFeed.class));
    }

    /**
     * Gets the data rows (number 2 and up) in the table.
     */
    public List<Row> getRows ()
    {
        return Collections.unmodifiableList(_rows);
    }

    /**
     * Adds a new row to the table using the given column values. Since normally more than one row
     * will be added if one is, this does not update the internal structures. Call
     * {@link #refreshAddedRows()} for that.
     */
    public void addNewRow (Map<String, String> values)
        throws ServiceException, IOException
    {
        ListEntry newEntry = new ListEntry();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            newEntry.getCustomElements().setValueLocal(entry.getKey(), entry.getValue());
        }
        newEntry = _worksheet.getService().insert(_worksheet.getListFeedUrl(), newEntry);
        _addedRows++;
    }

    /**
     * Checks if any new rows have been added since the last refresh.
     */
    public boolean needsRefresh ()
    {
        return _addedRows > 0;
    }

    /**
     * Queries the worksheet for recently added rows so that we have up-to-date rows and cells
     * that can be used normally.
     */
    public void refreshAddedRows ()
        throws ServiceException, IOException
    {
        if (_addedRows == 0) {
            return;
        }
        CellQuery query = new CellQuery(_worksheet.getCellFeedUrl());
        query.setMinimumRow(_rows.size() + 2);
        query.setMaximumRow(_rows.size() + _addedRows + 1);
        process(_worksheet.getService().query(query, CellFeed.class));
        _addedRows = 0;
    }

    /**
     * Updates a cell in the table, adding it if it was previously empty.
     * @param row the row in which the cell resides
     * @param key the column header
     * @param value the new value
     */
    public void updateCell (Row row, String key, String value)
        throws ServiceException, IOException
    {
        CellEntry cell = row.getCellEntry(key);
        if (cell != null) {
            cell.changeInputValueLocal(value);
            cell.update();
            return;
        }

        // the more obvious approach of creating a new cell and uploading it doesn't work because
        // we don't have the resource version (ETag). it would probably be more efficient in the
        // long run to download all cells in advance, even empty ones

        int rowNum = row.getNum();
        int colNum = findColNum(key);

        CellQuery query = new CellQuery(_worksheet.getCellFeedUrl());
        query.setMinimumRow(rowNum);
        query.setMaximumRow(rowNum);
        query.setMinimumCol(colNum);
        query.setMaximumCol(colNum);
        query.setReturnEmpty(true);

        CellFeed feed = _worksheet.getService().query(query, CellFeed.class);
        for (CellEntry newCell : feed.getEntries()) {
            if (newCell.getCell().getRow() == rowNum && newCell.getCell().getCol() == colNum) {
                newCell.changeInputValueLocal(value);
                newCell.update();
                row.newCellInserted(key, newCell);
                return;
            }
        }
        throw new ServiceException("Blank cell not returned by custom query");
    }

    /**
     * Deletes the rows with the given numbers. This has to download the whole spreadsheet so
     * the caller should try and batch all rows to delete at once. The verifier is consulted to
     * double check that it is ok to delete each row.
     * <p>NOTE: If the API fails, then table is no longer in a consistent state. Further use will
     * generate runtime exceptions.</p>
     */
    public void deleteRows (Collection<Integer> rows, DeleteVerifier verifier)
        throws ServiceException, IOException
    {
        ListFeed feed = _worksheet.getService().getFeed(_worksheet.getListFeedUrl(), ListFeed.class);
        int row = 1, deleted = 0;
        for (ListEntry entry : feed.getEntries()) {
            row++;
            Row curr = _rows.get(row - 2);
            if (curr == null) {
                continue;
            }
            curr.setNum(curr.getNum() - deleted);
            if (!rows.contains(row)) {
                continue;
            }

            // convert from the list entry headers, which are all lower-cased, to our native cell
            // entry headers
            Map<String, String> fields = Maps.newHashMap();
            for (String hdr : _headers) {
                fields.put(hdr, entry.getCustomElements().getValue(hdr.toLowerCase()));
            }
            if (!verifier.confirmDelete(curr.getNum(), fields)) {
                continue;
            }

            // if this throws, null out our fields before re-throwing
            try {
                entry.delete();
            } catch (IOException ex) {
                _rows = null;
                _worksheet = null;
                throw ex;
            } catch (ServiceException ex) {
                _rows = null;
                _worksheet = null;
                throw ex;
            }

            _rows.get(row - 2).setDeleted();
            ++deleted;
        }

        // now remove rows, starting from the back
        for (int ii = _rows.size() - 1; ii >= 0; --ii) {
            Row r = _rows.get(ii);
            if (r != null && r.isDeleted()) {
                _rows.remove(ii);
            }
        }
    }

    private int findColNum (String key)
    {
        int idx = _headers.indexOf(key);
        if (idx == -1) {
            throw new RuntimeException("Key " + key + " not found");
        }
        return idx + 1;
    }

    private void process (CellFeed feed)
    {
        Map<Integer, List<CellEntry>> newRows = Maps.newHashMap();
        for (CellEntry cell : feed.getEntries()) {
            Integer rowNum = cell.getCell().getRow();
            List<CellEntry> cells = newRows.get(rowNum);
            if (cells == null) {
                newRows.put(rowNum, cells = Lists.newLinkedList());
            }
            cells.add(cell);
        }

        if (_headers == null ^ newRows.get(1) != null) {
            throw new IllegalStateException();
        }

        if (_headers == null) {
            _headers = Lists.newArrayList();
            for (CellEntry headerCell : newRows.get(1)) {
                growAndSet(_headers, headerCell.getCell().getCol() - 1,
                    headerCell.getCell().getValue());
            }
            newRows.remove(1);
        }

        for (Map.Entry<Integer, List<CellEntry>> entry : newRows.entrySet()) {
            // we want our data rows to index from zero, but on the doc they start at 2
            int rowIdx = entry.getKey() - 2;
            growAndSet(_rows, rowIdx, null);
            if (_rows.get(rowIdx) == null) {
                _rows.set(rowIdx, new Row(entry.getKey(), _headers, entry.getValue()));
            }
        }
    }

    protected static <T> void growAndSet (List<T> list, int idx, T value)
    {
        while (idx >= list.size()) {
            list.add(null);
        }
        list.set(idx, value);
    }

    private List<String> _headers;
    private List<Row> _rows;
    private WorksheetEntry _worksheet;
    private int _addedRows;

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yy HH:mm");
}
