/*
 * Copyright (C) 2014 JPEXS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpexs.decompiler.flash.abc.avm2.parser.script;

import com.jpexs.decompiler.flash.SourceGeneratorLocalData;
import com.jpexs.decompiler.flash.abc.ABC;
import com.jpexs.decompiler.flash.abc.avm2.instructions.AVM2Instruction;
import com.jpexs.decompiler.flash.abc.avm2.instructions.localregs.GetLocalIns;
import com.jpexs.decompiler.flash.abc.avm2.instructions.localregs.KillIns;
import com.jpexs.decompiler.flash.abc.avm2.instructions.localregs.SetLocalIns;
import com.jpexs.decompiler.flash.abc.avm2.instructions.other.FindPropertyStrictIns;
import com.jpexs.decompiler.flash.abc.avm2.instructions.other.GetPropertyIns;
import com.jpexs.decompiler.flash.abc.avm2.instructions.other.SetPropertyIns;
import com.jpexs.decompiler.flash.abc.avm2.instructions.stack.DupIns;
import com.jpexs.decompiler.flash.abc.avm2.instructions.stack.PopIns;
import com.jpexs.decompiler.flash.abc.avm2.model.AVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.CoerceAVM2Item;
import com.jpexs.decompiler.flash.abc.types.InstanceInfo;
import com.jpexs.decompiler.flash.abc.types.Multiname;
import com.jpexs.decompiler.flash.abc.types.Namespace;
import com.jpexs.decompiler.flash.abc.types.NamespaceSet;
import com.jpexs.decompiler.flash.abc.types.ScriptInfo;
import com.jpexs.decompiler.flash.abc.types.traits.Trait;
import com.jpexs.decompiler.flash.abc.types.traits.TraitFunction;
import com.jpexs.decompiler.flash.abc.types.traits.TraitMethodGetterSetter;
import com.jpexs.decompiler.flash.abc.types.traits.TraitSlotConst;
import com.jpexs.decompiler.flash.helpers.GraphTextWriter;
import com.jpexs.decompiler.graph.GraphSourceItem;
import com.jpexs.decompiler.graph.GraphTargetItem;
import com.jpexs.decompiler.graph.SourceGenerator;
import com.jpexs.decompiler.graph.TypeFunctionItem;
import com.jpexs.decompiler.graph.TypeItem;
import com.jpexs.decompiler.graph.model.LocalData;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author JPEXS
 */
public class PropertyAVM2Item extends AVM2Item {

    public String propertyName;
    public GraphTargetItem object;
    public List<ABC> abcs;
    public GraphTargetItem index;
    private GraphTargetItem storeValue;
    private List<String> openedNamespaces;
    private List<Integer> openedNamespacesKind;

    public PropertyAVM2Item(GraphTargetItem object, String propertyName, GraphTargetItem index, List<ABC> abcs, List<String> openedNamespaces, List<Integer> openedNamespacesKind) {
        super(null, PRECEDENCE_PRIMARY);
        this.propertyName = propertyName;
        this.object = object;
        this.abcs = abcs;
        this.index = index;
        this.openedNamespaces = openedNamespaces;
        this.openedNamespacesKind = openedNamespacesKind;
    }

    public void setStoreValue(GraphTargetItem storeValue) {
        this.storeValue = storeValue;
    }

    @Override
    public GraphTextWriter appendTo(GraphTextWriter writer, LocalData localData) throws InterruptedException {
        return writer;
    }

    private int allNsSet() {
        int nssa[] = new int[openedNamespaces.size()];
        for (int i = 0; i < openedNamespaces.size(); i++) {
            nssa[i] = (abcs.get(0).constants.getNamespaceId(new Namespace(openedNamespacesKind.get(i), abcs.get(0).constants.getStringId(openedNamespaces.get(i), true)), 0, true));
        }
        return abcs.get(0).constants.getNamespaceSetId(new NamespaceSet(nssa), true);
    }

    private String resolveObjectType() {
        String objType = object == null ? null : object.returnType().toString();
        if (objType == null) {
            loopo:
            for (int i = 0; i < openedNamespaces.size(); i++) {
                String ns = openedNamespaces.get(i);
                String nspkg = ns;
                String nsclass = null;
                int nsKind = openedNamespacesKind.get(i);
                if (nspkg.contains(":") && nsKind != Namespace.KIND_NAMESPACE) {
                    nsclass = nspkg.substring(nspkg.indexOf(":") + 1);
                    nspkg = nspkg.substring(0, nspkg.indexOf(":"));
                }
                loopabc:
                for (ABC a : abcs) {
                    for (int h = 0; h < a.instance_info.size(); h++) {
                        InstanceInfo ii = a.instance_info.get(h);
                        Multiname n = a.constants.constant_multiname.get(ii.name_index);
                        if (n.getNamespace(a.constants).getName(a.constants).equals(nspkg) && (nsclass == null || (n.getName(a.constants, new ArrayList<String>()).equals(nsclass)))) {
                            Reference<String> outName = new Reference<>("");
                            Reference<String> outNs = new Reference<>("");
                            Reference<String> outPropNs = new Reference<>("");
                            Reference<Integer> outPropNsKind = new Reference<>(1);
                            if (AVM2SourceGenerator.searchProperty(abcs, nspkg, n.getName(a.constants, new ArrayList<String>()), propertyName, outName, outNs, outPropNs, outPropNsKind)) {
                                objType = "".equals(outNs.getVal()) ? outName.getVal() : outNs.getVal() + "." + outName.getVal();
                                break loopo;
                            }
                        }
                    }
                }
            }
        }
        if (objType == null) {
            throw new RuntimeException("Unresolved object type");
        }
        return objType;
    }

