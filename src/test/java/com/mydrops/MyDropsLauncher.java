package com.mydrops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class MyDropsLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(MyDropsPlugin.class);
        List<String> arguments = new ArrayList<>(Arrays.asList(args));
        if (!arguments.contains("--developer-mode")) { arguments.add("--developer-mode"); }
        RuneLite.main(arguments.toArray(new String[0]));
    }
}
