/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.List;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

//JaCoCo Exclude

/**
 * A node that changes the type of its input, usually narrowing it. For example, a {@link PiNode}
 * refines the type of a receiver during type-guarded inlining to be the type tested by the guard.
 *
 * In contrast to a {@link GuardedValueNode}, a {@link PiNode} is useless as soon as the type of its
 * input is as narrow or narrower than the {@link PiNode}'s type. The {@link PiNode}, and therefore
 * also the scheduling restriction enforced by the guard, will go away.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
@NodeIntrinsicFactory
public class PiNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, Canonicalizable, ValueProxy {

    public static final NodeClass<PiNode> TYPE = NodeClass.create(PiNode.class);
    @Input ValueNode object;
    protected Stamp piStamp;

    public ValueNode object() {
        return object;
    }

    @SuppressWarnings("this-escape")
    protected PiNode(NodeClass<? extends PiNode> c, ValueNode object, Stamp stamp, GuardingNode guard) {
        super(c, stamp, guard);
        this.object = object;
        this.piStamp = stamp;
        assert piStamp.isCompatible(object.stamp(NodeView.DEFAULT)) : "Object stamp not compatible to piStamp";
        inferStamp();
    }

    public PiNode(ValueNode object, Stamp stamp) {
        this(object, stamp, null);
    }

    public PiNode(ValueNode object, Stamp stamp, ValueNode guard) {
        this(TYPE, object, stamp, (GuardingNode) guard);
    }

    public PiNode(ValueNode object, ValueNode guard) {
        this(object, AbstractPointerStamp.pointerNonNull(object.stamp(NodeView.DEFAULT)), guard);
    }

    public PiNode(ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull) {
        this(object, StampFactory.object(exactType ? TypeReference.createExactTrusted(toType) : TypeReference.createWithoutAssumptions(toType),
                        nonNull || StampTool.isPointerNonNull(object.stamp(NodeView.DEFAULT))));
    }

    public static ValueNode create(ValueNode object, Stamp stamp) {
        ValueNode value = canonical(object, stamp, null, null);
        if (value != null) {
            return value;
        }
        return new PiNode(object, stamp);
    }

    public static ValueNode create(ValueNode object, Stamp stamp, ValueNode guard) {
        ValueNode value = canonical(object, stamp, (GuardingNode) guard, null);
        if (value != null) {
            return value;
        }
        return new PiNode(object, stamp, guard);
    }

    public static ValueNode create(ValueNode object, ValueNode guard) {
        Stamp stamp = AbstractPointerStamp.pointerNonNull(object.stamp(NodeView.DEFAULT));
        ValueNode value = canonical(object, stamp, (GuardingNode) guard, null);
        if (value != null) {
            return value;
        }
        return new PiNode(object, stamp, guard);
    }

    public enum IntrinsifyOp {
        NON_NULL,
        POSITIVE_INT,
        INT_NON_ZERO,
        LONG_NON_ZERO,
        DOUBLE_NON_NAN,
        FLOAT_NON_NAN
    }

    public static boolean intrinsify(GraphBuilderContext b, ValueNode input, ValueNode guard, IntrinsifyOp intrinsifyOp) {
        Stamp piStamp;
        JavaKind pushKind;
        switch (intrinsifyOp) {
            case NON_NULL:
                piStamp = AbstractPointerStamp.pointerNonNull(input.stamp(NodeView.DEFAULT));
                pushKind = JavaKind.Object;
                break;
            case POSITIVE_INT:
                piStamp = StampFactory.positiveInt();
                pushKind = JavaKind.Int;
                break;
            case INT_NON_ZERO:
                piStamp = StampFactory.nonZeroInt();
                pushKind = JavaKind.Int;
                break;
            case LONG_NON_ZERO:
                piStamp = StampFactory.nonZeroLong();
                pushKind = JavaKind.Long;
                break;
            case FLOAT_NON_NAN:
                // non NAN float stamp
                piStamp = new FloatStamp(32, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, true);
                pushKind = JavaKind.Float;
                break;
            case DOUBLE_NON_NAN:
                // non NAN double stamp
                piStamp = new FloatStamp(64, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
                pushKind = JavaKind.Double;
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(intrinsifyOp); // ExcludeFromJacocoGeneratedReport
        }
        ValueNode value = canonical(input, piStamp, (GuardingNode) guard, null);
        if (value == null) {
            value = new PiNode(input, piStamp, guard);
        }
        b.push(pushKind, b.append(value));
        return true;
    }

    public static boolean intrinsify(GraphBuilderContext b, ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull, ValueNode guard) {
        Stamp stamp = StampFactory.object(exactType ? TypeReference.createExactTrusted(toType) : TypeReference.createWithoutAssumptions(toType),
                        nonNull || StampTool.isPointerNonNull(object.stamp(NodeView.DEFAULT)));
        ValueNode value = canonical(object, stamp, (GuardingNode) guard, null);
        if (value == null) {
            value = new PiNode(object, stamp);
        }
        b.push(JavaKind.Object, b.append(value));
        return true;
    }

    /**
     * A stamp expressing the property that is proved by the {@linkplain #getGuard() guard}, but not
     * more.
     * </p>
     *
     * For example, if the guard proves a property {@code x >= 0} on an {@code int} value, then the
     * {@link #piStamp()} should be {@link StampFactory#positiveInt()}. If the input value's stamp
     * is constrained, e.g., {@code [-100 - 100]}, then this pi's overall {@link #stamp(NodeView)}
     * will be {@code [0 - 100]}, computed as the join of the {@link #piStamp()} and the input's
     * stamp.
     */
    public final Stamp piStamp() {
        return piStamp;
    }

    public void strengthenPiStamp(Stamp newPiStamp) {
        assert this.piStamp.join(newPiStamp).equals(newPiStamp) : "stamp can only improve";
        this.piStamp = newPiStamp;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        if (generator.hasOperand(object)) {
            generator.setResult(this, generator.operand(object));
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(computeStamp());
    }

    private Stamp computeStamp() {
        return piStamp.improveWith(object().stamp(NodeView.DEFAULT));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            ResolvedJavaType type = StampTool.typeOrNull(this, tool.getMetaAccess());
            if (type != null && type.isAssignableFrom(virtual.type())) {
                tool.replaceWithVirtual(virtual);
            } else {
                tool.getDebug().log(DebugContext.INFO_LEVEL, "could not virtualize Pi because of type mismatch: %s %s vs %s", this, type, virtual.type());
            }
        }
    }

    @SuppressFBWarnings(value = {"NP"}, justification = "We null check it before")
    public static ValueNode canonical(ValueNode object, Stamp piStamp, GuardingNode guard, ValueNode self) {
        GraalError.guarantee(piStamp != null && object != null, "Invariant piStamp=%s object=%s guard=%s self=%s", piStamp, object, guard, self);

        // Use most up to date stamp.
        Stamp computedStamp = piStamp.improveWith(object.stamp(NodeView.DEFAULT));

        // The pi node does not give any additional information => skip it.
        if (computedStamp.equals(object.stamp(NodeView.DEFAULT))) {
            return object;
        }

        if (guard == null) {
            // Try to merge the pi node with a load node.
            if (object instanceof ReadNode && !object.hasMoreThanOneUsage()) {
                ReadNode readNode = (ReadNode) object;
                readNode.setStamp(readNode.stamp(NodeView.DEFAULT).improveWith(piStamp));
                return readNode;
            }
        } else {
            for (Node n : guard.asNode().usages()) {
                if (n instanceof PiNode && n != self) {
                    PiNode otherPi = (PiNode) n;
                    if (otherPi.guard != guard) {
                        assert otherPi.object() == guard : Assertions.errorMessageContext("object", object, "otherPi", otherPi, "guard", guard);
                        /*
                         * The otherPi is unrelated because it uses this.guard as object but not as
                         * guard.
                         */
                        continue;
                    }
                    if (otherPi.object() == self || otherPi.object() == object) {
                        // Check if other pi's stamp is more precise
                        Stamp joinedPiStamp = piStamp.improveWith(otherPi.piStamp());
                        if (joinedPiStamp.equals(piStamp)) {
                            // Stamp did not get better, nothing to do.
                        } else if (otherPi.object() == object && joinedPiStamp.equals(otherPi.piStamp())) {
                            // We can be replaced with the other pi.
                            return otherPi;
                        } else if (self != null && self.hasExactlyOneUsage() && otherPi.object == self) {
                            if (joinedPiStamp.equals(otherPi.piStamp)) {
                                return object;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Node value = canonical(object(), piStamp(), getGuard(), this);
        if (value != null) {
            return value;
        }
        if (tool.allUsagesAvailable()) {
            for (Node usage : usages()) {
                if (!(usage instanceof VirtualState)) {
                    return this;
                }
            }
            // Only state usages: for them a more precise stamp does not matter.
            return object;
        }
        return this;
    }

    /**
     * Perform Pi canonicalizations on any PiNodes anchored at {@code guard} in an attempt to
     * eliminate all of them. This purely done to enable earlier elimination of the user of these
     * PiNodes.
     */
    public static void tryEvacuate(SimplifierTool tool, GuardingNode guard) {
        tryEvacuate(tool, guard, true);
    }

    private static void tryEvacuate(SimplifierTool tool, GuardingNode guard, boolean recurse) {
        ValueNode guardNode = guard.asNode();
        if (guardNode.hasNoUsages()) {
            return;
        }

        List<PiNode> pis = guardNode.usages().filter(PiNode.class).snapshot();
        for (PiNode pi : pis) {
            if (!pi.isAlive()) {
                continue;
            }
            if (pi.hasNoUsages()) {
                pi.safeDelete();
                continue;
            }

            /*
             * RECURSE CALL
             *
             * If there are PiNodes still anchored at this guard then either they must simplify away
             * because they are no longer necessary or this node must be replaced with a
             * ValueAnchorNode because the type injected by the PiNode is only true at this point in
             * the control flow.
             */
            if (recurse && pi.getOriginalNode() instanceof PiNode) {
                // It's not uncommon for one extra level of PiNode to inhibit removal of
                // this PiNode so try to simplify the input first.
                GuardingNode otherGuard = ((PiNode) pi.getOriginalNode()).guard;
                if (otherGuard != null) {
                    tryEvacuate(tool, otherGuard, false);
                }
            }
            /*
             * A note on the RECURSE CALL above: When we have pis with input pis on the same guard
             * (which should actually be combined) it can be that the recurse call (processing the
             * same pis again) already deletes this node (very special stamp setups necessary).
             * Thus, it can be that pi is dead at this point already, so we have to check for this
             * again.
             */
            if (!pi.isAlive()) {
                continue;
            }
            Node canonical = pi.canonical(tool);
            if (canonical != pi) {
                if (!canonical.isAlive()) {
                    canonical = guardNode.graph().addOrUniqueWithInputs(canonical);
                }
                pi.replaceAtUsages(canonical);
                pi.safeDelete();
            }
        }
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }

    public void setOriginalNode(ValueNode newNode) {
        this.updateUsages(object, newNode);
        this.object = newNode;
        assert piStamp.isCompatible(object.stamp(NodeView.DEFAULT)) : "New object stamp not compatible to piStamp";
    }

    /**
     * Changes the stamp of an object inside a snippet to be the stamp of the node replaced by the
     * snippet.
     */
    @NodeIntrinsic(PiNode.Placeholder.class)
    public static native Object piCastToSnippetReplaceeStamp(Object object);

    /**
     * Changes the stamp of a primitive value and ensures the newly stamped value is positive and
     * does not float above a given guard.
     *
     * @param value an arbitrary {@code int} value
     * @param guard a node proving that {@code value >= 0} holds at some point in the graph
     *
     * @return the {@code value} with its stamp clamped to exclude negative values, guarded by
     *         {@code guard}
     */
    public static int piCastPositive(int value, GuardingNode guard) {
        return intrinsified(value, guard, IntrinsifyOp.POSITIVE_INT);
    }

    public static int piCastNonZero(int value, GuardingNode guard) {
        return intrinsified(value, guard, IntrinsifyOp.INT_NON_ZERO);
    }

    public static long piCastNonZero(long value, GuardingNode guard) {
        return intrinsified(value, guard, IntrinsifyOp.LONG_NON_ZERO);
    }

    @NodeIntrinsic
    private static native int intrinsified(int value, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    @NodeIntrinsic
    private static native long intrinsified(long value, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    /**
     * Changes the stamp of an object and ensures the newly stamped value is non-null and does not
     * float above a given guard.
     */
    public static Object piCastNonNull(Object object, GuardingNode guard) {
        return intrinsified(object, guard, IntrinsifyOp.NON_NULL);
    }

    @NodeIntrinsic
    private static native Object intrinsified(Object object, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    /**
     * Changes the stamp of an object and ensures the newly stamped value is non-null and does not
     * float above a given guard.
     */
    public static Class<?> piCastNonNullClass(Class<?> type, GuardingNode guard) {
        return intrinsified(type, guard, IntrinsifyOp.NON_NULL);
    }

    public static float piCastNonNanFloat(float input, GuardingNode guard) {
        return intrinsified(input, guard, IntrinsifyOp.FLOAT_NON_NAN);
    }

    @NodeIntrinsic
    private static native float intrinsified(float input, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    public static double piCastNonNanDouble(double input, GuardingNode guard) {
        return intrinsified(input, guard, IntrinsifyOp.DOUBLE_NON_NAN);
    }

    @NodeIntrinsic
    private static native double intrinsified(double input, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    @NodeIntrinsic
    private static native Class<?> intrinsified(Class<?> object, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    @NodeIntrinsic
    public static native Object piCast(Object object, @ConstantNodeParameter ResolvedJavaType toType,
                    @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull, GuardingNode guard);

    @NodeIntrinsic
    public static native Object piCast(Object object, @ConstantNodeParameter Class<?> toType,
                    @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull, GuardingNode guard);

    /**
     * A placeholder node in a snippet that will be replaced with a {@link PiNode} when the snippet
     * is instantiated.
     */
    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    public static class Placeholder extends FloatingGuardedNode {

        public static final NodeClass<Placeholder> TYPE = NodeClass.create(Placeholder.class);
        @Input ValueNode object;

        public ValueNode object() {
            return object;
        }

        protected Placeholder(NodeClass<? extends Placeholder> c, ValueNode object) {
            super(c, PlaceholderStamp.SINGLETON, null);
            this.object = object;
        }

        public Placeholder(ValueNode object) {
            this(TYPE, object);
        }

        /**
         * Replaces this node with a {@link PiNode} during snippet instantiation.
         *
         * @param snippetReplaceeStamp the stamp of the node being replace by the snippet
         */
        public void makeReplacement(Stamp snippetReplaceeStamp) {
            ValueNode value = graph().addOrUnique(PiNode.create(object(), snippetReplaceeStamp, null));
            replaceAndDelete(value);
        }
    }

    /**
     * A stamp for {@link Placeholder} nodes which are only used in snippets. It is replaced by an
     * actual stamp when the snippet is instantiated.
     */
    public static final class PlaceholderStamp extends ObjectStamp {
        private static final PlaceholderStamp SINGLETON = new PlaceholderStamp();

        public static PlaceholderStamp singleton() {
            return SINGLETON;
        }

        private PlaceholderStamp() {
            super(null, false, false, false, false);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return "PlaceholderStamp";
        }
    }

    /**
     * Maximum number of usage iterations per guard for
     * {@link #guardTrySkipPi(GuardingNode, LogicNode, boolean)}.
     */
    private static final int MAX_PI_USAGE_ITERATIONS = 8;

    /**
     * Optimize a (Fixed)Guard-condition-pi pattern as a whole: note that is is different than what
     * conditional elimination does because here we detect exhaustive patterns and optimize them as
     * a whole. This is hard to express in CE as we optimize both a pi and its condition in one go.
     * There is no dedicated optimization phase in graal that does this, therefore we build on
     * simplification as a more non-local transform.
     *
     * We are looking for the following pattern
     */
    // @formatter:off
    //               inputPiObject
    //               |
    //               inputPi----
    //               |          |
    //               |          |
    //            condition     |
    //               |          |
    //          (fixed) guard     |
    //               |          |
    //               usagePi----
    //@formatter:on
    /*
     * and we optimize the condition and the pi together to use inputPi's input if inputPi does not
     * contribute any knowledge to usagePi. This means that inputPi is totally skipped. If both
     * inputPi and usagePi ultimately work on the same input (un-pi-ed) then later conditional
     * elimination can cleanup inputPi's guard if applicable.
     *
     * Note: this optimization does not work for subtypes of PiNode like DynamicPi as their stamps
     * are not yet known.
     */
    public static boolean guardTrySkipPi(GuardingNode guard, LogicNode condition, boolean negated) {
        if (!(guard instanceof FixedGuardNode || guard instanceof GuardNode || guard instanceof BeginNode)) {
            return false;
        }
        final LogicNode usagePiCondition = condition;

        if (usagePiCondition.inputs().filter(PiNode.class).isEmpty()) {
            return false;
        }
        int iterations = 0;
        boolean progress = true;
        int pisSkipped = 0;
        outer: while (progress && iterations++ < MAX_PI_USAGE_ITERATIONS) {
            progress = false;
            // look for the pattern from the javadoc
            for (PiNode usagePi : piUsageSnapshot(guard)) {
                /*
                 * Restrict this optimization to regular pi nodes only - sub classes of pi nodes
                 * implement delayed pi stamp computation or other optimizations and should thus not
                 * be skipped.
                 */
                if (!usagePi.isRegularPi()) {
                    continue;
                }
                final ValueNode usagePiObject = usagePi.object();
                if (!usagePiCondition.inputs().contains(usagePiObject)) {
                    continue;
                }

                final Stamp usagePiPiStamp = usagePi.piStamp();
                final Stamp usagePiFinalStamp = usagePi.stamp(NodeView.DEFAULT);

                final boolean usagePiObjectRegularPi = usagePiObject instanceof PiNode inputPi && inputPi.isRegularPi();
                if (!usagePiObjectRegularPi) {
                    continue;
                }

                final PiNode inputPi = (PiNode) usagePiObject;

                /*
                 * Ensure that the pi actually "belongs" to this guard in the sense that the
                 * succeeding stamp for the guard is actually the pi stamp.
                 */
                Stamp succeedingStamp = null;
                if (usagePiCondition instanceof UnaryOpLogicNode uol) {
                    succeedingStamp = uol.getSucceedingStampForValue(negated);
                } else if (usagePiCondition instanceof BinaryOpLogicNode bol) {
                    if (bol.getX() == inputPi) {
                        succeedingStamp = bol.getSucceedingStampForX(negated, bol.getX().stamp(NodeView.DEFAULT), bol.getY().stamp(NodeView.DEFAULT).unrestricted());
                    } else if (bol.getY() == inputPi) {
                        succeedingStamp = bol.getSucceedingStampForY(negated, bol.getX().stamp(NodeView.DEFAULT).unrestricted(), bol.getY().stamp(NodeView.DEFAULT));
                    }
                }
                final boolean piProvenByCondition = succeedingStamp != null && usagePiPiStamp.equals(succeedingStamp);
                if (!piProvenByCondition) {
                    continue;
                }
                /*
                 * We want to find out if the inputPi can be skipped because usagePi's guard and pi
                 * stamp prove enough knowledge to actually skip inputPi completely. This can be
                 * relevant for complex type check patterns and interconnected pis: conditional
                 * elimination cannot enumerate all values thus we try to free up local patterns
                 * early by skipping unnecessary pis.
                 */
                final Stamp inputPiPiStamp = inputPi.piStamp();
                final Stamp inputPiObjectFinalStamp = inputPi.object().stamp(NodeView.DEFAULT);
                /*
                 * Determine if the stamp from piInput.input & usagePi.piStamp is equally strong
                 * than the current piStamp, then we can build a new pi that skips the input pi.
                 */
                final Stamp resultStampWithInputPiObjectOnly = usagePiPiStamp.improveWith(inputPiObjectFinalStamp);
                final boolean thisPiEquallyStrongWithoutInputPi = resultStampWithInputPiObjectOnly.tryImproveWith(inputPiPiStamp) == null;
                if (thisPiEquallyStrongWithoutInputPi) {
                    assert resultStampWithInputPiObjectOnly.tryImproveWith(inputPiPiStamp) == null : Assertions.errorMessage(
                                    "Dropping input pi assumes that input pi stamp does not contribute to knowledge but it does", inputPi, inputPi.object(), usagePiPiStamp,
                                    usagePiFinalStamp);
                    // The input pi's object stamp was strong enough so we can skip the input pi.
                    final ValueNode newPi = usagePiCondition.graph().addOrUnique(PiNode.create(inputPi.object(), usagePiPiStamp, usagePi.getGuard().asNode()));
                    final LogicNode newCondition = (LogicNode) usagePiCondition.copyWithInputs(true);
                    newCondition.replaceAllInputs(usagePiObject, inputPi.object());
                    if (guard.asNode() instanceof FixedGuardNode fg) {
                        fg.setCondition(newCondition, negated);
                    } else if (guard.asNode() instanceof GuardNode floatingGuard) {
                        floatingGuard.setCondition(newCondition, negated);
                    } else if (guard.asNode() instanceof BeginNode) {
                        ((IfNode) guard.asNode().predecessor()).setCondition(newCondition);
                    } else {
                        GraalError.shouldNotReachHere("Unknown guard " + guard);
                    }
                    usagePi.replaceAndDelete(newPi);
                    progress = true;
                    pisSkipped++;
                    continue outer;
                }
            }
        }
        return pisSkipped > 0;
    }

    private boolean isRegularPi() {
        return getClass() == PiNode.class;
    }

    private static Iterable<PiNode> piUsageSnapshot(GuardingNode guard) {
        return guard.asNode().usages().filter(PiNode.class).snapshot();
    }
}
