/*
 * Copyright (C) 2016-2022 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package tv.phantombot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Supplier;
import org.apache.commons.lang3.SystemUtils;

public class ConfigurationManager {

    private static final String BOTLOGIN_TXT_LOCATION = "./config/botlogin.txt";
    private static final String OUAUTH_PREFIX = "oauth:";

    public static final String PROP_ENVOVERRIDE = "ENVOVERRIDE";

    public static final String PROP_BASEPORT = "baseport";
    public static final String PROP_USEHTTPS = "usehttps";
    public static final String PROP_WEBENABLE = "webenable";
    public static final String PROP_MUSICENABLE = "musicenable";
    public static final String PROP_OAUTH = "oauth";
    public static final String PROP_CHANNEL = "channel";
    public static final String PROP_OWNER = "owner";
    public static final String PROP_USER = "user";
    public static final String PROP_DEBUGON = "debugon";
    public static final String PROP_DEBUGLOG = "debuglog";
    public static final String PROP_RELOADSCRIPTS = "reloadscripts";
    public static final String PROP_RHINODEBUGGER = "rhinodebugger";
    public static final String PROP_WEBAUTH = "webauth";
    public static final String PROP_WEBAUTH_RO = "webauthro";
    public static final String PROP_PANEL_USER = "paneluser";
    public static final String PROP_PANEL_PASSWORD = "panelpassword";
    public static final String PROP_YTAUTH = "ytauth";
    public static final String PROP_YTAUTH_RO = "ytauthro";
    public static final String PROP_API_OAUTH = "apioauth";
    public static final String PROP_SILENTSCRIPTSLOAD = "silentscriptsload";
    public static final String PROP_USEROLLBAR = "userollbar";

    private ConfigurationManager() {
        // private constructor to prevent users from instantiating a pure static class
    }

    static CaselessProperties getConfiguration() {
        /* Properties configuration */
        CaselessProperties startProperties = CaselessProperties.instance();
        startProperties.clear();

        /* Indicates that the botlogin.txt file should be overwritten/created. */
        boolean changed = false;

        // Indicates that this is a fresh setup
        boolean newSetup = false;

        /* Load up the bot info from the bot login file */
        try {
            if (new File(BOTLOGIN_TXT_LOCATION).exists()) {
                try ( FileInputStream inputStream = new FileInputStream(BOTLOGIN_TXT_LOCATION)) {
                    startProperties.load(inputStream);
                }
            }
        } catch (IOException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
        }

        /* Load up the bot info from the environment */
        String prefix = "PHANTOMBOT_";
        boolean envOverrides = System.getenv().containsKey(prefix + PROP_ENVOVERRIDE)
                && (System.getenv(prefix + PROP_ENVOVERRIDE).equalsIgnoreCase("true") || System.getenv(prefix + PROP_ENVOVERRIDE).equals("1"));
        System.getenv().entrySet().forEach((v) -> {
            String key = v.getKey().toUpperCase();
            String value = v.getValue();
            if (key.startsWith(prefix) && prefix.length() < key.length() && !key.equals(prefix + PROP_ENVOVERRIDE)) {
                key = key.substring(prefix.length()).toLowerCase();
                if (envOverrides) {
                    startProperties.setProperty(key, value);
                } else {
                    startProperties.putIfAbsent(key, value);
                }
            }
        });

        changed |= generateDefaultValues(startProperties);

        /* Make a new botlogin with the botName, oauth or channel is not found */
        if (doSetup(startProperties, startProperties.getProperty(PROP_CHANNEL, "").isBlank(),
                startProperties.getProperty(PROP_OAUTH, "").isBlank())) {
            newSetup = true;
        }

        changed |= correctCommonErrors(startProperties);

        /*
         * Iterate the properties and delete entries for anything that does not have a
         * value.
         */
        changed = startProperties.stringPropertyNames().stream().map((propertyKey) -> startProperties.remove(propertyKey, "")).reduce(changed, (accumulator, _item) -> accumulator | _item);

        if (!startProperties.getPropertyAsBoolean("allownonascii", false)) {
            for (String propertyKey : startProperties.stringPropertyNames()) {
                String olds = startProperties.getProperty(propertyKey);
                String news = olds.codePoints().filter(x -> x >= 32 || x <= 126).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();

                if (!olds.equals(news)) {
                    startProperties.setProperty(propertyKey, news);
                    changed = true;
                }
            }
        }

        /* Check to see if anything changed */
        if (changed) {
            startProperties.store(false);
        }

        // fresh setup indicator should not be saved
        startProperties.setProperty("newSetup", newSetup ? "true" : "false");

        return startProperties;
    }

    private static boolean generateDefaultValues(CaselessProperties startProperties) {
        boolean changed = false;

        changed |= setDefaultIfMissing(startProperties, PROP_BASEPORT, "25000", "Set default baseport");
        changed |= setDefaultIfMissing(startProperties, PROP_USEHTTPS, "true", "Set default usehttps");
        changed |= setDefaultIfMissing(startProperties, PROP_WEBENABLE, "true", "Set default webenable");
        changed |= setDefaultIfMissing(startProperties, PROP_MUSICENABLE, "true", "Set default musicenable");

        changed |= setDefaultIfMissing(startProperties, PROP_USEROLLBAR, "true", "Enabled Rollbar");

        /* Check to see if there's a webOauth set */
        changed |= setDefaultIfMissing(startProperties, PROP_WEBAUTH, ConfigurationManager::generateWebAuth, "New webauth key has been generated for " + BOTLOGIN_TXT_LOCATION);
        /* Check to see if there's a webOAuthRO set */
        changed |= setDefaultIfMissing(startProperties, PROP_WEBAUTH_RO, ConfigurationManager::generateWebAuth, "New webauth read-only key has been generated for " + BOTLOGIN_TXT_LOCATION);
        /* Check to see if there's a youtubeOAuth set */
        changed |= setDefaultIfMissing(startProperties, PROP_YTAUTH, ConfigurationManager::generateWebAuth, "New YouTube websocket key has been generated for " + BOTLOGIN_TXT_LOCATION);
        /* Check to see if there's a youtubeOAuthThro set */
        changed |= setDefaultIfMissing(startProperties, PROP_YTAUTH_RO, ConfigurationManager::generateWebAuth, "New YouTube read-only websocket key has been generated for " + BOTLOGIN_TXT_LOCATION);
        return changed;
    }

    private static boolean correctCommonErrors(CaselessProperties startProperties) {
        boolean changed = false;

        /* Make sure the oauth has been set correctly */
        if (startProperties.getProperty(PROP_OAUTH) != null && !startProperties.getProperty(PROP_OAUTH).startsWith(OUAUTH_PREFIX) && !startProperties.getProperty(PROP_OAUTH).isEmpty()) {
            startProperties.setProperty(PROP_OAUTH, OUAUTH_PREFIX + startProperties.getProperty(PROP_OAUTH));
            changed = true;
        }

        /* Make sure the apiOAuth has been set correctly */
        if (startProperties.getProperty(PROP_API_OAUTH) != null && !startProperties.getProperty(PROP_API_OAUTH).startsWith(OUAUTH_PREFIX) && !startProperties.getProperty(PROP_API_OAUTH).isEmpty()) {
            startProperties.setProperty(PROP_API_OAUTH, OUAUTH_PREFIX + startProperties.getProperty(PROP_API_OAUTH));
            changed = true;
        }

        /* Make sure the channelName does not have a # */
        if (startProperties.getProperty(PROP_CHANNEL).startsWith("#")) {
            startProperties.setProperty(PROP_CHANNEL, startProperties.getProperty(PROP_CHANNEL).substring(1));
            changed = true;
        } else if (startProperties.getProperty(PROP_CHANNEL).contains(".tv")) {
            startProperties.setProperty(PROP_CHANNEL, startProperties.getProperty(PROP_CHANNEL).substring(startProperties.getProperty(PROP_CHANNEL).indexOf(".tv/") + 4).replaceAll("/", ""));
            changed = true;
        }

        /* Check for the owner after the channel check is done. */
        if (startProperties.getProperty(PROP_OWNER) == null && startProperties.getProperty(PROP_CHANNEL) != null && !startProperties.getProperty(PROP_CHANNEL).isEmpty()) {
            startProperties.setProperty(PROP_OWNER, startProperties.getProperty(PROP_CHANNEL));
            changed = true;
        }
        return changed;
    }

    /**
     * Sets a default value to a properties object if the requested property does not exist
     *
     * @param properties the properties object to be modified
     * @param propertyName the name of the property, which should be set if null
     * @param defaultValue the default value, to which the property is set, if the property is missing in the properties object
     * @param setMessage the message which will be printed if the value is set to the given default value
     * @return if the value is already present in the properties object
     */
    private static boolean setDefaultIfMissing(CaselessProperties properties, String propertyName, String defaultValue, String generatedMessage) {
        return setDefaultIfMissing(properties, propertyName, () -> defaultValue, generatedMessage);
    }

    /**
     * Sets a default value to a properties object if the requested property does not exist
     *
     * @param properties the properties object to be modified
     * @param propertyName the name of the property, which should be generated if null
     * @param defaultValueGenerator the generating function, which generates the default value, if the property is missing in the properties object
     * @param generatedMessage the message which will be printed if the value is generated
     * @return if the value is already present in the properties object and does not have to be generated
     */
    private static boolean setDefaultIfMissing(CaselessProperties properties, String propertyName, Supplier<String> defaultValueGenerator, String generatedMessage) {
        boolean changed = false;
        if (properties.getProperty(propertyName) == null) {
            properties.setProperty(propertyName, defaultValueGenerator.get());
            com.gmt2001.Console.debug.println(generatedMessage);
            changed = true;
        }
        return changed;
    }

    /**
     * Gets a boolean value from the a properties object and prints a message according to the property name.
     *
     * @param properties the Properties object to get the boolean value from
     * @param propertyName the name of the property to get
     * @param defaulValue the default value of the property
     * @return the value of the property. If parsing the value to a boolean fails, the default value is returned.
     */
    public static boolean getBoolean(CaselessProperties properties, String propertyName, boolean defaulValue) {
        return properties.getPropertyAsBoolean(propertyName, defaulValue);
    }

    private static boolean doSetup(CaselessProperties startProperties, boolean channel, boolean oauth) {
        if (!channel && !oauth) {
            return false;
        }

        com.gmt2001.Console.out.print("\r\n");
        com.gmt2001.Console.out.print("Welcome to the PhantomBot setup process!\r\n");
        com.gmt2001.Console.out.print("If you have any issues please join our Discord!\r\n");
        com.gmt2001.Console.out.print("Discord: https://discord.gg/YKvMd78\r\n");
        com.gmt2001.Console.out.print("\r\n");

        if (RepoVersion.isDocker()) {
            com.gmt2001.Console.out.print("PhantomBot has detected that your device is running Docker.\r\n");
            com.gmt2001.Console.out.print("We recommend using our official docker-compose.yml to setup\r\n");
            com.gmt2001.Console.out.print("the container: https://github.com/PhantomBot/PhantomBot/blob/master/docker-compose.yml\r\n");
            com.gmt2001.Console.out.print("\r\n");
            com.gmt2001.Console.out.print("If you want your PhantomBot Docker container to update automatically,\r\n");
            com.gmt2001.Console.out.print("we recommend using ouroboros: https://github.com/gmt2001/ouroboros\r\n");
        } else if (SystemUtils.IS_OS_WINDOWS) {
            com.gmt2001.Console.out.print("PhantomBot has detected that your device is running Windows.\r\n");
            com.gmt2001.Console.out.print("Here's the setup guide for Windows: https://phantombot.dev/guides/#guide=content/setupbot/windows");
        } else if (SystemUtils.IS_OS_LINUX) {
            com.gmt2001.Console.out.print("PhantomBot has detected that your device is running Linux.\r\n");
            com.gmt2001.Console.out.print("Here's the setup guide for Ubuntu: https://phantombot.dev/guides/#guide=content/setupbot/ubuntu\r\n");
            com.gmt2001.Console.out.print("Here's the setup guide for CentOS: https://phantombot.dev/guides/#guide=content/setupbot/centos");
        } else if (SystemUtils.IS_OS_MAC) {
            com.gmt2001.Console.out.print("PhantomBot has detected that your device is running macOS.\r\n");
            com.gmt2001.Console.out.print("Here's the setup guide for macOS: https://phantombot.dev/guides/#guide=content/setupbot/macos");
        } else if (SystemUtils.IS_OS_FREE_BSD || SystemUtils.IS_OS_NET_BSD || SystemUtils.IS_OS_OPEN_BSD) {
            com.gmt2001.Console.out.print("PhantomBot has detected that your device is running BSD.\r\n");
            com.gmt2001.Console.out.print("Sorry, we do not have any setup guides for this OS.\r\n");
        } else if (SystemUtils.IS_OS_UNIX) {
            com.gmt2001.Console.out.print("PhantomBot has detected that your device is running Unix.\r\n");
            com.gmt2001.Console.out.print("Sorry, we do not have any setup guides for this OS.\r\n");
        } else {
            com.gmt2001.Console.out.print("PhantomBot is not able to detect your OS type.\r\n");
            com.gmt2001.Console.out.print("Sorry, we do not have any setup guides for this OS.\r\n");
        }

        com.gmt2001.Console.out.print("\r\n\r\n\r\n");

        if (channel) {
            com.gmt2001.Console.out.print("The channel to join is not setup, please perform first run\r\n");
            com.gmt2001.Console.out.print("setup from the bots webserver.\r\n");
        }

        if (oauth) {
            com.gmt2001.Console.out.print("The oauth token is not setup, please perform first run\r\n");
            com.gmt2001.Console.out.print("setup from the bots webserver.\r\n");
        }

        com.gmt2001.Console.out.print("\r\n");
        com.gmt2001.Console.out.print("\r\n");

        return true;
    }

    /* gen a oauth */
    private static String generateWebAuth() {
        return PhantomBot.generateRandomString(30);
    }
}
