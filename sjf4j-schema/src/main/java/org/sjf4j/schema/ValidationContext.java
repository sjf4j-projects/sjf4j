package org.sjf4j.schema;

import org.sjf4j.exception.NodeException;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/** Per-call execution state; no shared mutable state across validations. */
public final class ValidationContext {
    private final boolean failFast;
    private final boolean strictFormat;
    private final boolean booleanOnly;
    private List<ValidationMessage> messages;
    private boolean valid = true;
    private ValidationMessage lastMessage;
    private int ignoreErrorAdding;

    private NodeFrame leaf;
    private NodeFrame[] frames;
    private int nodeDepth;

    // Evaluated annotations are isolated by instance position AND schema branch.
    private Deque<EvaluationBits>[] evaluatedByDepth;
    private Deque<SchemaPlan> planStack;

    // Active reference invocation chain, not lifetime history.
    private SchemaPlan firstRefPlan;
    private NodeFrame firstRefNode;
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
    }

    static ValidationContext booleanOnly(boolean strictFormat) {
        return new ValidationContext(true, strictFormat, true);
    }

    NodeFrame root(Object value) {
        return NodeFrame.infer(value);
    }

    private NodeFrame nextFrame() {
        int next = nodeDepth + 1;
        NodeFrame frame;
        if (next == 1) {
            if (leaf == null) leaf = new NodeFrame();
            frame = leaf;
        } else {
            if (frames == null) {
                frames = new NodeFrame[4];
            } else if (next >= frames.length) {
                frames = Arrays.copyOf(frames, frames.length << 1);
            }
            if (frames[next] == null) frames[next] = new NodeFrame();
            frame = frames[next];
        }
        if (evaluatedByDepth != null && next < evaluatedByDepth.length
                && evaluatedByDepth[next] != null) {
            evaluatedByDepth[next].clear();
        }
        nodeDepth = next;
        return frame;
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
        NodeFrame frame = nodeDepth == 1 ? leaf : frames[nodeDepth];
        frame.finishChild();
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
        return planStack == null ? null : planStack.pop()    void enterRef(SchemaPlan plan, NodeFrame node, PathSegment keywordPs) {
        // The common case is a single $ref; avoid arrays and a scan altogether.
        if (activeRefCount == 0) {
            firstRefPlan = plan;
            firstRefNode = node;
            activeRefCount = 1;
            return;
        }

        if (firstRefPlan == plan && firstRefNode == node) {
            throw cyclicReference(plan, keywordPs);
        }
        for (int i = 1; i < activeRefCount; i++) {
            if (activeRefPlans[i] == plan && activeRefNodes[i] == node) {
                throw cyclicReference(plan, keywordPs);
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
        activeRefNodes[activeRefCount] = node;
        activeRefCount++;
    }

    private static NodeException cyclicReference(SchemaPlan plan, PathSegment keywordPs) {
        return new NodeException(SchemaUtil.formatSchemaLine(
                SchemaUtil.Code.SCHEMA_RESOLVE,
                "cyclic schema reference detected", keywordPs, plan.schemaUri));
    }

    void leaveRef() {
        if (--activeRefCount == 0) {
            firstRefPlan = null;
            firstRefNode = null;
        } else {
            activeRefPlans[activeRefCount] = null;
            activeRefNodes[activeRefCount] = null;
        }
    }

      activeRefPlans[i] = null;
        activeRefNodes[i] = null;
    }

    @SuppressWarnings("unchecked")
    private Deque<EvaluationBits> evaluated(boolean create) {
        if (evaluatedByDepth == null) {
            if (!create) return null;
            evaluatedByDepth = (Deque<EvaluationBits>[]) new Deque[4];
        } else if (nodeDepth >= evaluatedByDepth.length) {
            if (!create) return null;
            evaluatedByDepth = Arrays.copyOf(evaluatedByDepth,
                    Math.max(nodeDepth + 1, evaluatedByDepth.length << 1));
        }
        Deque<EvaluationBits> stack = evaluatedByDepth[nodeDepth];
        if (stack == null && create) {
            stack = new ArrayDeque<>();
            evaluatedByDepth[nodeDepth] = stack;
        }
        return stack;
    }

    int evaluationDepth() {
        Deque<EvaluationBits> stack = evaluated(false);
        return stack == null ? 0 : stack.size();
    }

    void restoreEvaluationDepth(int depth) {
        Deque<EvaluationBits> stack = evaluated(false);
        if (stack != null) while (stack.size() > depth) stack.pop();
    }

    void createEvaluated() {
        evaluated(true).push(new EvaluationBits());
    }

    void pushEvaluated() {
        Deque<EvaluationBits> stack = evaluated(false);
        if (stack != null) stack.push(new EvaluationBits());
    }

    void pushEvaluated(EvaluationBits bits) {
        Deque<EvaluationBits> stack = evaluated(false);
        if (stack != null && bits != null) stack.push(bits);
    }

    EvaluationBits popEvaluated() {
        Deque<EvaluationBits> stack = evaluated(false);
        return stack == null || stack.isEmpty() ? null : stack.pop();
    }

    EvaluationBits peekEvaluated() {
        Deque<EvaluationBits> stack = evaluated(false);
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }

    void markEvaluated(int index) {
        EvaluationBits bits = peekEvaluated();
        if (bits != null) bits.set(index);
    }

    void markEvaluated(int from, int to) {
        EvaluationBits bits = peekEvaluated();
        if (bits != null) bits.set(from, to);
    }

    EvaluationBits mergedEvaluated() {
        EvaluationBits merged = new EvaluationBits();
        Deque<EvaluationBits> stack = evaluated(false);
        if (stack != null) for (EvaluationBits bits : stack) merged.or(bits);
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
        if (failFast) {
            lastMessage = msg;
        } else {
            if (messages == null) messages = new ArrayList<>();
            messages.add(msg);
        }
        valid = false;
    }

    void addWarn(PathSegment instancePs, PathSegment keywordPs, URI schemaUri,
                 String keyword, String message) {
        if (!failFast && !booleanOnly && ignoreErrorAdding == 0) {
            if (messages == null) messages = new ArrayList<>();
            messages.add(new ValidationMessage(ValidationMessage.Severity.WARN,
                    instancePs, keywordPs, schemaUri, keyword, message));
        }
    }
}
