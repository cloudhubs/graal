/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import org.graalvm.jniutils.HotSpotCalls;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JMethodID;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JThrowable;
import org.graalvm.jniutils.JNI.JValue;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.io.IOException;

import static org.graalvm.jniutils.JNIUtil.GetStaticMethodID;
import static org.graalvm.jniutils.JNIUtil.encodeMethodSignature;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

/**
 * This exception is used to transfer a local exception over the boundary. The local exception is
 * marshaled, passed over the boundary as a {@link ForeignException}, unmarshalled, and re-thrown.
 */
@SuppressWarnings("serial")
public final class ForeignException extends RuntimeException {

    static final byte UNDEFINED = 0;
    static final byte HOST_TO_GUEST = 1;
    static final byte GUEST_TO_HOST = 2;
    private static final ThreadLocal<ForeignException> pendingException = new ThreadLocal<>();
    private static final JNIMethodResolver CreateForeignException = JNIMethodResolver.create("createForeignException", Throwable.class, byte[].class);
    private static final JNIMethodResolver ToByteArray = JNIMethodResolver.create("toByteArray", byte[].class, ForeignException.class);
    private static volatile HotSpotCalls toHotSpot;

    private final byte kind;
    private final byte[] rawData;

    private ForeignException(byte[] rawData, byte kind, boolean writableStackTrace) {
        super(null, null, true, writableStackTrace);
        this.rawData = rawData;
        this.kind = kind;
    }

    /**
     * Re-throws this exception in HotSpot using a JNI API.
     */
    public void throwInHotSpot(JNIEnv env) {
        JNIUtil.Throw(env, callCreateForeignException(env, JNIUtil.createHSArray(env, rawData)));
    }

    /**
     * Unmarshalls the foreign exception transferred by this {@link ForeignException} and re-throws
     * it.
     *
     * @param marshaller the marshaller to unmarshal the exception
     */
    public RuntimeException throwOriginalException(BinaryMarshaller<? extends Throwable> marshaller) {
        try (BinaryInput in = BinaryInput.create(rawData)) {
            Throwable t;
            try {
                t = marshaller.read(in);
            } catch (IOException ioe) {
                throw new AssertionError(ioe.getMessage(), ioe);
            }
            throw ForeignException.silenceException(RuntimeException.class, t);
        } finally {
            clearPendingException();
        }
    }

    /**
     * Merges foreign stack trace marshalled in the {@link ForeignException} with local
     * {@link ForeignException}'s stack trace. This is a helper method for throwable marshallers to
     * merge local and foreign stack traces. Typical usage looks like this:
     *
     * <pre>
     * final class DefaultThrowableMarshaller implements BinaryMarshaller&lt;Throwable&gt; {
     *     private final BinaryMarshaller&lt;StackTraceElement[]&gt; stackTraceMarshaller = MyJNIConfig.getDefault().lookupMarshaller(StackTraceElement[].class);
     *
     *     &#64;Override
     *     public Throwable read(BinaryInput in) throws IOException {
     *         String foreignExceptionClassName = in.readUTF();
     *         String foreignExceptionMessage = in.readUTF();
     *         StackTraceElement[] foreignExceptionStack = stackTraceMarshaller.read(in);
     *         RuntimeException exception = new RuntimeException(foreignExceptionClassName + ": " + foreignExceptionMessage);
     *         exception.setStackTrace(ForeignException.mergeStackTrace(foreignExceptionStack));
     *         return exception;
     *     }
     *
     *     &#64;Override
     *     public void write(BinaryOutput out, Throwable object) throws IOException {
     *         out.writeUTF(object.getClass().getName());
     *         out.writeUTF(object.getMessage());
     *         stackTraceMarshaller.write(out, object.getStackTrace());
     *     }
     * }
     * </pre>
     *
     * @param foreignExceptionStack the stack trace marshalled into the {@link ForeignException}
     * @return the stack trace combining both local and foreign stack trace elements
     */
    public static StackTraceElement[] mergeStackTrace(StackTraceElement[] foreignExceptionStack) {
        if (foreignExceptionStack.length == 0) {
            // Exception has no stack trace, nothing to merge.
            return foreignExceptionStack;
        }
        ForeignException localException = pendingException.get();
        if (localException != null) {
            switch (localException.kind) {
                case HOST_TO_GUEST:
                    return JNIExceptionWrapper.mergeStackTraces(localException.getStackTrace(), foreignExceptionStack, false);
                case GUEST_TO_HOST:
                    return JNIExceptionWrapper.mergeStackTraces(foreignExceptionStack, localException.getStackTrace(), true);
                default:
                    throw new IllegalStateException("Unsupported kind " + localException.kind);
            }
        } else {
            return foreignExceptionStack;
        }
    }

