//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.gdata.data.ILink;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CustomElementCollection;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.threerings.tools.gxlate.spreadsheet.Folder;
import com.threerings.tools.gxlate.spreadsheet.Row;
import com.threerings.tools.gxlate.spreadsheet.Table;

@Mojo(name="doc-test")
public class DocTestMojo extends BaseMojo
{
    /**
     * Mode for dumping (see source code).
     */
    @Parameter(property="docTest.mode", defaultValue="")
    private String _mode;

    /**
     * Cell value to update (see source code).
     */
    @Parameter(property="docTest.updateCell")
    private String _updateCell;

    @Override
    public void execute ()
        throws MojoExecutionException, MojoFailureException
    {
        try {
            run();
        } catch (Exception ex) {
            throw new MojoExecutionException("Uh oh: " + ex.getMessage());
        }
    }

    private void run ()
        throws Exception
    {
        Folder folder = openFolder();
        Iterable<DocumentListEntry> docs = folder.getDocuments();

        if (_mode.contains("docs")) {
            getLog().info("Documents:");
            for (DocumentListEntry entry : docs) {
                getLog().info("    " + entry.getTitle().getPlainText());
                getLog().info("      Type: " + entry.getType());
                getLog().info("      Kind: " + entry.getKind());
                getLog().info("      Id: " + entry.getId());
                getLog().info("      DocId: " + entry.getDocId());
                getLog().info("      ResourceId: " + entry.getResourceId());
                getLog().info("      DocumentLink: " + entry.getDocumentLink().getHref());
                getLog().info("      SelfLink: " + entry.getSelfLink().getHref());
                for (ILink link : entry.getLinks()) {
                    getLog().info("      Link " + link.getRel() + ": " + link.getHref());
                }
            }
        }

        getLog().info("Spreadsheets:");
        for (DocumentListEntry spreadsheet : folder.getSpreadsheets()) {
            getLog().info("    " + spreadsheet.getTitle().getPlainText());
            for (WorksheetEntry entry : folder.getWorksheets(spreadsheet)) {

                if (_mode.contains("tags")) {
                    ListFeed feed = entry.getService().getFeed(entry.getListFeedUrl(), ListFeed.class);
                    for (ListEntry lentry : feed.getEntries()) {
                        CustomElementCollection coll = lentry.getCustomElements();
                        Set<String> tags = coll.getTags();
                        getLog().info("Tags: " + Joiner.on(",").join(tags));
                    }
                }

                if (_mode.contains("rows")) {
                    Table table = new Table(entry);
                    for (Row row : table.getRows()) {
                        Map<String, String> values = row.getValues();
                        getLog().info("      " + values.get("Scope") + "::" + values.get("Id"));
                        testUpdate(row);
                    }
                }
            }
        }
    }

    protected void testUpdate (Row row)
        throws Exception
    {
        if (_updateCell == null) {
            return;
        }

        if (row.getValues().get("Id").equals(_updateCell)) {
            CellEntry cell = row.getCellEntry("English");
            cell.changeInputValueLocal(cell.getCell().getValue() + ", whoa look at me!");
            cell.update();
        }
    }
}
