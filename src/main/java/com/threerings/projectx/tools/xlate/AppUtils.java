package com.threerings.projectx.tools.xlate;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.EnumSet;

import org.apache.log4j.Logger;
import org.kohsuke.args4j.Option;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gdata.data.BaseEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.common.base.Joiner;
import com.google.gdata.util.common.base.StringUtil;

import com.threerings.projectx.tools.xlate.props.PropsFile;
import com.threerings.projectx.tools.xlate.spreadsheet.Folder;
import com.threerings.projectx.tools.xlate.spreadsheet.Index;
import com.threerings.projectx.tools.xlate.spreadsheet.Table;
import com.threerings.projectx.tools.xlate.spreadsheet.Index.IndexError;

/**
 * Some methods and classes for xlate apps.
 */
public class AppUtils
{
    /** Log file for progress, errors, etc. */
    public static Logger log = Logger.getLogger("main");

    /**
     * The google folders to choose from.
     */
    public static enum FolderChoice
    {
        OOO("0B0pM-1AlnSTnZWI3ZjlkZTQtN2M2Zi00OWM5LWI0OWYtZmVlZGI2YzQxOGRl", false, true,
                Sets.immutableEnumSet(Language.FRENCH, Language.GERMAN, Language.SPANISH),
                Sets.immutableEnumSet(Domain.GAME_CLIENT_NAMES)),
        CJ("0B82cIjrbjMcfdFhvYWlQbnh1NlU", true, false,
                Sets.immutableEnumSet(Language.KOREAN/*, Language.JAPANESE*/),
                ImmutableSet.<Domain>of()),
        GC("0B82cIjrbjMcfcE55WXhiNVZnOTA", false, false,
                Sets.immutableEnumSet(Language.CHINESE),
                Sets.immutableEnumSet(Domain.SUPPORT)),
        CUSTOM(null, true, false, ImmutableSet.<Language>of(), ImmutableSet.<Domain>of());

        public final String id;

        public final boolean supportTool;
        public final boolean prependPlaceholder;
        public final Set<Language> languages;
        public final Set<Domain> excluded;

        FolderChoice (String id, boolean supportTool, boolean prependPlaceholder,
                Set<Language> languages, Set<Domain> excluded) {
            this.id = id;
            this.supportTool = supportTool;
            this.prependPlaceholder = prependPlaceholder;
            this.languages = languages;
            this.excluded = excluded;
        }
    }

    public static class Data
    {
        Table table;
        Index index;
        List<PropsFile> sources;

        /**
         * Failures that occurred while processing translations.
         */
        List<Exception> failures = Lists.newArrayList();
    }

    /**
     * Command line options shared by xlate apps.
     */
    public static class CommonParameters
    {
        @Option(name = "--folder-choice", required = false, metaVar = "FOLDER LABEL", usage =
                "The choice of Google Drive folder.")
        FolderChoice folderChoice;

        @Option(name = "-f", required = false, metaVar = "FOLDERID", usage =
                "A non-standard Google Drive folder id to use (for testing or new folders).")
        String customFolderId;

        File propsPath;

        @Option(name = "-u", required = false, metaVar = "USERNAME", usage =
                "Username for Google Drive")
        String username;

        @Option(name = "-p", required = false, metaVar = "PASSWORD", usage =
                "Password for Google Drive")
        String password;

        @Option(name = "-d", metaVar = "DOC", usage = "Document to upload to "
            + "(defaults to first found)")
        String docName;

        @Option(name = "-t", metaVar = "TAB", usage = "Worksheet tab name to upload to "
            + "(defaults to the one for the domain)")
        String worksheetName;

        @Option(name = "-c", required = false, usage = "Don't change anything, just check for changes")
        boolean checkOnly;

        @Option(name = "-o", usage = "Domain of the translations.")
        Domain domain;

        @Option(name = "-R", required = false, usage = "Look for properties files recursively")
        boolean recursive;

        @Option(name = "-l", required = false, metaVar = "LANGUAGES", usage = "Comma-separated "
                + "list of language codes to process. Codes are fr (French), it (Italian), de "
                + "(German), es (Spanish), zh (Traditional Chinese), ko (Korean), ja (Japanese).")
        String languageOpt;

        @Option(name = "--support-tool", required = false, usage = "Consider strings that are " +
                "only visible to support staff (for licensing a server, i.e. CJ).")
        boolean supportTool;

        Set<Language> languages;

        /**
         * Reads in any mandatory options from the console.
         */
        public void ensure ()
        {
            ensure(new ConsoleReader());
        }

        /**
         * Loads the google worksheet specified by the options and returns the resulting table
         * instance. If there is an error, the user is notified and null is returned.
         */
        public Table requireTable ()
            throws Exception
        {
            Folder folder = null;
            log.info("Opening folder '" + getFolderId() + "'");
            folder = Folder.open("threerings-xlate-0.1", username, password, getFolderId());

            DocumentListEntry doc = requireEntry(folder.getSpreadsheets(), "document", docName);

            if (worksheetName == null) {
                worksheetName = domain.defaultSheetName;
            }
            log.info("Searching for worksheet '" + worksheetName + "' in '" +
                doc.getTitle().getPlainText() + "'");
            WorksheetEntry worksheet = requireEntry(
                folder.getWorksheets(doc), "worksheet", worksheetName);

            log.info("Downloading '" + worksheet.getTitle().getPlainText() + "' of '"
                + doc.getTitle().getPlainText() + "'");
            return new Table(worksheet);
        }

