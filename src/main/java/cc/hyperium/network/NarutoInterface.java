package cc.hyperium.network;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;

public class NarutoInterface {
    public static HashSet<Class<? extends NarutoInterfac>> classes = new HashSet<>();
    public static void someStuff() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        for (Class<? extends NarutoInterfac> clazz : classes) {
            clazz.getMethod("someCoolStuff").invoke(null);
        }
    }
    public static interface NarutoInterfac {
        void someCoolStuff();
    }
}
