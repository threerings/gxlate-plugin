package com.threerings.projectx.tools.xlate;

import java.io.File;
import java.util.Map;
import java.util.Set;

import org.kohsuke.args4j.Option;

import com.google.common.collect.Sets;

import com.threerings.projectx.tools.xlate.AppUtils.CommonParameters;
import com.threerings.projectx.tools.xlate.props.PropsFile;
import com.threerings.projectx.tools.xlate.spreadsheet.DeleteVerifier;
import com.threerings.projectx.tools.xlate.spreadsheet.Index;
import com.threerings.projectx.tools.xlate.spreadsheet.Row;

import static com.threerings.projectx.tools.xlate.AppUtils.log;

/**
 * Program for uploading english strings to a google worksheet.
 */
public class UploadEnglish
{
    /**
     * Main entry point.
     */
    public static void main (String[] args)
    {
        Parameters params = CmdLine.parse(Parameters.class, "xlate UploadEnglish", args);
        if (params == null) {
            return;
        }

        params.ensure();
        AppUtils.Data data = params.loadData();
        if (data == null) {
            System.exit(1);
            return;
        }

        main(params, data);
        System.exit(data.failures.size());
    }

    public static void main (Parameters params, AppUtils.Data data)
    {
        if (params.checkOnly) {
            log.info("Comparing English strings to spreadsheet and reporting differences. "
                + "No changes will be made.");
        }

        Set<Index.Key> keysFound = Sets.newHashSet();
        for (PropsFile source : data.sources) {
            RowGenerator generator = RowGenerator.get(params.domain, source, params.supportTool);
            if (generator == null) {
                log.debug("No row generator defined for " + source.getFile());
                continue;
            }

            for (RowGenerator.Row genRow : generator.generate()) {
                if (genRow.status == Rules.Status.IGNORE || genRow.status == Rules.Status.OMIT) {
                    continue;
                }
                Index.Key key = data.index.key(genRow.fields.toStringMap(params.languages));
                Row row = data.index.lookup(key);
                keysFound.add(key);
                if (row != null) {
                    if (genRow.fields.needsUpload(row)) {
                        log.info("Update required for row " + row.getNum() + ": " + key);
                        if (!params.checkOnly) {
                            try {
                                genRow.fields.put(Field.VERIFY, "CHANGE");
                                genRow.fields.upload(data.table, row, params.languages);
                            } catch (Exception ex) {
                                log.error("Failed to upate row " + row.getNum() + ": " + key, ex);
                                data.failures.add(ex);
                            }
                        }
                    }
                } else {
                    log.info("New row required for " + key);
                    if (!params.checkOnly) {
                        try {
                            genRow.fields.put(Field.VERIFY, "NEW");
                            data.table.addNewRow(genRow.fields.toStringMap(params.languages));
                        } catch (Exception ex) {
                            log.error("Unable to insert new row for " + key, ex);
                            data.failures.add(ex);
                        }
                    }
                }
            }
            if (data.table.needsRefresh()) {
                log.info("Refreshing table to incorporate added rows");
                try {
                    // TODO: add support for the index being refreshed somewhere
                    data.table.refreshAddedRows();
                } catch (Exception ex) {
                    log.error("Refresh failed");
                    data.failures.add(ex);
                }
            }
        }

        if (params.remove) {
            doRemovals(data, keysFound, params.checkOnly);
        }
    }

    public static void doRemovals (AppUtils.Data data, Set<Index.Key> keysFound, boolean checkOnly)
    {
        log.info("Checking for rows to remove");
        final Set<Index.Key> keysToRemove = Sets.newHashSet();
        for (Row row : data.table.getRows()) {
            if (row == null) {
                continue;
            }
            Index.Key key = data.index.key(row.getValues());
            if (!keysFound.contains(key)) {
                keysToRemove.add(key);
                if (checkOnly) {
                    log.info("Row " + row.getNum() + " requires removal: " + key);
                }
            }
        }
        if (checkOnly) {
            return;
        }

        Set<Integer> rowsToRemove = Sets.newHashSet();
        for (Index.Key key : keysToRemove) {
            rowsToRemove.add(data.index.lookup(key).getNum());
        }
        try {
            data.table.deleteRows(rowsToRemove, new VerifyKeyToDelete(data.index, keysToRemove));
        } catch (Exception ex) {
            log.error("Unable to delete rows", ex);
            data.failures.add(ex);
        }
    }

    protected static class VerifyKeyToDelete implements DeleteVerifier
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
                log.error("Row " + row + " was not formatted correctly, it did not "
                    + "contain all of the expected columns");
                return false;
            }
            if (!keysToRemove.contains(key)) {
                log.error("Row " + row + " was not found in original set of rows, maybe "
                    + " something changed.");
                return false;
            }
            log.info("Removing row " + row + ": " + key);
            return true;
        }
    }

    protected static class Parameters extends CommonParameters
    {
        @Option(name = "-r", required = true, metaVar = "PROPS", usage = "The properties file " +
                "or directory to upload")
        void setPropsPath (File path)
        {
            this.propsPath = path;
        }

        @Option(name = "-x", required = false, usage = "Remove rows from the spreadsheet that no " +
                "longer correspond to properties. (Make sure program is running on entire set of " +
                "properties.)")
        boolean remove;
    }
}
