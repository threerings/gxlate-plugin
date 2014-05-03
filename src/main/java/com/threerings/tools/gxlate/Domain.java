package com.threerings.tools.gxlate;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.threerings.tools.gxlate.props.PropsFile;
import com.threerings.tools.gxlate.props.PropsFile.Entry;
import com.threerings.tools.gxlate.spreadsheet.Table;

/**
 * Defines the target application or part of the application being translated. Roughly corresponds
 * to a tab in a spreadsheet.
 */
public interface Domain
{
    /**
     * Tests if this domain is destined to be compiled by gwt.
     */
    boolean isGwt ();

    /**
     * A row to be inserted into a spreadsheet.
     */
    public static class Row
    {
        /** Status of the row. */
        public final Rules.Status status;

        /** Mapping of field names to values. */
        public final FieldMapping fields;

        public Row (Rules.Status status, FieldMapping fields)
        {
            this.status = status;
            this.fields = fields;
        }
    }

    /**
     * Defines how to convert a props file into a series of rows, and which spreadsheet the rows
     * should go into.
     */
    public static class RuleSet
    {
        /**
         * Adds global rules, typically used to ignore programmatic property values.
         */
        public void addGlobal (Rules.Rule... rules)
        {
            for (Rules.Rule rule : rules) {
                _globalRules.add(rule);
            }
        }

        /**
         * Adds the rules to be applied for the given values.
         * @param domain the domain that the rules apply to
         * @param fileName the props file that the rules apply to
         * @param scope the tab that the resulting rows should be placed in
         * @param rules describe how to include, exclude and convert properties to rows
         */
        public void add (Domain domain, String fileName, String scope, Rules.Rule... rules)
        {
            Map<String, Rules.Scope> scopes = _domains.get(domain);
            if (scopes == null) {
                _domains.put(domain, scopes = Maps.newHashMap());
            }
            scopes.put(fileName, new Rules.Scope(fileName, scope, rules));
        }

        /**
         * Gets the row generator for a props file and domain.
         * @param domain the targeted domain
         * @param props the source file
         * @param flags custom flags (passed in from tool specification); the usage is determined
         * by the rule set
         */
        public RowGenerator get (Domain domain, PropsFile props, int flags)
        {
            Map<String, Rules.Scope> scopes = _domains.get(domain);
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
            return new RowGenerator(domain, scope, props, flags);
        }

        /**
         * Generates rows from a {@link PropsFile} into a field mapping.
         */
        public class RowGenerator
        {
            /**
             * Gets the associated "Scope" field value for a given properties file. It's possible
             * we may some day want to have a properties file map to more than one scope based on
             * rules, but that is not yet suppported.
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
                _context = new Rules.Context(_globalRules, flags);
            }

            private final Domain _domain;
            private final Rules.Scope _scope;
            private final PropsFile _props;
            private final Rules.Context _context;
        }

        private final Map<Domain, Map<String, Rules.Scope>> _domains = Maps.newHashMap();
        private final List<Rules.Rule> _globalRules = Lists.newArrayList();
    }
}
