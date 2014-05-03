package com.threerings.tools.gxlate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.threerings.tools.gxlate.props.PropsFile;
import com.threerings.tools.gxlate.props.PropsFile.Entry;

public class Rules
{
    /** Supplies the comment of the property (nearest chunk of #... text above it) as input
     * to a condition. */
    public static final Input COMMENT = new Input() {
        @Override
        public String get (PropsFile.Entry entry) {
            return entry.getComment();
        }
    };

    /** Supplies the id of the property as input to a condition. */
    public static final Input ID = new Input() {
        @Override
        public String get (PropsFile.Entry entry) {
            return entry.getId();
        }
    };

    /** Catch all conditional. */
    public static final Condition ELSE = new Condition () {
        @Override
        public boolean test (Entry entry, Context context) {
            return true;
        }
    };

    /**
     * Generates some vanilla text suitable for a "technical notes" column, if the string uses
     * escapes.
     * TODO: goolate need better abstraction?
     * @return empty string if the string has no escapes
     */
    public static String makeTechNotes (String english)
    {
        
        if (!english.contains("{")) {
            return "";
        }

        StringBuilder bldr = new StringBuilder(
            "The translation should use the following escape sequences:\n");
        for (int pos = 0;; ) {
            int open = english.indexOf('{', pos);
            if (open == -1) {
                break;
            }
            pos = english.indexOf('}', open) + 1;
            bldr.append(english.substring(open, pos)).append(": <Fill in>\n");
        }
        return bldr.toString();
    }

    /**
     * Possible results of applying rules to a property.
     */
    public enum Status
    {
        /** Generate a row for the property. */
        NORMAL,

        /** Always use the English version, copy it directly from the source. */
        IGNORE,

        /** Not localized (e.g. admin tools). */
        OMIT
    }

    /**
     * A unique source (English) property and the rules that apply to it. Not mutable.
     */
    public static class Scope
    {
        /** Filename, relative to the source root. */
        public final String fileName;

        /** Property name. */
        public final String name;

        /**
         * Creates a new Scope for the given property and with the given rules.
         */
        public Scope (String fileName, String name, Rule... rules)
        {
            this.fileName = fileName;
            this.name = name;
            _rules = rules;
        }

        /**
         * Applies the rules to the supplied property and generates its columns.
         * @param entry the source property from the loaded file
         * @param fields the columns to assign to, using rules
         * @param context information shared across rules, i.e. the application
         * @return whether and how to output the property
         */
        public Status apply (PropsFile.Entry entry, Map<Field, String> fields, Context context)
        {
            fields.put(Field.TYPE, "General");
            fields.put(Field.SCOPE, name);
            for (Rule rule : Iterables.concat(context.globalRules, Arrays.asList(_rules))) {
                Status status = rule.apply(entry, fields, context);
                if (status != null) {
                    return status;
                }
            }
            return null;
        }

        private final Rule[] _rules;
    }

    /**
     * Stuff the rules need to know from the app.
     */
    public static class Context
    {
        /**
         * Fires up a new context, using the application rules and flags.
         * @param globalRules
         * @param flags
         */
        public Context (List<Rule> globalRules, int flags)
        {
            this.globalRules = globalRules;
            this.flags = flags;
        }

        public Set<String> getNamedSet (String name)
        {
            Set<String> set = sets.get(name);
            if (set == null) {
                sets.put(name, set = Sets.newHashSet());
            }
            return set;
        }

        public boolean isFlagSet (int flag)
        {
            return (flags & flag) != 0;
        }

        private final Map<String, Set<String>> sets = Maps.newHashMap();
        private final List<Rule> globalRules;
        private final int flags;
    }

    /** An input to a rule. */
    public static abstract class Input
    {
        public abstract String get (PropsFile.Entry entry);

        /** Creates a condition that matches the input against a regex. */
        public PatternCondition matches (String expression)
        {
            PatternCondition pr = new PatternCondition();
            pr.input = this;
            pr.pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
            return pr;
        }

        /**
         * Creates a condition that matches the input against a "special" wildcard-type
         * expression: dots are not treated specially and the asterisk is short for ".*"
         */
        public PatternCondition smatches (String expression)
        {
            return matches(expression.replace(".", "\\.").replace("*", ".*"));
        }

        /** Creates a condition that exactly matches a string. */
        public PatternCondition equals (String expression)
        {
            return matches("^" + Pattern.quote(expression) + "$");
        }

        /** Creates a new condition that will match the input if it starts with the given prefix
         * and the substring after the prefix is in the given named set. */
        public IsInSetCondition isInSet (String setName, String prefix)
        {
            IsInSetCondition cond = new IsInSetCondition();
            cond.input = this;
            cond.prefix = prefix;
            cond.setName = setName;
            return cond;
        }
    }

