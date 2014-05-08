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
        for (PropsFile source : loadAllProps()) {
            Table table = doc.loadTable(Bundle.baseName(source.getFile()));
            Index index = new Index(table, Field.ID.getColumnName());
            for (Language language : languages()) {
                Map<Index.Key, Domain.Row> generatedFields = Maps.newHashMap();
                for (Domain.Row row : getFilteredRows(source)) {
                    generatedFields.put(index.key(
                        row.fields.toStringMap(Sets.immutableEnumSet(language))), row);
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

                try {
                    getLog().info((dest.exists() ? "Updating" : "Creating") + " file: " + dest);
                    source.write(dest, init(new DefaultTranslator(
                        table, index, generatedFields, language, existingProps)));
                } catch (IOException ex) {
                    getLog().error("Unable to write language file: " + dest);
                    failures.add(ex);
                }
            }
        }
    }
}
