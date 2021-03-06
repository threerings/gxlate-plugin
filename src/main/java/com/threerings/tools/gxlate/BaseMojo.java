//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gdata.data.BaseEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import com.threerings.tools.gxlate.Domain.Row;
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
    private String languages;

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

    /**
     * Rules to apply to source files. For each file processed, if the base name of the file
     * matches the {@code <file>} member of a rule in the list, then for each property processed,
     * if the property name matches the {@code <ignore>} member of the rule, then that property
     * is not uploaded to google docs. During download, the property is copied from the english
     * to all other languages.
     */
    @Parameter()
    private List<SimpleRule> rules;

    /** Derived from the input parameter {@link #languages} by the base class' execute. */
    private final List<Language> languageList = Lists.newArrayList();

    /** Derived from the input parameter {@link #languages} by the base class' execute. */
    private final Set<Language> languageSet = Sets.newHashSet();

    /** Accumulation of errors during execution. If any failures are present at the end, the
     * build is failed. */
    protected final List<Exception> failures = Lists.newArrayList();

    public static class SimpleRule
    {
        public String file;
        public String ignore;

        public Rules.Rule toRule ()
        {
            return Rules.ID.matches(ignore).ignore();
        }
    }

    /**
     * Opens the configured google docs folder and document. Provides a method for opening a
     * worksheet.
     */
    public class Document
    {
        public final Folder folder;
        public final DocumentListEntry entry;

        public Document ()
            throws Exception
        {
            folder = openFolder();
            entry = requireEntry(folder.getSpreadsheets(), "document", docName);
        }

        /**
         * Downloads a worksheet from google docs and converts to a {@code Table}.
         */
        protected Table loadTable (String tabName)
            throws Exception
        {
            String docTitle = entry.getTitle().getPlainText();
            getLog().debug(String.format("Searching for worksheet '%s' in '%s'", tabName, docTitle));

            WorksheetEntry worksheet = requireEntry(folder.getWorksheets(entry), "worksheet", tabName);
            String worksheetTitle = worksheet.getTitle().getPlainText();
            getLog().info(String.format("Downloading '%s' of '%s'", worksheetTitle, docTitle));

            return new Table(worksheet);
        }
    }

    public final void execute ()
        throws MojoExecutionException, MojoFailureException
    {
        Preconditions.checkState(failures.isEmpty());

        for (String langCode : languages.split(",")) {
            Language lang = new Language(langCode.trim());
            if (languageSet.contains(lang)) {
                throw new MojoExecutionException("Duplicate language in list: " + langCode);
            }
            languageList.add(lang);
            languageSet.add(lang);
        }

        try {
            run();
        } catch (Exception ex) {
            throw new MojoExecutionException("", ex);
        }

        if (!failures.isEmpty()) {
            throw new MojoFailureException("Some operations failed (see log)");
        }
    }

    abstract protected void run () throws Exception;

    protected boolean checkOnly ()
    {
        return checkOnly;
    }

    protected Set<Language> languages ()
    {
        return languageSet;
    }

    protected List<Language> languageList ()
    {
        return languageList;
    }

    protected Folder openFolder ()
        throws Exception
    {
        getLog().info("Opening folder '" + folderId + "'");
        return Folder.open("gxlate-0.1", username, password, folderId);
    }

    protected DefaultTranslator init (DefaultTranslator translator)
    {
        return translator.setCheckOnly(checkOnly()).setLog(getLog());
    }

    protected Iterable<Row> getFilteredRows (PropsFile source)
    {
        Domain domain = new Domain.Simple();
        String name = source.getFile().getName();
        String base = Bundle.baseName(name);
        Domain.RuleSet ruleSet = new Domain.RuleSet();
        List<Rules.Rule> rrules = Lists.newArrayList();
        if (rules != null) {
            for (SimpleRule rule : rules) {
                if (rule.file.equals(base)) {
                    rrules.add(Rules.ID.matches(rule.ignore).ignore());
                }
            }
        }
        return ruleSet.add(domain, name, "", rrules.toArray(new Rules.Rule[]{})).
                get(domain, source, 0).generate();
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

    private static List<File> findAllProps (File dir, List<File> files)
        throws IOException
    {
        File[] listing = dir.listFiles();
        if (listing == null) {
            throw new IOException("Directory not listable: " + dir);
        }
        for (File file : listing) {
            if (file.isFile()) {
                if (Bundle.isEnglish(file.getName())) {
                    files.add(file);
                }
            } else if (file.isDirectory()) {
                findAllProps(file, files);
            }
        }
        return files;
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
