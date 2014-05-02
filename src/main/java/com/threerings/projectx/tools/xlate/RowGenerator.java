package com.threerings.projectx.tools.xlate;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.threerings.projectx.tools.xlate.props.PropsFile;
import com.threerings.projectx.tools.xlate.props.PropsFile.Entry;
import com.threerings.projectx.tools.xlate.spreadsheet.Table;

import static com.threerings.projectx.tools.xlate.Rules.ID;
import static com.threerings.projectx.tools.xlate.Rules.COMMENT;
import static com.threerings.projectx.tools.xlate.Rules.ELSE;

/**
 * Transforms a property from a {@link PropsFile} into a field mapping using some custom project x
 * trickery and logic.
 */
public class RowGenerator
{
    /** Flag indicating sling support tool strings should also be included. This is an input
     * that depends on the deployment (i.e. CJ). */
    public static final int SUPPORT_TOOL = 1;

    public static class Row
    {
        public final Rules.Status status;
        public final FieldMapping fields;

        public Row (Rules.Status status, FieldMapping fields)
        {
            this.status = status;
            this.fields = fields;
        }
    }

    public static RowGenerator get (Domain domain, PropsFile props, boolean admin)
    {
        Map<String, Rules.Scope> scopes = DOMAINS.get(domain);
        if (domain == null) {
            return null;
        }
        String fname = props.getFile().getName();
        Rules.Scope scope = scopes.get(fname);
        if (scope == null) {
            // try removing _en
            String suffix = "_en.properties";
            if (fname.endsWith(suffix)) {
                fname = fname.substring(0, fname.length() - suffix.length()) + ".properties";
                scope = scopes.get(fname);
            }
        }
        if (scope == null) {
            return null;
        }
        return new RowGenerator(domain, scope, props, admin ? SUPPORT_TOOL : 0);
    }

    /**
     * Gets the associated "Scope" field value for a given properties file. It's possible we may
     * some day want to have a properties file map to more than one scope based on rules, but that
     * is not yet suppported.
     */
    public String getScopeName ()
    {
        return _scope.name;
    }

    public Iterable<Row> generate ()
    {
        return Iterables.transform(_props.properties(),
            new Function<Entry, Row>() {
                @Override public Row apply (Entry entry) {
                    return generate(entry);
                }
            });
    }

    /**
     * Using an entry from a properties file, creates a new field map for inserting into a
     * spreadsheet.
     */
    private Row generate (PropsFile.Entry entry)
    {
        Map<Field, String> fields = Maps.newHashMap();
        Rules.Status status = _scope.apply(entry, fields, _context);
        fields.put(Field.LAST_UPDATED, Table.googleNow());
        fields.put(Field.ID, entry.getId());
        String english = entry.getValue();
        if (_domain.isGwt()) {
            english = english.replace("''", "'");
        }
        fields.put(Field.ENGLISH, english);
        fields.put(Field.TECH_NOTES, Rules.makeTechNotes(entry.getValue()));
        return new Row(status, new FieldMapping(fields));
    }

    private RowGenerator (Domain domain, Rules.Scope scope, PropsFile props, int flags)
    {
        _domain = domain;
        _scope = scope;
        _props = props;
        _context = new Rules.Context(GLOBAL_RULES, flags);
    }

    private final Domain _domain;
    private final Rules.Scope _scope;
    private final PropsFile _props;
    private final Rules.Context _context;

    private static final Map<Domain, Map<String, Rules.Scope>> DOMAINS = Maps.newHashMap();
    private static final List<Rules.Rule> GLOBAL_RULES = Lists.newArrayList();

    private static void add (Domain domain, String fileName, String scope, Rules.Rule... rules)
    {
        Map<String, Rules.Scope> scopes = DOMAINS.get(domain);
        if (scopes == null) {
            DOMAINS.put(domain, scopes = Maps.newHashMap());
        }
        scopes.put(fileName, new Rules.Scope(fileName, scope, rules));
    }

