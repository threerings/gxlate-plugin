package com.threerings.tools.gxlate;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal to upload new and changed English strings.
 */
@Mojo(name="upload")
public class UploadMojo extends BaseMojo
{
    public void execute ()
        throws MojoExecutionException, MojoFailureException
    {
    }
}
