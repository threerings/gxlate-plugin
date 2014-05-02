package com.threerings.projectx.tools.xlate;

import static com.threerings.projectx.tools.xlate.AppUtils.log;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.kohsuke.args4j.Option;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.threerings.projectx.tools.xlate.AppUtils.CommonParameters;
import com.threerings.projectx.tools.xlate.props.PropsFile;
import com.threerings.projectx.tools.xlate.props.Translator;
import com.threerings.projectx.tools.xlate.spreadsheet.Index;
import com.threerings.projectx.tools.xlate.spreadsheet.Row;
import com.threerings.projectx.tools.xlate.spreadsheet.Table;

/**
 * Program for creating foreign language translations using strings downloaded from a google
 * spreadsheet.
 */
public class DownloadLanguage
{
    /**
     * Main entry point.
     */
    public static void main (String[] args)
    {
        Parameters params = CmdLine.parse(Parameters.class, "xlate DownloadLanguage", args);
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
        for (Language language : params.languages) {
            int sourcesFound = 0;
            for (PropsFile source : data.sources) {
                String file = source.getFile().getName();
                RowGenerator generator = RowGenerator.get(params.domain, source, params.supportTool);
                if (generator == null) {
                    continue;
                }
                sourcesFound++;
                int dot = file.lastIndexOf(".properties");
                File dest = new File(source.getFile().getParent(),
                    file.substring(0, dot) + "_" + language.code + file.substring(dot));

                PropsFile existingProps = null;
                if (dest.exists()) {
                    try {
                        existingProps = new PropsFile(dest);
                    } catch (IOException ex) {
                        log.error("Could not load existing props: " + dest);
                        data.failures.add(ex);
                    }
                }

                Map<Index.Key, RowGenerator.Row> generatedFields = Maps.newHashMap();
                for (RowGenerator.Row row : generator.generate()) {
                    generatedFields.put(data.index.key(
                                row.fields.toStringMap(Sets.immutableEnumSet(language))), row);
                }

                try {
                    log.info((dest.exists() ? "Updating" : "Creating") + " file: " + dest);
                    source.write(dest, new LangTranslator(params, data, generatedFields,
                        generator.getScopeName(), language, existingProps, params.domain.isGwt()));
                } catch (IOException ex) {
                    log.error("Unable to write language file: " + dest);
                    data.failures.add(ex);
                }
            }
            if (sourcesFound == 0) {
                log.warn("No generators found for domain: " + params.domain);
            }
        }
    }

    private static class LangTranslator extends Translator
    {
        LangTranslator (Parameters params, AppUtils.Data data,
            Map<Index.Key, RowGenerator.Row> generatedFields, String scope, Language language,
            PropsFile existingProps, boolean gwt)
        {
            this.params = params;
            this.data = data;
            this.generatedFields = generatedFields;
            this.scope = scope;
            this.language = language;
            this.existingProps = existingProps;
            this.gwt = gwt;
        }

        Parameters params;
        AppUtils.Data data;
        Map<Index.Key, RowGenerator.Row> generatedFields;
        String scope;
        Language language;
        PropsFile existingProps;
        boolean gwt;

        @Override
        public String translate (String id, String sourceStr)
        {
            Index.Key key = data.index.key(ImmutableMap.of(Field.SCOPE.getColumnName(), scope,
                Field.ID.getColumnName(), id));

            RowGenerator.Row genRow = generatedFields.get(key);
            if (genRow.status == Rules.Status.IGNORE) {
                // the generator wants this to not be translated, it is probably a url or image
                return sourceStr;
            }
            if (genRow.status == Rules.Status.OMIT) {
                // the generator wants to leave this string out entirely
                return null;
            }

            String placeholder =
                (params.noPrepend ? "" : "[" + language.code + "] ") + sourceStr.trim();
            Row row = data.index.lookup(key);
            if (row == null) {
                log.error("Row for " + key + " not yet in spreadsheet, using placeholder");
                return placeholder;
            }
            String stem = language.getHeaderStem();
            String newTranslation = row.getValues().get(stem);
            String oldTranslation = existingProps == null ? null : existingProps.getValue(id);

            // don't change translations that have not been verified
            String verify = row.getValues().get(Field.VERIFY.getColumnName(language));
            if (verify != null && verify.trim().length() > 0) {
                newTranslation = null;
            }
            if (newTranslation != null) {
                newTranslation = newTranslation.trim();
            }
            if (newTranslation == null) {
                if (oldTranslation == null) {
                    log.info("String " + key + " not yet translated, using placeholder");
                    return placeholder;
                } else if (oldTranslation.equals(placeholder)) {
                    log.info("String " + key + " not yet translated, retaining placeholder");
                    return placeholder;
                } else if (oldTranslation.startsWith("[" + language.code + "] ")) {
                    log.info("String " + key + " not yet translated, updating placeholder");
                    return placeholder;
                } else {
                    log.warn("Translation for " + key + " has disappeared, retaining "
                        + oldTranslation);
                    return oldTranslation;
                }
            } else if (newTranslation.isEmpty()) {
                log.warn("String " + key + " has blank translation, using placeholder");
                return placeholder;
            } else {
                if (gwt) {
                    newTranslation = newTranslation.replace("'", "''");
                }
                if (!newTranslation.equals(oldTranslation)) {
                    String lastImportedHeader = language.getHeaderStem() + "LastImported";
                    if (params.checkOnly) {
                        log.info("Found new translation for " + key);
                    } else {
                        log.info("Found new translation for " + key + ", updating");
                        try {
                            data.table.updateCell(row, lastImportedHeader, Table.googleNow());
                        } catch (Exception e) {
                            log.error("Unable to update the " + lastImportedHeader + " for row "
                                + row.getNum());
                            data.failures.add(e);
                        }
                    }
                }
                return newTranslation;
            }
        }
    }

    protected static class Parameters extends CommonParameters
    {
        @Option(name = "-r", required = true, metaVar = "PATH", usage = "The properties " +
            "directory or file to read English from. Translated files are written next to " +
            "English with a suffixed name")
        void setPropsPath (File path)
        {
            propsPath = path;
        }

        @Option(name = "-n", required = false, metaVar = "NOPREPEND", usage = "Do not prepend the "
                + "the language code for untranslated fields")
        boolean noPrepend;
    }
}
