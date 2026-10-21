package org.sjf4j.meta;

import org.sjf4j.JsonObject;
import org.sjf4j.exception.BindingException;

import java.util.Arrays;


public final class CreatorState {

    private static final Object UNSET = new Object();
    private static final int INITIAL_PENDING_CAPACITY = 4;

    private final CreatorInfo creatorInfo;

    private Object pojo;

    // Object[] for multi-argument creators, or the argument/sentinel for arity one.
    private Object args;
    private int remainingArgs;

    private PropertyInfo[] pendingFields;
    private Object[] pendingValues;
    private int pendingSize;

    private String[] pendingNames;
    private Object[] pendingNameValues;
    private int pendingNameSize;


    public CreatorState(CreatorInfo creatorInfo) {
        this.creatorInfo = creatorInfo;
        if (creatorInfo.hasNoArgsCreator()) {
            this.pojo = creatorInfo.newPojoNoArgs();
            return;
        }

        int argCount = creatorInfo.argNames == null ? 0 : creatorInfo.argNames.length;
        if (argCount == 1) {
            this.args = UNSET;
        } else {
            Object[] array = new Object[argCount];
            Arrays.fill(array, UNSET);
            this.args = array;
        }
        this.remainingArgs = argCount;
        if (argCount == 0) {
            createPojo();
        }
    }


    public boolean isCreated() {
        return pojo != null;
    }

    public Object pojo() {
        return pojo;
    }

    public void acceptCtorArg(int index, Object value) {
        if (creatorInfo.argNames.length == 1) {
            if (args != UNSET) {
                throw new BindingException("duplicate creator argument assignment for '" + creatorInfo.argNames[index] + "'");
            }
            args = value;
        } else {
            Object[] array = (Object[]) args;
            if (array[index] != UNSET) {
                throw new BindingException("duplicate creator argument assignment for '" + creatorInfo.argNames[index] + "'");
            }
            array[index] = value;
        }
        if (--remainingArgs == 0) {
            createPojo();
        }
    }

    /**
     * Only used while pojo has not been created yet.
     */
    public void bufferProperty(PropertyInfo field, Object value) {
        if (pojo != null) {
            throw new IllegalStateException("pojo already created");
        }

        if (pendingFields == null) {
            pendingFields = new PropertyInfo[INITIAL_PENDING_CAPACITY];
            pendingValues = new Object[INITIAL_PENDING_CAPACITY];

        } else if (pendingSize == pendingFields.length) {
            int newCapacity = pendingSize << 1;
            pendingFields = Arrays.copyOf(pendingFields, newCapacity);
            pendingValues = Arrays.copyOf(pendingValues, newCapacity);
        }

        pendingFields[pendingSize] = field;
        pendingValues[pendingSize] = value;
        pendingSize++;
    }

    public void acceptDynamic(String name, Object value) {
        if (pojo != null) {
            ((JsonObject) pojo).put(name, value);
            return;
        }

        if (pendingNames == null) {
            pendingNames = new String[INITIAL_PENDING_CAPACITY];
            pendingNameValues = new Object[INITIAL_PENDING_CAPACITY];
        } else if (pendingNameSize == pendingNames.length) {
            int newCapacity = pendingNameSize << 1;
            pendingNames = Arrays.copyOf(pendingNames, newCapacity);
            pendingNameValues = Arrays.copyOf(pendingNameValues, newCapacity);
        }

        pendingNames[pendingNameSize] = name;
        pendingNameValues[pendingNameSize] = value;
        pendingNameSize++;
    }

    public Object finish() {
        if (pojo == null) {
            if (creatorInfo.argNames.length == 1) {
                if (args == UNSET) args = null;
            } else {
                Object[] array = (Object[]) args;
                for (int i = 0; i < array.length; i++) {
                    if (array[i] == UNSET) array[i] = null;
                }
            }
            createPojo();
        }
        return pojo;
    }

    private void createPojo() {
        pojo = creatorInfo.argNames.length == 1
                ? creatorInfo.newPojoWithOneArg(args)
                : creatorInfo.newPojoWithArgs((Object[]) args);
        replayPendingProperties();
        replayPendingDynamic();
    }

    private void replayPendingProperties() {
        for (int i = 0; i < pendingSize; i++) {
            pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
        }
        pendingSize = 0;
    }

    private void replayPendingDynamic() {
        if (pendingNameSize == 0) return;

        JsonObject jo = (JsonObject) pojo;
        for (int i = 0; i < pendingNameSize; i++) {
            jo.put(pendingNames[i], pendingNameValues[i]);
        }
        pendingNameSize = 0;
    }

}