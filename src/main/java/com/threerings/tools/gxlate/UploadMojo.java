//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.threerings.tools.gxlate.props.PropsFile;
import com.threerings.tools.gxlate.spreadsheet.DeleteVerifier;
import com.threerings.tools.gxlate.spreadsheet.Index;
import com.threerings.tools.gxlate.spreadsheet.Row;
import com.threerings.tools.gxlate.spreadsheet.Table;

/**
 * Goal to upload new and changed English strings.
 */
@Mojo(name="upload")
public class UploadMojo extends BaseMojo
{
    /**
     * Whether rows that are no longer present in the source English files should be removed from
     * the spreadsheet.
     */
    @Parameter(property="gxlate.removeRows", defaultValue="false")
    private boolean removeRows;

    private Set<Index.Key> keysFound = Sets.newHashSet();

    @Override
    protected void run ()
        throws Exception
    {
        Preconditions.checkState(keysFound.isEmpty());

        if (checkOnly()) {
            getLog().info("Comparing English strings to spreadsheet and reporting differences. "
                + "No changes will be made.");
        }

        Document doc = new Document();
        Set<Integer> unused = Sets.newHashSet();
        for (PropsFile source : loadAllProps()) {
            Table table = doc.loadTable(Bundle.baseName(source.getFile().getName()));
            Index index = new Index(table, Field.ID.getColumnName());
            for (Domain.Row genRow : getFilteredRows(source)) {
                if (genRow.status == Rules.Status.IGNORE || genRow.status == Rules.Status.OMIT) {
                    continue;
                }

                String error = DefaultTranslator.checkBraces(genRow.fields.english(), unused);
                if (error != null) {
                    getLog().error(String.format("String %s %s", genRow.fields.id(), error));
                    failures.add(new Exception(genRow.fields.id()));
                    continue;
                }

                handleRow(table, index, genRow);
            }
            if (table.needsRefresh()) {
                getLog().info("Refreshing table to incorporate added rows");
                try {
                    // TODO: add support for the index being refreshed somewhere
                    table.refreshAddedRows();
                } catch (Exception ex) {
                    getLog().error("Refresh failed", ex);
                    failures.add(ex);
                }
            }
            if (removeRows) {
                doRemovals(table, index);
            }
        }
    }

    private void handleRow (Table table, Index index, Domain.Row genRow)
    {
        Index.Key key = index.key(genRow.fields.toStringMap(languages()));
        Row row = index.lookup(key);
        keysFound.add(key);
        if (row != null) {
            if (genRow.fields.needsUpload(row)) {
                String rowName = String.format("%s (row %d)", key, row.getNum());
                getLog().info(String.format("Update required for %s", rowName));
                if (!checkOnly()) {
                    try {
                        genRow.fields.put(Field.VERIFY, "CHANGE");
                        genRow.fields.upload(table, row, languages());
                    } catch (Exception ex) {
                        getLog().error(String.format("Failed to upate %s", rowName), ex);
                        failures.add(ex);
                    }
                }
            }
        } else {
            getLog().info("New row required for " + key);
            if (!checkOnly()) {
                try {
                    genRow.fields.put(Field.VERIFY, "NEW");
                    table.addNewRow(genRow.fields.toStringMap(languages()));
                } catch (Exception ex) {
                    getLog().error(String.format("Failed to insert new row for %s", key), ex);
                    failures.add(ex);
                }
            }
        }
    }

    private void doRemovals (Table table, Index index)
    {
        getLog().info("Checking for rows to remove");
        final Set<Index.Key> keysToRemove = Sets.newHashSet();
        for (Row row : table.getRows()) {
            if (row == null) {
                continue;
            }
            Index.Key key = index.key(row.getValues());
            if (!keysFound.contains(key)) {
                keysToRemove.add(key);
                if (checkOnly()) {
                    getLog().info(String.format("%s (row %d) requires removal", key, row.getNum()));
                }
            }
        }
        if (checkOnly()) {
            return;
        }

        Set<Integer> rowsToRemove = Sets.newHashSet();
        for (Index.Key key : keysToRemove) {
            rowsToRemove.add(index.lookup(key).getNum());
        }
        try {
            table.deleteRows(rowsToRemove, new VerifyKeyToDelete(index, keysToRemove));
        } catch (Exception ex) {
            getLog().error("Unable to delete rows", ex);
            failures.add(ex);
        }
    }

    private class VerifyKeyToDelete implements DeleteVerifier
    {
        Index index;
        Set<Index.Key> keysToRemove;

        VerifyKeyToDelete (Index index, Set<Index.Key> keysToRemove)
        {
            this.index = index;
            this.keysToRemove = keysToRemove;
        }

        @Override
        public boolean confirmDelete (int row, Map<String, String> fields)
        {
            Index.Key key = index.key(fields);
            if (key == null) {
                getLog().error("Row " + row + " was not formatted correctly, it did not "
                    + "contain all of the expected columns");
                return false;
            }
            if (!keysToRemove.contains(key)) {
                getLog().error("Row " + row + " was not found in original set of rows, maybe "
                    + " something changed.");
                return false;
            }
            getLog().info("Removing row " + key + " (row " + row + ")");
            return true;
        }
    }
}