        /**
         * Loads all the properties files in the {@link #propsPath}. This will be just one file if
         * the path is a file, or all the files in a directory if it is a directory. If
         * {@link #recursive} is set and the path is a directory, then all properties files in any
         * subdirectory will also be loaded.
         */
        public List<PropsFile> requireAllProps ()
            throws Exception
        {
            log.info("Loading English properties files");
            return AppUtils.loadAllProps(propsPath, recursive);
        }

        public String getFolderId () {
            return folderChoice == FolderChoice.CUSTOM ? customFolderId : folderChoice.id;
        }

        /**
         * Reads in mandatory options, using the given reader.
         */
        protected void ensure (ConsoleReader reader)
        {
            if (!StringUtil.isEmpty(languageOpt)) {
                if (!checkLanguages()) {
                    languageOpt = null;
                }
            }
            if (StringUtil.isEmpty(languageOpt)) {
                while (true) {
                    languageOpt = reader.ask(
                        "Languages to download",
                        false,
                        Joiner.on(",").join(
                            Iterables.transform(Arrays.asList(Language.values()),
                                new Function<Language, String>() {
                                    @Override
                                    public String apply (Language lang) {
                                        return lang.code;
                                    }
                                })));

                    if (checkLanguages()) {
                        break;
                    }
                }
            }

            if (StringUtil.isEmpty(username)) {
                username = reader.ask("Google account username: ", false, null);
            }
            if (StringUtil.isEmpty(password)) {
                password = reader.ask("Google account password: ", true, null);
            }
            // resolve the folder type and id
            if (folderChoice == null) {
                folderChoice = customFolderId == null ?
                    reader.askEnum(FolderChoice.class, "Folder type", FolderChoice.OOO) :
                    FolderChoice.CUSTOM;
            }
            if (folderChoice != FolderChoice.CUSTOM && customFolderId != null) {
                log.info("Ignoring folder id, using " + folderChoice);
            } else if (folderChoice == FolderChoice.CUSTOM && customFolderId == null) {
                customFolderId = reader.ask("Custom folder id: ", true, null);
            }
        }

        public Data loadData ()
        {
            try {
                return requireData();
            } catch (Exception ex) {
                log.error("Could not load data: " + ex);
                return null;
            }
        }

        public Data requireData ()
            throws Exception
        {
            Data data = new Data();
            data.sources = requireAllProps();
            data.table = requireTable();
            data.index = requireIndex(data.table);
            return data;
        }

        private boolean checkLanguages ()
        {
            languages = EnumSet.noneOf(Language.class);
            for (String str : languageOpt.split(",")) {
                try {
                    languages.add(Language.findByCode(str));
                } catch (IllegalArgumentException ex) {
                    System.err.println(str + " is not recognized as a language code");
                    return false;
                }
            }
            return !languages.isEmpty();
        }
    }

    public static final String DOMAINS = Domain.values().toString();

    /**
     * Creates an xlate index (scope::id) on the given table. If an error occurs, the user is
     * notified and null is returned.
     */
    public static Index requireIndex (Table table)
        throws Exception
    {
        try {
            return new Index(table, Field.SCOPE.getColumnName(), Field.ID.getColumnName());
        } catch (IndexError e) {
            log.error("Could not index spreadsheet:");
            for (Index.Error error : e.errors) {
                if (error instanceof Index.DuplicateKeyError) {
                    Index.DuplicateKeyError dke = (Index.DuplicateKeyError)error;
                    log.error("    Rows " + dke.row1 + " and " + dke.row2
                        + " have the same '" + Field.SCOPE.getColumnName() + "' and '" +
                        Field.ID.getColumnName() + "' column values");
                } else if (error instanceof Index.MissingCellError) {
                    Index.MissingCellError mce = (Index.MissingCellError)error;
                    log.error("    Row " + mce.row + " has no value for column '"
                        + mce.header + "'");
                }
            }

            throw e;
        }
    }

    /**
     * Finds the first google docs entry in a list of the given type and name. Returns null if no
     * such entry is found.
     */
    public static <E extends BaseEntry<E>> E requireEntry (
        Iterable<E> list, String type, String name)
            throws Exception
    {
        for (E entry : list) {
            if (name == null || entry.getTitle().getPlainText().equals(name)) {
                return entry;
            }
        }
        if (name == null) {
            throw new Exception("No " + type + "s found");
        } else {
            throw new Exception("No " + type + "s found with name " + name);
        }
    }

    public static List<File> findProps (File path, boolean recursive)
        throws IOException
    {
        List<File> files = Lists.newArrayList();
        if (path.isDirectory()) {
            File[] listing = path.listFiles();
            if (listing == null) {
                throw new IOException("Directory not listable: " + path);
            }
            for (File file : listing) {
                if (file.isFile() && file.getName().matches(".*.properties(\\.in)?$")) {
                    if (!file.getName().matches(".*_..\\.properties(\\.in)?$") ||
                            file.getName().matches(".*_en\\.properties(\\.in)?$")) {
                        files.add(file);
                    }
                } else if (file.isDirectory() && recursive) {
                    files.addAll(findProps(file, recursive));
                }
            }
        } else {
            files.add(path);
        }
        return files;
    }

    /**
     * Loads all the properties files in the given path. This will be just one file if the path
     * is a file, or all the files in a directory if it is a directory. If recursive is set and
     * the path is a directory, then all properties files in any subdirectory will also be loaded.
     */
    public static List<PropsFile> loadAllProps (File path, boolean recursive)
        throws IOException
    {
        List<PropsFile> files = Lists.newArrayList();
        for (File file : findProps(path, recursive)) {
            files.add(new PropsFile(file));
        }
        return files;
    }
}