    /**
     * Creates a {@link ForeignException} by marshaling the {@code exception} using
     * {@code marshaller}.
     *
     * @param exception the exception that should be passed over the boundary
     * @param marshaller the marshaller to marshall the exception
     */
    public static ForeignException forException(Throwable exception, BinaryMarshaller<? super Throwable> marshaller) {
        try (BinaryOutput.ByteArrayBinaryOutput out = BinaryOutput.create()) {
            marshaller.write(out, exception);
            return new ForeignException(out.getArray(), UNDEFINED, false);
        } catch (IOException ioe) {
            throw new AssertionError(ioe.getMessage(), ioe);
        }
    }

    /**
     * Returns the {@link HotSpotCalls} transferring the {@link ForeignException} thrown in the
     * HotSpot to an isolate.
     */
    public static HotSpotCalls getHotSpotCalls() {
        HotSpotCalls res = toHotSpot;
        if (res == null) {
            res = createHotSpotCalls();
            toHotSpot = res;
        }
        return res;
    }

    byte[] toByteArray() {
        return rawData;
    }

    static ForeignException create(byte[] rawData, byte kind) {
        ForeignException exception = new ForeignException(rawData, kind, true);
        pendingException.set(exception);
        return exception;
    }

    private static void clearPendingException() {
        pendingException.set(null);
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T silenceException(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }

    private static HotSpotCalls createHotSpotCalls() {
        return HotSpotCalls.createWithExceptionHandler(context -> {
            if (ForeignException.class.getName().equals(context.getThrowableClassName())) {
                JNIEnv env = context.getEnv();
                byte[] marshalledData = JNIUtil.createArray(env, callToByteArray(env, context.getThrowable()));
                throw ForeignException.create(marshalledData, ForeignException.GUEST_TO_HOST);
            } else {
                context.throwJNIExceptionWrapper();
            }
        });
    }

    private static JThrowable callCreateForeignException(JNIEnv env, JByteArray rawValue) {
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(rawValue);
        return HotSpotCalls.getDefault().callStaticJObject(env, CreateForeignException.getHotSpotEntryPoints(env), CreateForeignException.resolve(env), args);
    }

    private static JByteArray callToByteArray(JNIEnv env, JObject p0) {
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(p0);
        return HotSpotCalls.getDefault().callStaticJObject(env, ToByteArray.getHotSpotEntryPoints(env), ToByteArray.resolve(env), args);
    }

    private static final class JNIMethodResolver implements HotSpotCalls.JNIMethod {

        private final String methodName;
        private final String methodSignature;
        private volatile JClass entryPointsClass;
        private volatile JMethodID methodId;

        private JNIMethodResolver(String methodName, String methodSignature) {
            this.methodName = methodName;
            this.methodSignature = methodSignature;
        }

        JNIMethodResolver resolve(JNIEnv jniEnv) {
            JMethodID res = methodId;
            if (res.isNull()) {
                JClass entryPointClass = getHotSpotEntryPoints(jniEnv);
                try (CTypeConversion.CCharPointerHolder name = toCString(methodName); CTypeConversion.CCharPointerHolder sig = toCString(methodSignature)) {
                    res = GetStaticMethodID(jniEnv, entryPointClass, name.get(), sig.get());
                    if (res.isNull()) {
                        throw new InternalError("No such method: " + methodName);
                    }
                    methodId = res;
                }
            }
            return this;
        }

        JClass getHotSpotEntryPoints(JNIEnv env) {
            JClass res = entryPointsClass;
            if (res.isNull()) {
                res = JNIClassCache.lookupClass(env, ForeignExceptionEndPoints.class);
                entryPointsClass = res;
            }
            return res;
        }

        @Override
        public JMethodID getJMethodID() {
            return methodId;
        }

        @Override
        public String getDisplayName() {
            return methodName;
        }

        static JNIMethodResolver create(String methodName, Class<?> returnType, Class<?>... parameterTypes) {
            return new JNIMethodResolver(methodName, encodeMethodSignature(returnType, parameterTypes));
        }
    }
}