    static {
        GLOBAL_RULES.add(ID.equals("msgbundle_class").ignore());

        add(Domain.GAME_CLIENT_OTHER, "arena.properties", "Arena");
        add(Domain.GAME_CLIENT_OTHER, "auction.properties", "Auction House");
        add(Domain.GAME_CLIENT_OTHER, "board.properties", "Adventure Board");
        add(Domain.GAME_CLIENT_CHAT, "chat.properties", "Chat",
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
        add(Domain.GAME_CLIENT_CONVERSATION, "conversation.properties", "Conversation",
            ID.matches(".\\.cash_01.*").ignore(),
            ID.smatches("n.*").thenSet("NPC Name", "Popup title"),
            ID.smatches("r.*").thenSet("Player Response", "One line"),
            ID.smatches("m.*").thenSet("NPC Prompt", "Unlimited"));
        add(Domain.GAME_CLIENT_OTHER, "craft.properties", "Craft",
            ID.smatches("t.*").thenSet("Title"));
        add(Domain.GAME_CLIENT_DUNGEON, "dungeon.properties", "Dungeon",
            COMMENT.equals("RDG TestGate names").ignore(),
            COMMENT.equals("Dev gates").ignore(),
            COMMENT.equals("Custom level testing scene names").ignore(),
            COMMENT.equals("Random level names").and(ID.smatches("*test*")).ignore(),
            COMMENT.equals("Dev names").ignore());
        add(Domain.GAME_CLIENT_EXCHANGE, "exchange.properties", "Exchange");
        add(Domain.GAME_CLIENT_OTHER, "global.properties", "Global",
            COMMENT.equals("Editor translations").ignore());
        add(Domain.GAME_CLIENT_OTHER, "guild.properties", "Guild");
        add(Domain.GAME_CLIENT_HELPTIPS, "helptips.properties", "Help And Tips",
            ID.smatches("i.*").ignore());
        add(Domain.GAME_CLIENT_OTHER, "invite.properties", "Invitations");
        add(Domain.GAME_CLIENT_ITEM, "item.properties", "Item");
        add(Domain.GAME_CLIENT_OTHER, "logon.properties", "Logon Screen");
        add(Domain.GAME_CLIENT_OTHER, "lottery.properties", "Lottery");
        add(Domain.GAME_CLIENT_OTHER, "monster.properties", "Monster");
        add(Domain.GAME_CLIENT_SYSTEM, "projectx.properties", "System",
            ID.smatches("url.*").ignore(),
            COMMENT.smatches("Punctuation Keys*").ignore());
        add(Domain.GAME_CLIENT_OTHER, "pvp.properties", "PvP");
        add(Domain.GAME_CLIENT_OTHER, "redeem.properties", "Redeem");
        add(Domain.GAME_CLIENT_OTHER, "shop.properties", "Shop");
        add(Domain.GAME_CLIENT_OTHER, "social.properties", "Social");
        add(Domain.GAME_CLIENT_OTHER, "status.properties", "Status");
        add(Domain.GAME_CLIENT_OTHER, "steam.properties", "Steam");
        add(Domain.GAME_CLIENT_OTHER, "story.properties.in", "Story");
        add(Domain.GAME_CLIENT_OTHER, "support.properties", "Support");
        add(Domain.GAME_CLIENT_OTHER, "town.properties", "Town");
        add(Domain.GAME_CLIENT_OTHER, "trade.properties", "Trade");
        add(Domain.GAME_CLIENT_OTHER, "building.properties", "Building");
        add(Domain.GAME_CLIENT_OTHER, "design.properties", "Design");
        add(Domain.GAME_CLIENT_UPLINK, "uplink.properties", "Uplink");
        add(Domain.GAME_CLIENT_OTHER, "particles.properties", "Particles");
        add(Domain.GAME_CLIENT_OTHER, "help.properties", "Help");
        add(Domain.GAME_CLIENT_OTHER, "guildhelp.properties", "Guild Help");
        add(Domain.GAME_CLIENT_OTHER, "story.properties.in", "Story");
        add(Domain.GAME_CLIENT_MISSION, "mission.properties", "Mission");
        add(Domain.GAME_CLIENT_OTHER, "additional.properties", "Additional");
        add(Domain.GAME_CLIENT_NAMES, "dungeon-names.properties", "Dungeon");
        add(Domain.GAME_CLIENT_NAMES, "mission-names.properties", "Mission");
        add(Domain.GAME_CLIENT_NAMES, "item-names.properties", "Item");
        add(Domain.GAME_CLIENT_OTHER, "sprites.properties", "Battle Sprites");
        add(Domain.GAME_CLIENT_OTHER, "prologue.properties", "Prologue");

        add(Domain.REGISTER_APP, "projectx_messages.properties", "Spiral Knights Messages",
            ID.matches("header.*").thenSet("Header"),
            ID.matches("loginbridge.*").thenSet("Login Bridge"),
            ID.matches("login.*").thenSet("Login Page"),
            ID.matches("inviteemail.*").thenSet("Invite Email"),
            ID.matches("forgot.*").thenSet("Forgot Password Page"),
            ID.matches("register.*").thenSet("Registration Page"));

        add(Domain.SLING, "ClientMessages.properties", "Client",
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
        add(Domain.SLING, "ServerMessages.properties", "Server",
            COMMENT.smatches("General*").thenSet("Error Messages"),
            COMMENT.smatches("Admin*").andFlagSet(SUPPORT_TOOL).thenSet("Error Messages"),
            ELSE.omit());
        add(Domain.SLING, "UiMessages.properties", "UI",
            COMMENT.equals("General").thenSet("Error Messages"),
            COMMENT.matches("(Date|TimeSpan|TimeRange)Widget").andFlagSet(SUPPORT_TOOL).thenSet("Admin Widgets"),
            ELSE.omit());
        add(Domain.SLING_EVENT, "sling.properties", "Event");

        add(Domain.SUPPORT, "ProjectXMessages.properties", "Support",
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

        add(Domain.SUPPORT, "ProjectXServerMessages.properties", "Support",
            COMMENT.smatches("User app*").thenSet("Server"),
            COMMENT.smatches("Admin app*").andFlagSet(SUPPORT_TOOL).thenSet("Server"),
            ELSE.omit());
    }
}
