/*
 * Copyright 2011 Holger Brandl
 *
 * This code is licensed under BSD. For details see
 * http://www.opensource.org/licenses/bsd-license.php
 */

package com.r4intellij.actions;

/**
 * Event handler for the "Run Selection" action within an Arc code editor - runs the currently selected text within the current REPL.
 */
public class HeadNrowEvalAction extends AbstactEvalTextAction {

    @Override
    protected String getEvalCmd(String selectedText) {
        return "head(" + selectedText + "); nrow(" + selectedText + ");";
    }
}
