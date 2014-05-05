//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate.spreadsheet;

import java.io.IOException;
import java.net.URL;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.Category;
import com.google.gdata.data.ILink;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetFeed;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

/**
 * Provides access to google documents, spreadsheets and worksheets in a folder.
 */
public class Folder
{
    /**
     * Queries the contents of the given google doc folder and returns a new folder instance.
     */
    public static Folder open (String appName, String user, String password, String folderId)
        throws AuthenticationException, ServiceException, IOException
    {
        DocsService docs = new DocsService(appName);
        docs.setUserCredentials(user, password);
        DocumentListFeed documentListFeed = docs.getFeed(new URL(
            "https://docs.google.com/feeds/default/private/full/folder%3A" + folderId
                + "/contents"), DocumentListFeed.class);
        Iterable<DocumentListEntry> contents = documentListFeed.getEntries();
        SpreadsheetService spreadsheets = new SpreadsheetService(appName);
        spreadsheets.setUserCredentials(user, password);

        return new Folder(Iterables.filter(contents, Predicates.not(DELETED)), spreadsheets);
    }

    /**
     * Gets the documents in the folder.
     */
    public Iterable<DocumentListEntry> getDocuments ()
    {
        return _docs;
    }

    /**
     * Gets the documents in the folder that are spreadsheets.
     */
    public Iterable<DocumentListEntry> getSpreadsheets ()
    {
        return Iterables.filter(_docs, IS_SPREADSHEET);
    }

    /**
     * Gets the worksheets (tabs) in a document known to be a spreadsheet.
     */
    public Iterable<WorksheetEntry> getWorksheets (DocumentListEntry spreadsheet)
        throws IOException, ServiceException
    {
        ILink link = spreadsheet.getLink(
            "http://schemas.google.com/spreadsheets/2006#worksheetsfeed", null);
        WorksheetFeed feed = _spreadsheets.getFeed(new URL(link.getHref()), WorksheetFeed.class);
        return feed.getEntries();
    }

    // internal, use factory
    private Folder (Iterable<DocumentListEntry> docs, SpreadsheetService spreadsheets)
    {
        _docs = docs;
        _spreadsheets = spreadsheets;
    }

    private static final Predicate<DocumentListEntry> DELETED = new Predicate<DocumentListEntry>() {
        public boolean apply (DocumentListEntry entry)
        {
            for (Category category : entry.getCategories()) {
                if (category.getLabel().equals("trashed")) {
                    return true;
                }
            }
            return false;
        }
    };

    private static final Predicate<DocumentListEntry> IS_SPREADSHEET = new Predicate<DocumentListEntry>() {
        public boolean apply (DocumentListEntry entry)
        {
            return "spreadsheet".equals(entry.getType());
        }
    };

    private Iterable<DocumentListEntry> _docs;
    private SpreadsheetService _spreadsheets;
}
