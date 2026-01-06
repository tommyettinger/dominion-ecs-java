package dev.dominion.ecs.test.engine.system;

import dev.dominion.ecs.engine.system.ClassIndex;
import dev.dominion.ecs.engine.system.Logging;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassIndexTest {

    @Test
    void addClass() {
        try (ClassIndex map = new ClassIndex()) {
            Assertions.assertEquals(1, map.addClass(C1.class));
            Assertions.assertEquals(2, map.addClass(C2.class));
            Assertions.assertEquals(2, map.addClass(C2.class));
        }
    }

    @Test
    void getIndex() {
        try (ClassIndex map = new ClassIndex()) {
            map.addClass(C1.class);
            Assertions.assertEquals(0, map.getIndex(C2.class));
            map.addClass(C2.class);
            map.addClass(C1.class);
            Assertions.assertEquals(1, map.getIndex(C1.class));
            Assertions.assertEquals(2, map.getIndex(C2.class));
        }
    }

    @Test
    void getIndexOrAddClass() {
        try (ClassIndex map = new ClassIndex()) {
            Assertions.assertEquals(1, map.getIndexOrAddClass(C1.class));
            Assertions.assertEquals(2, map.getIndexOrAddClass(C2.class));
            Assertions.assertEquals(1, map.getIndexOrAddClass(C1.class));
            Assertions.assertEquals(2, map.getIndexOrAddClass(C2.class));
        }
    }

    @Test
    void getIndexOrAddClassBatch() {
        try (ClassIndex map = new ClassIndex()) {
            Assertions.assertArrayEquals(new int[]{1, 2, 3}
                    , map.getIndexOrAddClassBatch(new Class<?>[]{C1.class, C2.class, C3.class}));
        }
    }

    @Test
    void size() {
        try (ClassIndex map = new ClassIndex()) {
            map.addClass(C1.class);
            map.addClass(C2.class);
            map.addClass(C3.class);
            Assertions.assertEquals(3, map.size());
        }
    }

    @Test
    void concurrentGetIndexOrAddClass() throws InterruptedException {
        try (ClassIndex map = new ClassIndex(22, false, Logging.Context.TEST)) {
            // In earlier versions, capacity was set to 128, which worked almost every time, but was still
            // able to rarely fail at random. Checking with more capacity causes the fallback map to be used
            // more often, and that code was broken before (when it switched to the fallback map, it wouldn't
            // measure map.size() correctly).
            final int LIMIT = 250;           // create Classes for arrays up to 250 nesting levels deep.
            final int capacity = LIMIT * 10; // there are 10 classes this creates array Classes for.

            final ExecutorService executorService = Executors.newFixedThreadPool(4);
            AtomicInteger errors = new AtomicInteger(0);
            for (String c : new String[]{
                    "Ljava.lang.Character;", "Ljava.lang.Boolean;", "Ljava.lang.Byte;", "Ljava.lang.Short;",
                    "Ljava.lang.Integer;", "Ljava.lang.Long;", "Ljava.lang.Float;", "Ljava.lang.Double;",
                    "Ljava.lang.String;", "Ljava.lang.RuntimeException;",}) {
                for (int i = 1; i <= LIMIT; i++) {
                    final int ii = i;
                    executorService.execute(() -> {
                        Class<?> newClass;
                        try {
                            // creates a new Class for a nested array of the given Class with name c.
                            // This creates up to 250 array levels deep, but can't go much higher due
                            // to JVM limits. It does not instantiate any array.
                            newClass = Class.forName("[".repeat(ii) + c, false, null);
                            var wIndex = map.addObject(newClass);
                            int rIndex;
                            if ((rIndex = map.getObjectIndex(newClass)) != wIndex) {
                                System.out.println("rIndex = " + rIndex);
                                System.out.println("wIndex = " + wIndex);
                                errors.incrementAndGet();
                            }
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e); // won't ever happen with this LIMIT.
                        }
                    });
                }
            }
            executorService.shutdown();
            Assertions.assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
            Assertions.assertEquals(capacity, map.size());
            Assertions.assertEquals(0, errors.get());
        }
    }

    private static class C1 {
    }

    private static class C2 {
    }

    private static class C3 {
    }
}