    /** A condition for applying a rule. */
    public static abstract class Condition
    {
        abstract public boolean test (PropsFile.Entry entry, Context context);

        /**
         * Creates a rule based on this condition that will set the type column for the new row.
         */
        public Rule thenSet (String type)
        {
            return new Rule(this, new FieldAction(ImmutableMap.of(Field.TYPE, type)));
        }

        /**
         * Creates a rule based on this condition that will set the type and field size columns
         * for the new row.
         */
        public Rule thenSet (String type, String fieldSize)
        {
            return new Rule(this, new FieldAction(ImmutableMap.of(
                Field.FIELD_SIZE, fieldSize, Field.TYPE, type)));
        }

        /**
         * Creates a rule that will cause the current key to be ignored if this condition is met.
         * Ignored translations will be present but with the same value as the English.
         */
        public Rule ignore ()
        {
            return new Rule(this, new Action() {
                @Override public Status execute (Entry entry, Map<Field, String> fields,
                    Context context) {
                    return Status.IGNORE;
                }
            });
        }

        /**
         * Creates a rule that will cause the current key to be skipped. Skipped translations will
         * not appear in the language file.
         */
        public Rule omit ()
        {
            return new Rule(this, new Action() {
                @Override public Status execute (Entry entry, Map<Field, String> fields,
                    Context context) {
                    return Status.OMIT;
                }
            });
        }

        /**
         * Creates a rule that will, if this condition is met, insert the substring of the input
         * starting at the given offset into the given named set.
         */
        public Rule insertAndIgnore (String set, int offset)
        {
            final Action[] actions = {
                new InsertAction(set, offset),
                ignore().action
            };
            return new Rule(this, new Action() {
                @Override
                public Status execute (Entry entry, Map<Field, String> fields, Context context)
                {
                    Status result = null;
                    for (Action action : actions) {
                        result = action.execute(entry, fields, context);
                    }
                    return result;
                }
            });
        }

        public Condition and (Condition other)
        {
            final Condition c1 = this, c2 = other;
            return new Condition() {
                @Override public boolean test (Entry entry, Context context)
                {
                    return c1.test(entry, context) && c2.test(entry, context);
                }
            };
        }

        public Condition andFlagSet (final int flag)
        {
            return and(new Condition() {
                @Override public boolean test (Entry entry, Context context)
                {
                    return context.isFlagSet(flag);
                }
            });
        }
    }

    /** A rule is just a condition and an action. */
    public static final class Rule
    {
        public Rule (Condition condition, Action action)
        {
            this.condition = condition;
            this.action = action;
        }

        public Status apply (PropsFile.Entry entry, Map<Field, String> fields, Context context)
        {
            return condition.test(entry, context)
                ? action.execute(entry, fields, context)
                : null;
        }

        private Condition condition;
        private Action action;
    }

    /** Condition to test if an input matches a pattern. */
    public static class PatternCondition extends Condition
    {
        @Override
        public boolean test (Entry entry, Context context)
        {
            return pattern.matcher(input.get(entry)).matches();
        }

        private Input input;
        private Pattern pattern;
    }

    /** Condition to test if a substring of an input is in a set. */
    public static class IsInSetCondition extends Condition
    {
        @Override public boolean test (Entry entry, Context context)
        {
            String val = input.get(entry);
            return val.startsWith(prefix) &&
                context.getNamedSet(setName).contains(val.substring(prefix.length()));
        }

        private Input input;
        private String setName;
        private String prefix;
    }

    public static abstract class Action
    {
        abstract public Status execute (PropsFile.Entry entry, Map<Field, String> fields,
            Context context);
    }

    /** An action that copies some fields into the result. */
    public static class FieldAction extends Action
    {
        public FieldAction (Map<Field, String> fields)
        {
            this.fields = fields;
        }

        @Override
        public Status execute (Entry entry, Map<Field, String> fields, Context context)
        {
            fields.putAll(this.fields);
            return Status.NORMAL;
        }

        private final Map<Field, String> fields;
    }

    /** An action that inserts a substring of the entry's ID into a named set. */
    public static class InsertAction extends Action
    {
        public InsertAction (String setName, int offset)
        {
            this.setName = setName;
            this.offset = offset;
        }

        @Override
        public Status execute (Entry entry, Map<Field, String> fields, Context context)
        {
            context.getNamedSet(setName).add(ID.get(entry).substring(offset));
            return Status.NORMAL;
        }

        final String setName;
        final int offset;
    }
}
