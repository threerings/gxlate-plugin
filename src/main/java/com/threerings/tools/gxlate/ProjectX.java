package com.threerings.tools.gxlate;

import static com.threerings.tools.gxlate.Rules.COMMENT;
import static com.threerings.tools.gxlate.Rules.ELSE;
import static com.threerings.tools.gxlate.Rules.ID;

public class ProjectX
{
    /** Flag indicating sling support tool strings should also be included. This is an input
     * that depends on the deployment (i.e. CJ). */
    public static final int SUPPORT_TOOL = 1;

    /** Project X rules. */
    public static final Domain.RuleSet RULES = new Domain.RuleSet();

    /** ProjectX spreadsheet tabs. */
    private enum Tab implements Domain
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

        public final String name;

        @Override public boolean isGwt ()
        {
            return this == SLING || this == SUPPORT;
        }

        Tab (String name)
        {
            this.name = name;
        }
    }

    private static void add (Tab domain, String fileName, String scope, Rules.Rule... rules)
    {
        RULES.add(domain, fileName, scope, rules);
    }

    static {
        RULES.addGlobal(ID.equals("msgbundle_class").ignore());

        add(Tab.GAME_CLIENT_OTHER, "arena.properties", "Arena");
        add(Tab.GAME_CLIENT_OTHER, "auction.properties", "Auction House");
        add(Tab.GAME_CLIENT_OTHER, "board.properties", "Adventure Board");
        add(Tab.GAME_CLIENT_CHAT, "chat.properties", "Chat",
            ID.isInSet("adminCmds", "m.usage_").ignore(),
            COMMENT.equals("Chat commands").thenSet("Command", "Player typed"),
            COMMENT.equals("Privileged chat commands").insertAndIgnore("adminCmds", 2),
            COMMENT.equals("Emote macro commands").thenSet("Emote", "Player typed"),
            ID.smatches("f.*").ignore(),
            COMMENT.equals("Slots").thenSet("Slot"),
            ID.smatches("m.usage*").thenSet("Usage", "Chat box"),
            ID.smatches("*format").thenSet("Format", "Chat box"),
            COMMENT.equals("Feedback messages").thenSet("Feedback", "Chat box"),
            COMMENT.equals("Privileged feedback messages").ignore(),
            COMMENT.equals("Error messages").thenSet("Error", "Chat box"),
            ID.smatches("x.*").thenSet("Special", "N/A"));
        add(Tab.GAME_CLIENT_CONVERSATION, "conversation.properties", "Conversation",
            ID.matches(".\\.cash_01.*").ignore(),
            ID.smatches("n.*").thenSet("NPC Name", "Popup title"),
            ID.smatches("r.*").thenSet("Player Response", "One line"),
            ID.smatches("m.*").thenSet("NPC Prompt", "Unlimited"));
        add(Tab.GAME_CLIENT_OTHER, "craft.properties", "Craft",
            ID.smatches("t.*").thenSet("Title"));
        add(Tab.GAME_CLIENT_DUNGEON, "dungeon.properties", "Dungeon",
            COMMENT.equals("RDG TestGate names").ignore(),
            COMMENT.equals("Dev gates").ignore(),
            COMMENT.equals("Custom level testing scene names").ignore(),
            COMMENT.equals("Random level names").and(ID.smatches("*test*")).ignore(),
            COMMENT.equals("Dev names").ignore());
        add(Tab.GAME_CLIENT_EXCHANGE, "exchange.properties", "Exchange");
        add(Tab.GAME_CLIENT_OTHER, "global.properties", "Global",
            COMMENT.equals("Editor translations").ignore());
        add(Tab.GAME_CLIENT_OTHER, "guild.properties", "Guild");
        add(Tab.GAME_CLIENT_HELPTIPS, "helptips.properties", "Help And Tips",
            ID.smatches("i.*").ignore());
        add(Tab.GAME_CLIENT_OTHER, "invite.properties", "Invitations");
        add(Tab.GAME_CLIENT_ITEM, "item.properties", "Item");
        add(Tab.GAME_CLIENT_OTHER, "logon.properties", "Logon Screen");
        add(Tab.GAME_CLIENT_OTHER, "lottery.properties", "Lottery");
        add(Tab.GAME_CLIENT_OTHER, "monster.properties", "Monster");
        add(Tab.GAME_CLIENT_SYSTEM, "projectx.properties", "System",
            ID.smatches("url.*").ignore(),
            COMMENT.smatches("Punctuation Keys*").ignore());
        add(Tab.GAME_CLIENT_OTHER, "pvp.properties", "PvP");
        add(Tab.GAME_CLIENT_OTHER, "redeem.properties", "Redeem");
        add(Tab.GAME_CLIENT_OTHER, "shop.properties", "Shop");
        add(Tab.GAME_CLIENT_OTHER, "social.properties", "Social");
        add(Tab.GAME_CLIENT_OTHER, "status.properties", "Status");
        add(Tab.GAME_CLIENT_OTHER, "steam.properties", "Steam");
        add(Tab.GAME_CLIENT_OTHER, "story.properties.in", "Story");
        add(Tab.GAME_CLIENT_OTHER, "support.properties", "Support");
        add(Tab.GAME_CLIENT_OTHER, "town.properties", "Town");
        add(Tab.GAME_CLIENT_OTHER, "trade.properties", "Trade");
        add(Tab.GAME_CLIENT_OTHER, "building.properties", "Building");
        add(Tab.GAME_CLIENT_OTHER, "design.properties", "Design");
        add(Tab.GAME_CLIENT_UPLINK, "uplink.properties", "Uplink");
        add(Tab.GAME_CLIENT_OTHER, "particles.properties", "Particles");
        add(Tab.GAME_CLIENT_OTHER, "help.properties", "Help");
        add(Tab.GAME_CLIENT_OTHER, "guildhelp.properties", "Guild Help");
        add(Tab.GAME_CLIENT_OTHER, "story.properties.in", "Story");
        add(Tab.GAME_CLIENT_MISSION, "mission.properties", "Mission");
        add(Tab.GAME_CLIENT_OTHER, "additional.properties", "Additional");
        add(Tab.GAME_CLIENT_NAMES, "dungeon-names.properties", "Dungeon");
        add(Tab.GAME_CLIENT_NAMES, "mission-names.properties", "Mission");
        add(Tab.GAME_CLIENT_NAMES, "item-names.properties", "Item");
        add(Tab.GAME_CLIENT_OTHER, "sprites.properties", "Battle Sprites");
        add(Tab.GAME_CLIENT_OTHER, "prologue.properties", "Prologue");

        add(Tab.REGISTER_APP, "projectx_messages.properties", "Spiral Knights Messages",
            ID.matches("header.*").thenSet("Header"),
            ID.matches("loginbridge.*").thenSet("Login Bridge"),
            ID.matches("login.*").thenSet("Login Page"),
            ID.matches("inviteemail.*").thenSet("Invite Email"),
            ID.matches("forgot.*").thenSet("Forgot Password Page"),
            ID.matches("register.*").thenSet("Registration Page"));

        add(Tab.SLING, "ClientMessages.properties", "Client",
            COMMENT.smatches("General string*").thenSet("General"),
            COMMENT.smatches("General admin*").andFlagSet(SUPPORT_TOOL).thenSet("General"),
            COMMENT.matches("Event Statuses").thenSet("Ticket Status"),
            COMMENT.smatches("FAQPanels*").andFlagSet(SUPPORT_TOOL).thenSet("FAQ"),
            COMMENT.smatches("FAQSection*").andFlagSet(SUPPORT_TOOL).thenSet("FAQ"),
            COMMENT.smatches("LoginBar*").thenSet("Login"),
            COMMENT.smatches("PetitionsPanel*").thenSet("Requests"),
            COMMENT.smatches("SubmitPetitionPanel*").thenSet("Submit Request"),
            COMMENT.smatches("SlingApp*").thenSet("Page"),
            COMMENT.matches("Event Types").andFlagSet(SUPPORT_TOOL).thenSet("Ticket type"),
            COMMENT.smatches("AccountPanel*|AccountsSection*|RelatedAccountsPanel*").
                andFlagSet(SUPPORT_TOOL).thenSet("Accounts"),
            COMMENT.smatches("AdminBar*").andFlagSet(SUPPORT_TOOL).thenSet("Admin Toolbar"),
            COMMENT.smatches("EventPanel*|PostMessagePanel*|EventsSection*|EventsTable*|" +
                "PetitionsPanel*|PostNotePanel*").andFlagSet(SUPPORT_TOOL).thenSet("Tickets"),
            COMMENT.smatches("AdvancedEventSearchPanel*").andFlagSet(SUPPORT_TOOL).
                thenSet("Ticket Search"),
            COMMENT.smatches("ReportsPanel*").andFlagSet(SUPPORT_TOOL).thenSet("Reports"),
            ELSE.omit());
        add(Tab.SLING, "ServerMessages.properties", "Server",
            COMMENT.smatches("General*").thenSet("Error Messages"),
            COMMENT.smatches("Admin*").andFlagSet(SUPPORT_TOOL).thenSet("Error Messages"),
            ELSE.omit());
        add(Tab.SLING, "UiMessages.properties", "UI",
            COMMENT.equals("General").thenSet("Error Messages"),
            COMMENT.matches("(Date|TimeSpan|TimeRange)Widget").andFlagSet(SUPPORT_TOOL).thenSet("Admin Widgets"),
            ELSE.omit());
        add(Tab.SLING_EVENT, "sling.properties", "Event");

        add(Tab.SUPPORT, "ProjectXMessages.properties", "Support",
            COMMENT.smatches("Support Mail*").thenSet("Email"),
            COMMENT.smatches("Account Reclaim*").thenSet("Reclaim"),
            COMMENT.smatches("Petitions*").thenSet("Requests"),
            COMMENT.smatches("User app*").thenSet("Toolbar"),
            COMMENT.equals("Admin app").andFlagSet(SUPPORT_TOOL).thenSet("Admin Toolbar"),
            COMMENT.equals("KnightPanel").andFlagSet(SUPPORT_TOOL).thenSet("Info"),
            COMMENT.equals("ProjectXAccountPanel").andFlagSet(SUPPORT_TOOL).thenSet("Accounts"),
            COMMENT.equals("KnightMailPanel").andFlagSet(SUPPORT_TOOL).thenSet("Mail"),
            COMMENT.equals("KnightMissionsPanel").andFlagSet(SUPPORT_TOOL).thenSet("Missions"),
            COMMENT.equals("SpritesPanel").andFlagSet(SUPPORT_TOOL).thenSet("Sprites"),
            COMMENT.equals("ReservedNamesPanel").andFlagSet(SUPPORT_TOOL).thenSet("Reservations"),
            COMMENT.equals("TransactionPanel").andFlagSet(SUPPORT_TOOL).thenSet("Transactions"),
            COMMENT.equals("Steam DLC Panel").andFlagSet(SUPPORT_TOOL).thenSet("Steam DLC"),
            COMMENT.equals("Coin History Panel").andFlagSet(SUPPORT_TOOL).thenSet("Coin History"),
            COMMENT.equals("Sessions panel").andFlagSet(SUPPORT_TOOL).thenSet("Sessions"),
            ELSE.omit());

        add(Tab.SUPPORT, "ProjectXServerMessages.properties", "Support",
            COMMENT.smatches("User app*").thenSet("Server"),
            COMMENT.smatches("Admin app*").andFlagSet(SUPPORT_TOOL).thenSet("Server"),
            ELSE.omit());
    }
}
