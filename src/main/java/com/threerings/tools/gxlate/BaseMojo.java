package com.threerings.tools.gxlate;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import com.google.gdata.data.BaseEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.common.base.Objects;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.threerings.tools.gxlate.props.PropsFile;
import com.threerings.tools.gxlate.spreadsheet.Folder;
import com.threerings.tools.gxlate.spreadsheet.Table;

public abstract class BaseMojo extends AbstractMojo
{
    /**
     * The id of the google docs folder where the translation spreadsheet lives.
     */
    @Parameter(property="gxlate.folderId", required=true)
    private String folderId;

    /**
     * The name of the spreadsheet to target, defaults to the first one found in the folder.
     */
    @Parameter(property="gxlate.docName")
    private String docName;

    /**
     * The directory in which to find properties files.
     */
    @Parameter(property="gxlate.propsDir", defaultValue=".")
    private File propsDir;

    /**
     * Comma separated list of language codes to translate. Each code corresponds to a spreadsheet
     * column and a properties file extension. TODO: describe the extension
     */
    @Parameter(property="gxlate.languages", required=true)
    private Set<Language> languages;

    /**
     * Whether to just download the spreadsheet and print information on what needs to be done.
     */
    @Parameter(property="gxlate.checkOnly", defaultValue="false")
    private boolean checkOnly;

    /**
     * The Google account name to log into.
     */
    @Parameter(property="google.username", required=true)
    private String username;

    /**
     * The password for the Google account.
     */
    @Parameter(property="google.password", required=true)
    private String password;

    protected Set<Language> languages ()
    {
        return languages;
    }

    /**
     * Opens the configured google docs folder.
     */
    protected Folder openFolder ()
        throws Exception
    {
        getLog().info("Opening folder '" + folderId + "'");
        return Folder.open("gxlate-0.1", username, password, folderId);
    }

    /**
     * Downloads a spreadsheet tab from google docs.
     */
    protected Table loadTable (String tabName)
        throws Exception
    {
        Folder folder = openFolder();
        DocumentListEntry doc = requireEntry(folder.getSpreadsheets(), "document", docName);
        String docTitle = doc.getTitle().getPlainText();
        getLog().info(String.format("Searching for worksheet '%s' in '%s'", tabName, docTitle));

        WorksheetEntry worksheet = requireEntry(folder.getWorksheets(doc), "worksheet", tabName);
        String worksheetTitle = worksheet.getTitle().getPlainText();
        getLog().info(String.format("Downloading '%s' of '%s'", worksheetTitle, docTitle));

        return new Table(worksheet);
    }

    /**
     * Finds all the English props files (source data) in our configured props path.
     */
    protected List<File> findAllProps ()
        throws IOException
    {
        getLog().info("Finding English properties files");
        return findAllProps(propsDir, Lists.<File>newArrayList());
    }

    /**
     * Loads all the English props file (source data) in our configured props path.
     */
    protected List<PropsFile> loadAllProps ()
        throws IOException
    {
        getLog().info("Loading English properties files");
        List<PropsFile> result = Lists.newArrayList();
        for (File file : findAllProps(propsDir, Lists.<File>newArrayList())) {
            result.add(new PropsFile(file));
        }
        return result;
    }

    private static Pattern PROPS = Pattern.compile("(.*?)(_(..))?\\.properties(\\.in)?$");

    private static List<File> findAllProps (File dir, List<File> files)
        throws IOException
    {
        File[] listing = dir.listFiles();
        if (listing == null) {
            throw new IOException("Directory not listable: " + dir);
        }
        for (File file : listing) {
            if (file.isFile()) {
                Matcher m = PROPS.matcher(file.getName());
                if (m.matches()) {
                    String lang = Objects.firstNonNull(m.group(3), "");
                    if (lang.isEmpty() || lang.equalsIgnoreCase(Language.EN.name())) {
                        files.add(file);
                    }
                }
            } else if (file.isDirectory()) {
                findAllProps(file, files);
            }
        }
        return files;
    }

    protected static String baseName (String name)
    {
        Matcher m = PROPS.matcher(name);
        return m.matches() ? m.group(1) : null;
    }

    protected static <E extends BaseEntry<E>> E requireEntry (
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
}
