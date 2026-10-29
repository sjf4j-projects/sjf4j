package org.sjf4j.schema;

import org.sjf4j.exception.NodeException;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;

/** Per-call execution state; no shared mutable state across validations. */
public final class ValidationContext {
    private final boolean failFast;
    private final boolean strictFormat;
    private final boolean booleanOnly;
    private final List<ValidationMessage> messages;
    private boolean valid = true;
    private ValidationMessage lastMessage;
    private int ignoreErrorAdding;

    private NodeFrame root;
    private NodeFrame[] frames;
    private int nodeDepth;

    // Evaluated annotations are isolated by instance position AND schema branch.
    private Deque<BitSet>[] evaluatedByDepth;
    private Deque<SchemaPlan> planStack;

    // Active reference invocation chain, not lifetime history.
    private SchemaPlan[] activeRefPlans;
    private NodeFrame[] activeRefNodes;
    private int activeRefCount;

    ValidationContext(boolean failFast, boolean strictFormat) {
        this(failFast, strictFormat, false);
    }

    private ValidationContext(boolean failFast, boolean strictFormat, boolean booleanOnly) {
        this.failFast = failFast;
        this.strictFormat = strictFormat;
        this.booleanOnly = booleanOnly;
        this.messages = !failFast && !booleanOnly ? new ArrayList<>() : null;
    }

    static ValidationContext booleanOnly(boolean strictFormat) {
        return new ValidationContext(true, strictFormat, true);
    }

    NodeFrame root(Object value) {
        root = NodeFrame.infer(value);
        return root;
    }

    private NodeFrame nextFrame() {
        int next = nodeDepth + 1;
        if (frames == null) {
            frames = new NodeFrame[4];
        } else if (next >= frames.length) {
            frames = Arrays.copyOf(frames, frames.length << 1);
        }
        if (frames[next] == null) frames[next] = new NodeFrame();
        if (evaluatedByDepth != null && next < evaluatedByDepth.length
                && evaluatedByDepth[next] != null) {
            evaluatedByDepth[next].clear();
        }
        nodeDepth = next;
        return frames[next];
    }

    NodeFrame enterKey(NodeFrame parent, String key, Object value) {
        return nextFrame().bindKey(parent, key, value);
    }

    NodeFrame enterIndex(NodeFrame parent, int index, Object value) {
        return nextFrame().bindIndex(parent, index, value);
    }

    NodeFrame enterValue(Object value) {
        return nextFrame().bind(value);
    }

    void leaveChild() {
        nodeDepth--;
    }

    public boolean isFailFast() { return failFast; }
    public boolean isStrictFormat() { return strictFormat; }
    public boolean isValid() { return valid; }
    public boolean shouldAbort() { return failFast || ignoreErrorAdding != 0; }
    boolean needsDiagnostic() { return !booleanOnly && ignoreErrorAdding == 0; }

    public void pushIgnoreError() { ignoreErrorAdding++; }
    public void popIgnoreError() { ignoreErrorAdding--; }

    public void pushPlan(SchemaPlan plan) {
        if (planStack == null) planStack = new ArrayDeque<>();
        planStack.push(plan);
    }

    public SchemaPlan popPlan() {
        return planStack == null ? null : planStack.pop();
    }

    public Deque<SchemaPlan> planStack() { return planStack; }

    void enterRef(SchemaPlan plan, NodeFrame node, PathSegment keywordPs) {
        for (int i = activeRefCount - 1; i >= 0; i--) {
            if (activeRefPlans[i] == plan && activeRefNodes[i] == node) {
                throw new NodeException(SchemaUtil.formatSchemaLine(
                        SchemaUtil.Code.SCHEMA_RESOLVE,
                        "cyclic schema reference detected", keywordPs, plan.schemaUri));
            }
        }
        if (activeRefPlans == null) {
            activeRefPlans = new SchemaPlan[4];
            activeRefNodes = new NodeFrame[4];
        } else if (activeRefCount == activeRefPlans.length) {
            activeRefPlans = Arrays.copyOf(activeRefPlans, activeRefCount << 1);
            activeRefNodes = Arrays.copyOf(activeRefNodes, activeRefCount << 1);
        }
        activeRefPlans[activeRefCount] = plan;
        activeRefNodes[activeRefCount++] = node;
    }

    void leaveRef() {
        int i = --activeRefCount;
        activeRefPlans[i] = null;
        activeRefNodes[i] = null;
    }

    @SuppressWarnings("unchecked")
    private Deque<BitSet> evaluated(boolean create) {
        if (evaluatedByDepth == null) {
            if (!create) return null;
            evaluatedByDepth = (Deque<BitSet>[]) new Deque[4];
        } else if (nodeDepth >= evaluatedByDepth.length) {
            if (!create) return null;
            evaluatedByDepth = Arrays.copyOf(evaluatedByDepth,
                    Math.max(nodeDepth + 1, evaluatedByDepth.length << 1));
        }
        Deque<BitSet> stack = evaluatedByDepth[nodeDepth];
        if (stack == null && create) {
            stack = new ArrayDeque<>();
            evaluatedByDepth[nodeDepth] = stack;
        }
        return stack;
    }

    int evaluationDepth() {
        Deque<BitSet> stack = evaluated(false);
        return stack == null ? 0 : stack.size();
    }

    void restoreEvaluationDepth(int depth) {
        Deque<BitSet> stack = evaluated(false);
        if (stack != null) while (stack.size() > depth) stack.pop();
    }

    void createEvaluated() {
        evaluated(true).push(new BitSet());
    }

    void pushEvaluated() {
        Deque<BitSet> stack = evaluated(false);
        if (stack != null) stack.push(new BitSet());
    }

    void pushEvaluated(BitSet bits) {
        Deque<BitSet> stack = evaluated(false);
        if (stack != null && bits != null) stack.push(bits);
    }

    BitSet popEvaluated() {
        Deque<BitSet> stack = evaluated(false);
        return stack == null || stack.isEmpty() ? null : stack.pop();
    }

    BitSet peekEvaluated() {
        Deque<BitSet> stack = evaluated(false);
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }

    void markEvaluated(int index) {
        BitSet bits = peekEvaluated();
        if (bits != null) bits.set(index);
    }

    void markEvaluated(int from, int to) {
        BitSet bits = peekEvaluated();
        if (bits != null) bits.set(from, to);
    }

    BitSet mergedEvaluated() {
        BitSet merged = new BitSet();
        Deque<BitSet> stack = evaluated(false);
        if (stack != null) for (BitSet bits : stack) merged.or(bits);
        return merged;
    }

    public ValidationResult toResult() {
        if (valid && (messages == null || messages.isEmpty()) && lastMessage == null) {
            return ValidationResult.SUCCESS;
        }
        return new ValidationResult(valid, messages, lastMessage);
    }

    void addError(NodeFrame instance, PathSegment instancePs, PathSegment keywordPs,
                  URI schemaUri, String keyword, String message) {
        if (!needsDiagnostic()) return;
        if (instancePs == null && instance != null) instancePs = instance.materializePath();
        ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR,
                instancePs, keywordPs, schemaUri, keyword, message);
        if (messages != null) messages.add(msg);
        else lastMessage = msg;
        valid = false;
    }

    void addWarn(PathSegment instancePs, PathSegment keywordPs, URI schemaUri,
                 String keyword, String message) {
        if (messages != null && ignoreErrorAdding == 0) {
            messages.add(new ValidationMessage(ValidationMessage.Severity.WARN,
                    instancePs, keywordPs, schemaUri, keyword, message));
        }
    }
}
