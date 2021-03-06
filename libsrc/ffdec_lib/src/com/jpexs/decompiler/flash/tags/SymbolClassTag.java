/*
 *  Copyright (C) 2010-2015 JPEXS, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.decompiler.flash.tags;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.SWFInputStream;
import com.jpexs.decompiler.flash.SWFOutputStream;
import com.jpexs.decompiler.flash.tags.base.SymbolClassTypeTag;
import com.jpexs.decompiler.flash.types.BasicType;
import com.jpexs.decompiler.flash.types.annotations.SWFArray;
import com.jpexs.decompiler.flash.types.annotations.SWFType;
import com.jpexs.decompiler.flash.types.annotations.Table;
import com.jpexs.helpers.ByteArrayRange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SymbolClassTag extends SymbolClassTypeTag {

    public static final int ID = 76;

    public static final String NAME = "SymbolClass";

    @SWFType(value = BasicType.UI16)
    @SWFArray(value = "tag", countField = "numSymbols")
    @Table(value = "symbols", itemName = "symbol")
    public List<Integer> tags;

    @SWFArray(value = "name", countField = "numSymbols")
    @Table(value = "symbols", itemName = "symbol")
    public List<String> names;

    /**
     * Constructor
     *
     * @param swf
     */
    public SymbolClassTag(SWF swf) {
        super(swf, ID, NAME, null);
        tags = new ArrayList<>();
        names = new ArrayList<>();
    }

    public SymbolClassTag(SWFInputStream sis, ByteArrayRange data) throws IOException {
        super(sis.getSwf(), ID, NAME, data);
        readData(sis, data, 0, false, false, false);
    }

    @Override
    public final void readData(SWFInputStream sis, ByteArrayRange data, int level, boolean parallel, boolean skipUnusualTags, boolean lazy) throws IOException {
        int numSymbols = sis.readUI16("numSymbols");
        tags = new ArrayList<>(numSymbols);
        names = new ArrayList<>(numSymbols);
        for (int i = 0; i < numSymbols; i++) {
            int tagID = sis.readUI16("tagID");
            String className = sis.readString("className");
            tags.add(tagID);
            names.add(className);
        }
    }

    /**
     * Gets data bytes
     *
     * @param sos SWF output stream
     * @throws java.io.IOException
     */
    @Override
    public void getData(SWFOutputStream sos) throws IOException {
        int numSymbols = tags.size();
        sos.writeUI16(numSymbols);
        for (int i = 0; i < numSymbols; i++) {
            sos.writeUI16(tags.get(i));
            sos.writeString(names.get(i));
        }
    }
}
