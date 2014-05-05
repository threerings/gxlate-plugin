//
// Google Translation Plugin - maven plugin facilitating localization using google docs
// Copyright (c) 2014, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/gxlate-plugin/blob/master/LICENSE

package com.threerings.tools.gxlate.spreadsheet;

import java.util.Map;

/**
 * Used in calls to {@link Table#deleteRows()} to make sure a row should be deleted.
 */
public interface DeleteVerifier
{
    /**
     * Confirms that the given row and fields should be deleted.
     */
    boolean confirmDelete (int row, Map<String, String> fields);
}