    public GraphTargetItem resolvePropertyType() {
        if (index != null) {
            return TypeItem.UNBOUNDED;
        }

        String objType = resolveObjectType();
        for (ABC a : abcs) {
            int ci = a.findClassByName(objType);
            if (ci != -1) {
                for (Trait t : a.instance_info.get(ci).instance_traits.traits) {
                    String tnames = t.getName(a).getName(a.constants, new ArrayList<String>());
                    if (tnames.equals(propertyName)) {
                        if (t instanceof TraitSlotConst) {
                            TraitSlotConst tsc = (TraitSlotConst) t;
                            if (tsc.type_index == 0) {
                                return TypeItem.UNBOUNDED;
                            }
                            return new TypeItem(a.constants.constant_multiname.get(tsc.type_index).getNameWithNamespace(a.constants));
                        }
                        if (t instanceof TraitMethodGetterSetter) {
                            TraitMethodGetterSetter tmgs = (TraitMethodGetterSetter) t;
                            if (tmgs.kindType == Trait.TRAIT_GETTER) {
                                return new TypeItem(a.constants.constant_multiname.get(a.method_info.get(tmgs.method_info).ret_type).getNameWithNamespace(a.constants));
                            }
                            if (tmgs.kindType == Trait.TRAIT_SETTER) {
                                return new TypeItem(a.constants.constant_multiname.get(a.method_info.get(tmgs.method_info).param_types[0]).getNameWithNamespace(a.constants));
                            }
                        }
                        if (t instanceof TraitFunction) {
                            return new TypeItem("Function");
                        }
                        return TypeItem.UNBOUNDED;
                    }
                }
                break;
            }
        }
        return TypeItem.UNBOUNDED;
    }

    public int resolveProperty() {
        if (index != null) {
            return abcs.get(0).constants.getMultinameId(new Multiname(Multiname.MULTINAMEL,
                    abcs.get(0).constants.getStringId(propertyName, true), 0,
                    allNsSet(), 0, new ArrayList<Integer>()), true);
        }
        
        String objType = resolveObjectType();
        for (ABC a : abcs) {
            int ci = a.findClassByName(objType);
            if (ci != -1) {
                for (Trait t : a.instance_info.get(ci).instance_traits.traits) {
                    Multiname tname = t.getName(a);
                    String tnames = t.getName(a).getName(a.constants, new ArrayList<String>());
                    if (tnames.equals(propertyName)) {
                        return abcs.get(0).constants.getMultinameId(new Multiname(tname.kind,
                                abcs.get(0).constants.getStringId(tnames, true),
                                abcs.get(0).constants.getNamespaceId(new Namespace(tname.getNamespace(a.constants).kind, abcs.get(0).constants.getStringId(tname.getNamespace(a.constants).getName(a.constants), true)), 0, true), 0, 0, new ArrayList<Integer>()), true);
                    }
                }
                for (Trait t : a.class_info.get(ci).static_traits.traits) {
                    Multiname tname = t.getName(a);
                    String tnames = t.getName(a).getName(a.constants, new ArrayList<String>());
                    if (tnames.equals(propertyName)) {
                        return abcs.get(0).constants.getMultinameId(new Multiname(tname.kind,
                                abcs.get(0).constants.getStringId(tnames, true),
                                abcs.get(0).constants.getNamespaceId(new Namespace(tname.getNamespace(a.constants).kind, abcs.get(0).constants.getStringId(tname.getNamespace(a.constants).getName(a.constants), true)), 0, true), 0, 0, new ArrayList<Integer>()), true);
                    }
                }
                break;
            }
        }
        
        for (ABC a : abcs) {
            for(ScriptInfo si:a.script_info){
                for (Trait t : si.traits.traits) {
                    Multiname tname = t.getName(a);
                    String tnames = t.getName(a).getName(a.constants, new ArrayList<String>());                    
                    if (tnames.equals(propertyName)) {
                        return abcs.get(0).constants.getMultinameId(new Multiname(tname.kind,
                                abcs.get(0).constants.getStringId(tnames, true),
                                abcs.get(0).constants.getNamespaceId(new Namespace(tname.getNamespace(a.constants).kind, abcs.get(0).constants.getStringId(tname.getNamespace(a.constants).getName(a.constants), true)), 0, true), 0, 0, new ArrayList<Integer>()), true);
                    }
                }
            }
        }
        
        return abcs.get(0).constants.getMultinameId(new Multiname(Multiname.MULTINAME,
                abcs.get(0).constants.getStringId(propertyName, true), 0,
                allNsSet(), 0, new ArrayList<Integer>()), true);
    }

