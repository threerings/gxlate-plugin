package com.threerings.tools.gxlate;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Goal to download changes to the translated strings.
 */
@Mojo(name="download")
public class DownloadMojo extends BaseMojo
{
    /**
     * Whether rows that are no longer present in the source English files should be removed from
     * the spreadsheet.
     */
    @Parameter(property="gxlate.removeRows", defaultValue="false")
    private boolean _removeRows;

    public void execute ()
        throws MojoExecutionException, MojoFailureException
    {
    }
}
