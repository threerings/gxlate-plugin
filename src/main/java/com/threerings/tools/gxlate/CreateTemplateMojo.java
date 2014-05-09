package com.threerings.tools.gxlate;

import java.net.URL;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.google.gdata.client.spreadsheet.CellQuery;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.ILink;
import com.google.gdata.data.Link;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;

/**
 * Goal to create the template worksheet for the translation document, based on the currently
 * configured languages. If a template tab already exists, it is deleted. Other tabs are not
 * affected. The new template sheet will have language columns in the same order as specified in
 * the mojo configuration.
 */
@Mojo(name="create-template")
public class CreateTemplateMojo extends BaseMojo
{
    @Parameter(property="gxlate.templateName", defaultValue="template")
    private String templateName;

    private static String[] coreColumns = {"Context", "Id", "Type", "FieldSize",
        "LocationInGame", "TechNotes", "ENLastUpdated", "EN"};

    private static String[] langColumns = {"", "Verify", "Check"};

    @Override
    protected void run ()
        throws Exception
    {
        Document doc = new Document();
        deleteTab(doc);
        createTab(doc);
        addHeaderRow(doc);
        getLog().info("Complete! Now freeze row 1, make any other adjustements, and duplicate "+
                "for each properties file");
    }

    private void deleteTab (Document doc)
        throws Exception
    {
        for (WorksheetEntry worksheet : doc.folder.getWorksheets(doc.entry)) {
            if (worksheet.getTitle().getPlainText().equals(templateName)) {
                getLog().info("Deleting tab " + templateName);
                worksheet.delete();
                return;
            }
        }
        getLog().info("Template tab not found to delete " + templateName);
    }

    private void createTab (Document doc)
        throws Exception
    {
        getLog().info("Creating tab " + templateName);
        SpreadsheetService service = doc.folder.getService();
        ILink link = doc.entry.getLink(
            com.google.gdata.data.spreadsheet.Namespaces.WORKSHEETS_LINK_REL,
            Link.Type.ATOM);

        // Create a local representation of the new worksheet.
        WorksheetEntry worksheet = new WorksheetEntry();
        worksheet.setTitle(new PlainTextConstruct(templateName));
        worksheet.setColCount(colCount());
        worksheet.setRowCount(100);

        service.insert(new URL(link.getHref()), worksheet);
    }

    private int colCount ()
    {
        return coreColumns.length + langColumns.length * languageList().size();
    }

    private void addHeaderRow (Document doc)
        throws Exception
    {
        getLog().info("Adding header row for " + templateName);
        WorksheetEntry worksheet = requireEntry(
            doc.folder.getWorksheets(doc.entry), "worksheet", templateName);

        CellQuery query = new CellQuery(worksheet.getCellFeedUrl());
        query.setMinimumRow(1);
        query.setMaximumRow(1);
        query.setMinimumCol(1);
        query.setMaximumCol(colCount());
        query.setReturnEmpty(true);

        CellFeed feed = worksheet.getService().query(query, CellFeed.class);
        for (CellEntry entry : feed.getEntries()) {
            int row = entry.getCell().getRow();
            int col = entry.getCell().getCol();
            if (row != 1) {
                getLog().warn("Cell query for row 1 returned row " + row);
                continue;
            }
            if (col > colCount()) {
                getLog().warn("Cell query returned large column " + col);
                continue;
            }
            if (col <= coreColumns.length) {
                entry.changeInputValueLocal(coreColumns[col - 1]);
            } else {
                col -= coreColumns.length + 1;
                entry.changeInputValueLocal(languageList().get(col / langColumns.length).code() +
                    langColumns[col % langColumns.length]);
            }
            entry.update();
        }
    }
}