    @Override
    public GraphTargetItem returnType() {
        if (index != null) {
            return TypeItem.UNBOUNDED;
        }
        String objType = resolveObjectType();

        if (objType == null) {
            return TypeItem.UNBOUNDED;
        }
        for (ABC a : abcs) {
            int ci = a.findClassByName(objType);
            if (ci != -1) {
                for (Trait t : a.instance_info.get(ci).instance_traits.traits) {
                    String tname = t.getName(a).getName(a.constants, new ArrayList<String>());
                    if (tname.equals(propertyName)) {
                        if (t instanceof TraitSlotConst) {
                            TraitSlotConst tsc = (TraitSlotConst) t;
                            if (tsc.type_index == 0) {
                                return TypeItem.UNBOUNDED;
                            }
                            return new TypeItem(a.constants.constant_multiname.get(tsc.type_index).getNameWithNamespace(a.constants));
                        }
                        if (t instanceof TraitMethodGetterSetter) {
                            TraitMethodGetterSetter tmgs = (TraitMethodGetterSetter) t;
                            return new TypeFunctionItem(a.constants.constant_multiname.get(a.method_info.get(tmgs.method_info).ret_type).getNameWithNamespace(a.constants));
                        }
                        if (t instanceof TraitFunction) {
                            TraitFunction tf = (TraitFunction) t;
                            return new TypeFunctionItem(a.constants.constant_multiname.get(a.method_info.get(tf.method_info).ret_type).getNameWithNamespace(a.constants));
                        }
                    }
                }
                break;
            }
        }

        return TypeItem.UNBOUNDED;
    }

    @Override
    public List<GraphSourceItem> toSource(SourceGeneratorLocalData localData, SourceGenerator generator) {
        AVM2SourceGenerator g = (AVM2SourceGenerator) generator;

        int propertyId = resolveProperty();
        Object obj = object;
        if (obj == null) {
            obj = new AVM2Instruction(0, new FindPropertyStrictIns(), new int[]{propertyId}, new byte[0]);
        }
        if (storeValue != null) {
            int temp_reg = g.getFreeRegister(localData);
            List<GraphSourceItem> ret = toSourceMerge(localData, generator, obj, index, new CoerceAVM2Item(null, storeValue, resolvePropertyType().toString()),
                    new AVM2Instruction(0, new DupIns(), new int[]{}, new byte[0]),
                    new AVM2Instruction(0, new SetLocalIns(), new int[]{temp_reg}, new byte[0]),
                    new AVM2Instruction(0, new SetPropertyIns(), new int[]{propertyId}, new byte[0]),
                    new AVM2Instruction(0, new GetLocalIns(), new int[]{temp_reg}, new byte[0]),
                    new AVM2Instruction(0, new KillIns(), new int[]{temp_reg}, new byte[0])
            );
            g.killRegister(localData, temp_reg);
            return ret;
        } else {
            return toSourceMerge(localData, generator, obj, index,
                    new AVM2Instruction(0, new GetPropertyIns(), new int[]{propertyId}, new byte[0])
            );
        }
    }

    @Override
    public List<GraphSourceItem> toSourceIgnoreReturnValue(SourceGeneratorLocalData localData, SourceGenerator generator) {
        AVM2SourceGenerator g = (AVM2SourceGenerator) generator;

        int propertyId = resolveProperty();
        Object obj = object;
        if (obj == null) {
            obj = new AVM2Instruction(0, new FindPropertyStrictIns(), new int[]{propertyId}, new byte[0]);
        }
        if (storeValue != null) {
            return toSourceMerge(localData, generator, obj, index, new CoerceAVM2Item(null, storeValue, resolvePropertyType().toString()),
                    new AVM2Instruction(0, new SetPropertyIns(), new int[]{propertyId}, new byte[0])
            );
        } else {
            return toSourceMerge(localData, generator, obj, index,
                    new AVM2Instruction(0, new GetPropertyIns(), new int[]{propertyId}, new byte[0]),
                    new AVM2Instruction(0, new PopIns(), new int[]{}, new byte[0])
            );
        }
    }

    @Override
    public boolean hasReturnValue() {
        return true;
    }

}
