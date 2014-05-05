//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import com.threerings.tools.gxlate.props.PropsFile;

/**
 * Goal which reads all configured props files to confirm they are all readable.
 */
@Mojo(name="check-props")
public class CheckPropsMojo extends BaseMojo
{
    @Override
    public void execute ()
        throws MojoExecutionException, MojoFailureException
    {
        List<File> props;
        try {
            props = findAllProps();
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to find props: " + ex.getMessage());
        }

        int errors = 0;
        for (File file : props) {
            errors += check(file);
        }

        if (errors > 0) {
            throw new MojoExecutionException("Errors loading properites files (see log)");
        }

        getLog().info("All props files loaded successfully: " + props.size());
    }

    protected int check (File file)
    {
        try {
            new PropsFile(file);
            return 0;

        } catch (IOException ex) {
            getLog().error("Could not load properties in " + file.getPath(), ex);
            return 1;
        }
    }
}
