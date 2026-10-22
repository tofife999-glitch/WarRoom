package warroom.ui;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

/**
 * Just enough reflection to find panels of the game's own Intel screen. Starsector refuses java.lang.reflect
 * to mods, so those classes are loaded straight from the JDK and only used through method handles, as other
 * Intel screen mods do. Lookups are cached per class. Anything can be missing on another game version:
 * callers treat every failure as "not there".
 */
final class Reflect {
    private static final MethodHandle METHODS;
    private static final MethodHandle METHOD_NAME;
    private static final MethodHandle METHOD_PARAMS;
    private static final MethodHandle METHOD_INVOKE;
    private static final MethodHandle METHOD_ACCESSIBLE;
    private static final MethodHandle FIELDS;
    private static final MethodHandle FIELD_TYPE;
    private static final MethodHandle FIELD_MODIFIERS;
    private static final MethodHandle FIELD_GET;
    private static final MethodHandle FIELD_ACCESSIBLE;
    private static final int STATIC = 0x0008;
    private static final Object NONE = new Object();
    private static final Map<String, Object> cache = new HashMap<>();

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            ClassLoader jdk = Class.class.getClassLoader();
            Class<?> method = Class.forName("java.lang.reflect.Method", false, jdk);
            Class<?> field = Class.forName("java.lang.reflect.Field", false, jdk);
            METHODS = l.findVirtual(Class.class, "getMethods", MethodType.methodType(method.arrayType()));
            METHOD_NAME = l.findVirtual(method, "getName", MethodType.methodType(String.class));
            METHOD_PARAMS = l.findVirtual(method, "getParameterCount", MethodType.methodType(int.class));
            METHOD_INVOKE = l.findVirtual(method, "invoke", MethodType.methodType(Object.class, Object.class, Object[].class));
            METHOD_ACCESSIBLE = l.findVirtual(method, "setAccessible", MethodType.methodType(void.class, boolean.class));
            FIELDS = l.findVirtual(Class.class, "getDeclaredFields", MethodType.methodType(field.arrayType()));
            FIELD_TYPE = l.findVirtual(field, "getType", MethodType.methodType(Class.class));
            FIELD_MODIFIERS = l.findVirtual(field, "getModifiers", MethodType.methodType(int.class));
            FIELD_GET = l.findVirtual(field, "get", MethodType.methodType(Object.class, Object.class));
            FIELD_ACCESSIBLE = l.findVirtual(field, "setAccessible", MethodType.methodType(void.class, boolean.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Reflect() {
    }

    /** Whether the object has a public method of this name. */
    static boolean has(Object target, String name) throws Throwable {
        return target != null && method(target.getClass(), name, -1) != null;
    }

    /** Calls a public method by name and number of arguments; throws NoSuchMethodException if there is none. */
    static Object call(Object target, String name, Object... args) throws Throwable {
        Object m = method(target.getClass(), name, args.length);
        if (m == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        return METHOD_INVOKE.invoke(m, target, args);
    }

    /** Calls a public static method of a class named by its full name. */
    static Object callStatic(String className, String name, Object... args) throws Throwable {
        Class<?> c = Class.forName(className);
        Object m = method(c, name, args.length);
        if (m == null) throw new NoSuchMethodException(className + "." + name);
        return METHOD_INVOKE.invoke(m, (Object) null, args);
    }

    /** The value of the object's first instance field whose type's simple name is {@code typeName}, or null. */
    static Object field(Object target, String typeName) throws Throwable {
        Object f = field(target.getClass(), typeName, null);
        return f == null ? null : FIELD_GET.invoke(f, target);
    }

    /** The value of the object's first instance field of this type (or a subtype), or null. */
    static Object field(Object target, Class<?> type) throws Throwable {
        Object f = field(target.getClass(), null, type);
        return f == null ? null : FIELD_GET.invoke(f, target);
    }

    private static Object method(Class<?> c, String name, int params) throws Throwable {
        String key = c.getName() + "#" + name + "/" + params;
        Object m = cache.get(key);
        if (m == null) {
            m = NONE;
            for (Object o : (Object[]) METHODS.invoke(c)) {
                if (!name.equals((String) METHOD_NAME.invoke(o))) continue;
                if (params >= 0 && (int) METHOD_PARAMS.invoke(o) != params) continue;
                METHOD_ACCESSIBLE.invoke(o, true);
                m = o;
                break;
            }
            cache.put(key, m);
        }
        return m == NONE ? null : m;
    }

    private static Object field(Class<?> start, String typeName, Class<?> type) throws Throwable {
        String key = start.getName() + "@" + (typeName != null ? typeName : type.getName());
        Object f = cache.get(key);
        if (f == null) {
            f = NONE;
            search:
            for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Object o : (Object[]) FIELDS.invoke(c)) {
                    if (((int) FIELD_MODIFIERS.invoke(o) & STATIC) != 0) continue;
                    Class<?> ft = (Class<?>) FIELD_TYPE.invoke(o);
                    boolean match = typeName != null ? ft.getSimpleName().equals(typeName) : type.isAssignableFrom(ft);
                    if (!match) continue;
                    FIELD_ACCESSIBLE.invoke(o, true);
                    f = o;
                    break search;
                }
            }
            cache.put(key, f);
        }
        return f == NONE ? null : f;
    }
}
