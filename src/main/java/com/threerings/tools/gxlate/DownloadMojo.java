//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import com.google.common.collect.Maps;

import org.apache.maven.plugins.annotations.Mojo;

import com.threerings.tools.gxlate.props.PropsFile;
import com.threerings.tools.gxlate.spreadsheet.Index;
import com.threerings.tools.gxlate.spreadsheet.Table;

/**
 * Goal to download changes to the translated strings.
 */
@Mojo(name="download")
public class DownloadMojo extends BaseMojo
{
    @Override
    protected void run ()
        throws Exception
    {
        Document doc = new Document();
        int placeholders = 0, errors = 0, retained = 0;
        for (PropsFile source : loadAllProps()) {
            Table table = doc.loadTable(Bundle.baseName(source.getFile()));
            Index index = new Index(table, Field.ID.getColumnName());
            for (Language language : languages()) {
                Map<Index.Key, Domain.Row> generatedFields = Maps.newHashMap();
                for (Domain.Row row : getFilteredRows(source)) {
                    generatedFields.put(index.key(
                        row.fields.toStringMap(Collections.singleton(language))), row);
                }

                File dest = Bundle.setLanguage(source.getFile(), language);
                PropsFile existingProps = null;
                if (dest.exists()) {
                    try {
                        existingProps = new PropsFile(dest);
                    } catch (IOException ex) {
                        getLog().error("Could not load existing props: " + dest);
                        failures.add(ex);
                    }
                }

                getLog().info((dest.exists() ? "Updating" : "Creating") + " file: " + dest);
                DefaultTranslator translator = new DefaultTranslator(
                    table, index, generatedFields, language, existingProps);
                try {
                    source.write(dest, init(translator));
                } catch (IOException ex) {
                    getLog().error("Unable to write language file: " + dest);
                    failures.add(ex);
                } finally {
                    placeholders += translator.placeholders();
                    errors += translator.errors();
                    retained += translator.retained();
                    if (translator.placeholders() > 0) {
                        getLog().info(String.format("Used %d placeholder(s) for %s.",
                            translator.placeholders(), language));
                    }
                    if (translator.retained() > 0) {
                        getLog().info(String.format("Retained %d string(s) for %s.",
                            translator.retained(), language));
                    }
                }
            }
        }
        if (errors > 0) {
            failures.add(new Exception("Translation content"));
        }
        String placeholdersMessage = String.format("Placeholders used: %d.", placeholders);
        if (placeholders > 0) {
            getLog().warn(placeholdersMessage);
        } else {
            getLog().info(placeholdersMessage);
        }
        if (retained > 0) {
            getLog().warn(String.format("Old strings retained: %d.", retained));
        }
    }
}
