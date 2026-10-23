package com.mydrops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.devtools.DevToolsPlugin;

public final class MyDropsLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(MyDropsPlugin.class);
        List<String> arguments = new ArrayList<>(Arrays.asList(args));
        if (!arguments.contains("--developer-mode")) { arguments.add("--developer-mode"); }
        RuneLite.main(arguments.toArray(new String[0]));
        PluginManager pluginManager = RuneLite.getInjector().getInstance(PluginManager.class);
        DevToolsPlugin devTools = pluginManager.getPlugins().stream()
            .filter(DevToolsPlugin.class::isInstance)
            .map(DevToolsPlugin.class::cast)
            .findFirst().orElse(null);
        boolean discovered = devTools != null;
        if (devTools == null)
        {
            try
            {
                devTools = pluginManager.loadPlugins(Collections.<Class<?>>singletonList(DevToolsPlugin.class), null)
                    .stream().filter(DevToolsPlugin.class::isInstance)
                    .map(DevToolsPlugin.class::cast).findFirst().orElse(null);
            }
            catch (Exception ex)
            {
                System.err.println("My Drops dev tools: load failed: " + ex.getClass().getSimpleName());
            }
        }
        System.out.println("My Drops dev tools: assertions=" + RuneLite.class.desiredAssertionStatus()
            + " launcherManaged=" + (RuneLiteProperties.getLauncherVersion() != null)
            + " discovered=" + discovered
            + " loaded=" + (devTools != null)
            + " enabled=" + (devTools != null && pluginManager.isPluginEnabled(devTools))
            + " active=" + (devTools != null && pluginManager.isPluginActive(devTools)));
        if (devTools != null && !pluginManager.isPluginActive(devTools))
        {
            pluginManager.setPluginEnabled(devTools, true);
            DevToolsPlugin selectedDevTools = devTools;
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    pluginManager.startPlugin(selectedDevTools);
                    System.out.println("My Drops dev tools: started=" + pluginManager.isPluginActive(selectedDevTools));
                }
                catch (Exception ex)
                {
                    System.err.println("My Drops dev tools: start failed: " + ex.getClass().getSimpleName());
                }
            });
        }
    }
}
