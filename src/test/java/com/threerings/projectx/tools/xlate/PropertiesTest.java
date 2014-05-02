package com.threerings.projectx.tools.xlate;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.threerings.projectx.tools.xlate.props.PropsFile;
import com.threerings.projectx.tools.xlate.props.Translator;

public class PropertiesTest
{
    public static void main (String[] args)
        throws Exception
    {
        main4(args);
    }

    public static void main4 (String[] args)
        throws Exception
    {
        final Map<File, Integer> counts = Maps.newHashMap();
        for (PropsFile props : AppUtils.loadAllProps(new File(args[0]), false)) {
            counts.put(props.getFile(), Iterables.size(props.properties()));
        }
        List<File> files = Lists.newArrayList(counts.keySet());
        Collections.sort(files, new Comparator<File>() {
            @Override public int compare (File o1, File o2) {
                return counts.get(o1) - counts.get(o2);
            }
        });
        for (File f : files) {
            System.out.println("File " + f.getName() + ": " + counts.get(f));
        }
    }

    public static void main3 (String[] args)
        throws Exception
    {
        File dir = new File(args[0]);
        for (File f : dir.listFiles()) {
            if (!f.isFile()) {
                continue;
            }
            PropsFile props = new PropsFile(f);
            for (Domain dom : Domain.values()) {
                if (!dom.isGameClient()) {
                    continue;
                }
                RowGenerator rows = RowGenerator.get(dom, props, false);
                for (PropsFile.Entry entry : props.properties()) {
                    if (!entry.getValue().equals(entry.getValue().trim())) {
                        System.out.println(entry.getId() + " in " + rows.getScopeName()
                            + " has whitespace at ends");
                    }
                }
            }
        }
    }

    public static void main2 (String[] args)
        throws Exception
    {
        System.out.println(Rules.makeTechNotes("Howdy {0}, my name is {1}"));
    }

    public static void main1 (String[] args)
        throws Exception
    {
        File dir1 = new File(args[0]);
        File dir2 = new File(args[1]);
        for (File f : dir1.listFiles()) {
            if (!f.isFile()) {
                continue;
            }
            PropsFile props = new PropsFile(f);
            props.write(new File(dir2, f.getName()), new Translator() {
                @Override
                public String translate (String id, String source)
                {
                    return source;
                }
            });
        }
    }
}
