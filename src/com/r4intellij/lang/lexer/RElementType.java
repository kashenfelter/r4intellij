/*
 * Copyright 2011 Holger Brandl
 *
 * This code is licensed under BSD. For details see
 * http://www.opensource.org/licenses/bsd-license.php
 */

package com.r4intellij.lang.lexer;

import com.intellij.psi.tree.IElementType;
import com.r4intellij.file.RFileType;
import org.jetbrains.annotations.NotNull;


/**
 * The definition of a R element type.
 *
 * @author Holger Brandl
 */
public class RElementType extends IElementType {

    public RElementType(@NotNull String debugName) {
        super(debugName, RFileType.R_LANGUAGE);
    }

    public String toString() {
        return "[R] " + super.toString();
    }
}
