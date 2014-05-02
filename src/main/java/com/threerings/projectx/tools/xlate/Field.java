package com.threerings.projectx.tools.xlate;

/**
 * Enumerates the fields expected in a project x translation spreadsheet. Specifies the column
 * names of those fields and whether or not a change to the field should be saved if the
 * spreadsheet has an existing value.
 */
public enum Field
{
    SCOPE("Context", UploadMode.NONE),
    ID("Id", UploadMode.NONE),
    ENGLISH("EN", UploadMode.NORMAL),
    LAST_UPDATED("ENLastUpdated", UploadMode.SPECIAL),
    TYPE("Type", UploadMode.NONE),
    FIELD_SIZE("FieldSize", UploadMode.SOFT),
    TECH_NOTES("TechNotes", UploadMode.SOFT),
    SEQUENCE_NO("SequenceNo", UploadMode.SPECIAL),
    VERIFY("Verify", UploadMode.NONE, true);

    /** Behaviors for field update when uploading English sources. */
    enum UploadMode
    {
        /** Not affected by uploads. It is manually entered. */
        NONE {
            @Override public boolean needsUpload (String currentValue, String candidateValue) {
                return false;
            }
        },

        /** Updated whenever its source counterpart changes. */
        NORMAL {
            @Override public boolean needsUpload (String currentValue, String candidateValue) {
                currentValue = currentValue == null ? "" : currentValue;
                candidateValue = candidateValue == null ? "" : candidateValue;
                return !currentValue.equals(candidateValue);
            }
        },

        /** Updated if its source counterpart changes and the spreadsheet value is empty. */
        SOFT {
            @Override public boolean needsUpload (String currentValue, String candidateValue) {
                boolean blank = currentValue == null || currentValue.trim().length() == 0;
                return blank && NORMAL.needsUpload(currentValue, candidateValue);
            }
        },

        /** Updated according to custom logic. */
        SPECIAL {
            @Override public boolean needsUpload (String currentValue, String candidateValue) {
                return false; // app logic will ignore this
            }
        };

        public abstract boolean needsUpload (String currentValue, String candidateValue);
    };

    /**
     * Gets the spreadsheet column name associated with this field.
     */
    public String getColumnName ()
    {
        return _columnName;
    }

    /**
     * Gets the language modified column name.
     */
    public String getColumnName (Language language)
    {
        return language.getHeaderStem() + _columnName;
    }

    /**
     * Modifies a value with the language.
     */
    public String modifyValue (String value, Language language)
    {
        return value + "_" + language.getHeaderStem();
    }

    /** Gets the upload mode for this field. */
    public UploadMode getUploadMode ()
    {
        return _uploadMode;
    }

    /** Is this a language columns. */
    public boolean isLanguage ()
    {
        return _language;
    }

    Field (String columnName, UploadMode uploadMode)
    {
        this(columnName, uploadMode, false);
    }

    Field (String columnName, UploadMode uploadMode, boolean language)
    {
        _columnName = columnName;
        _uploadMode = uploadMode;
        _language = language;
    }

    private final String _columnName;
    private final UploadMode _uploadMode;
    private final boolean _language;
}
