package com.threerings.tools.gxlate;

/**
 * Enumerates the project x entities that need translation.
 */
public enum Domain
{
    GAME_CLIENT_EXCHANGE("GC.exchange"),
    GAME_CLIENT_UPLINK("GC.uplink"),
    GAME_CLIENT_CONVERSATION("GC.conversation"),
    GAME_CLIENT_HELPTIPS("GC.helptips"),
    GAME_CLIENT_DUNGEON("GC.dungeon"),
    GAME_CLIENT_CHAT("GC.chat"),
    GAME_CLIENT_SYSTEM("GC.system"),
    GAME_CLIENT_ITEM("GC.item"),
    GAME_CLIENT_MISSION("GC.mission"),
    GAME_CLIENT_NAMES("GC.names"),
    GAME_CLIENT_OTHER("GC.other"),
    REGISTER_APP("Register"),
    SLING("Sling"),
    SLING_EVENT("Sling Event"),
    SUPPORT("Support");

    public final String defaultSheetName;

    public boolean isGameClient ()
    {
        return name().startsWith("GAME_CLIENT_");
    }

    /**
     * Tests if this domain is destined to be compiled by gwt.
     */
    public final boolean isGwt ()
    {
        return this == SLING || this == SUPPORT;
    }

    Domain (String defaultSheetName)
    {
        this.defaultSheetName = defaultSheetName;
    }
}
