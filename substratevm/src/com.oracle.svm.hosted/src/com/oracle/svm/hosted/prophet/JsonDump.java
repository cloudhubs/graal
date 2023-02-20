package com.oracle.svm.hosted.prophet;

import java.lang.reflect.Field;
import java.util.Collection;

public class JsonDump {

    private final StringBuilder builder = new StringBuilder();

    private int indent = 1;

    public static String dump(Object object) {
        try {
            JsonDump instance = new JsonDump();
            System.out.println("beginning visiting elem");
            instance.visitElem(object);
            System.out.println("visited elem");
            return instance.builder.toString();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

    }

    private void visitElem(Object value) throws ReflectiveOperationException {
        if (value == null) {
            append("null");
        } else if (value instanceof Boolean || value instanceof Integer) {
            append(value.toString());
        } else if (value instanceof String) {
            appendString((String) value);
        } else if (value instanceof Collection) {
            visitCollection(((Collection<?>) value));
        } else {
            visitObject(value);
        }
    }

    private void appendString(String value) {
        append('"');
        append(value);
        append('"');
    }

    private void visitObject(Object object) throws ReflectiveOperationException {
        append('{');
        indent++;
        appendLn();

        dumpFields(object);

        indent--;
        append('}');
        appendLn();
    }

    private void visitCollection(Collection<?> value) throws ReflectiveOperationException {
        append('[');
        indent++;
        appendLn();

        int n = value.size();
        int i = 0;
        for (Object o : value) {
            visitElem(o);
            if (++i < n)
                append(',');
            appendLn();
        }

        indent--;
        append(']');
        appendLn();
    }

    private void dumpFields(Object object) throws ReflectiveOperationException {
        Field[] fields = object.getClass().getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            appendString(field.getName());
            append(" : ");
            field.setAccessible(true);
            Object value = field.get(object);
            visitElem(value);
            if (i != fields.length - 1)
                append(',');
            appendLn();
        }
        // System.out.println("\n" + builder + "\n");
    }

    private void appendLn() {
        builder.append('\n');
        builder.append("\t".repeat(indent));
    }

    private void append(char c) {
        append("" + c);
    }

    private void append(String s) {
        builder.append(s);
    }
}
