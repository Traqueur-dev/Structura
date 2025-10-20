package fr.traqueur.structura.registries;

import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.readers.Reader;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CustomReaderRegistry Tests")
class CustomReaderRegistryTest {

    private CustomReaderRegistry registry;

    // Test types
    record CustomType(String value) {}
    record AnotherType(int number) {}

    @BeforeEach
    void setUp() {
        registry = CustomReaderRegistry.getInstance();
        registry.clear();
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonPatternTest {

        @Test
        @DisplayName("Should return the same instance on multiple calls")
        void shouldReturnSameInstance() {
            CustomReaderRegistry instance1 = CustomReaderRegistry.getInstance();
            CustomReaderRegistry instance2 = CustomReaderRegistry.getInstance();

            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("Should maintain state across getInstance calls")
        void shouldMaintainStateAcrossGetInstanceCalls() {
            Reader<CustomType> reader = CustomType::new;

            CustomReaderRegistry instance1 = CustomReaderRegistry.getInstance();
            instance1.register(CustomType.class, reader);

            CustomReaderRegistry instance2 = CustomReaderRegistry.getInstance();

            assertTrue(instance2.hasReader(CustomType.class));
            assertEquals(1, instance2.size());
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTest {

        @Test
        @DisplayName("Should successfully register a reader")
        void shouldSuccessfullyRegisterReader() {
            Reader<CustomType> reader = CustomType::new;

            registry.register(CustomType.class, reader);

            assertTrue(registry.hasReader(CustomType.class));
            assertEquals(1, registry.size());
        }

        @Test
        @DisplayName("Should register multiple readers for different classes")
        void shouldRegisterMultipleReaders() {
            Reader<CustomType> reader1 = CustomType::new;
            Reader<AnotherType> reader2 = str -> new AnotherType(Integer.parseInt(str));

            registry.register(CustomType.class, reader1);
            registry.register(AnotherType.class, reader2);

            assertTrue(registry.hasReader(CustomType.class));
            assertTrue(registry.hasReader(AnotherType.class));
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("Should throw exception when registering null class")
        void shouldThrowExceptionWhenRegisteringNullClass() {
            Reader<CustomType> reader = CustomType::new;

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    registry.register(null, reader)
            );

            assertTrue(exception.getMessage().contains("Cannot register reader for null class"));
        }

        @Test
        @DisplayName("Should throw exception when registering null reader")
        void shouldThrowExceptionWhenRegisteringNullReader() {
            StructuraException exception = assertThrows(StructuraException.class, () ->
                    registry.register(CustomType.class, null)
            );

            assertTrue(exception.getMessage().contains("Cannot register null reader"));
            assertTrue(exception.getMessage().contains(CustomType.class.getName()));
        }

        @Test
        @DisplayName("Should throw exception when registering duplicate reader")
        void shouldThrowExceptionWhenRegisteringDuplicate() {
            Reader<CustomType> reader1 = CustomType::new;
            Reader<CustomType> reader2 = str -> new CustomType(str.toUpperCase());

            registry.register(CustomType.class, reader1);

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    registry.register(CustomType.class, reader2)
            );

            assertTrue(exception.getMessage().contains("already registered"));
            assertTrue(exception.getMessage().contains(CustomType.class.getName()));
        }
    }

    @Nested
    @DisplayName("Unregistration")
    class UnregistrationTest {

        @Test
        @DisplayName("Should successfully unregister a reader")
        void shouldSuccessfullyUnregisterReader() {
            Reader<CustomType> reader = CustomType::new;
            registry.register(CustomType.class, reader);

            boolean result = registry.unregister(CustomType.class);

            assertTrue(result);
            assertFalse(registry.hasReader(CustomType.class));
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("Should return false when unregistering non-existent reader")
        void shouldReturnFalseWhenUnregisteringNonExistent() {
            boolean result = registry.unregister(CustomType.class);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should throw exception when unregistering null class")
        void shouldThrowExceptionWhenUnregisteringNullClass() {
            StructuraException exception = assertThrows(StructuraException.class, () ->
                    registry.unregister(null)
            );

            assertTrue(exception.getMessage().contains("Cannot unregister reader for null class"));
        }

        @Test
        @DisplayName("Should unregister specific reader without affecting others")
        void shouldUnregisterSpecificReaderOnly() {
            Reader<CustomType> reader1 = CustomType::new;
            Reader<AnotherType> reader2 = str -> new AnotherType(Integer.parseInt(str));

            registry.register(CustomType.class, reader1);
            registry.register(AnotherType.class, reader2);

            registry.unregister(CustomType.class);

            assertFalse(registry.hasReader(CustomType.class));
            assertTrue(registry.hasReader(AnotherType.class));
            assertEquals(1, registry.size());
        }
    }

    @Nested
    @DisplayName("Conversion")
    class ConversionTest {

        @Test
        @DisplayName("Should successfully convert string value using registered reader")
        void shouldSuccessfullyConvertString() {
            Reader<CustomType> reader = CustomType::new;
            registry.register(CustomType.class, reader);

            Optional<CustomType> result = registry.convert("test-value", CustomType.class);

            assertTrue(result.isPresent());
            assertEquals("test-value", result.get().value());
        }

        @Test
        @DisplayName("Should handle complex conversion logic")
        void shouldHandleComplexConversionLogic() {
            Reader<AnotherType> reader = str -> new AnotherType(Integer.parseInt(str) * 2);
            registry.register(AnotherType.class, reader);

            Optional<AnotherType> result = registry.convert("42", AnotherType.class);

            assertTrue(result.isPresent());
            assertEquals(84, result.get().number());
        }

        @Test
        @DisplayName("Should return empty when value is not a string")
        void shouldReturnEmptyWhenValueIsNotString() {
            Reader<CustomType> reader = CustomType::new;
            registry.register(CustomType.class, reader);

            Optional<CustomType> result1 = registry.convert(123, CustomType.class);
            Optional<CustomType> result2 = registry.convert(true, CustomType.class);
            Optional<CustomType> result3 = registry.convert(new Object(), CustomType.class);

            assertTrue(result1.isEmpty());
            assertTrue(result2.isEmpty());
            assertTrue(result3.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when no reader is registered")
        void shouldReturnEmptyWhenNoReaderRegistered() {
            Optional<CustomType> result = registry.convert("test", CustomType.class);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when target class is null")
        void shouldReturnEmptyWhenTargetClassIsNull() {
            Optional<CustomType> result = registry.convert("test", null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should throw exception when reader fails")
        void shouldThrowExceptionWhenReaderFails() {
            Reader<AnotherType> reader = str -> new AnotherType(Integer.parseInt(str));
            registry.register(AnotherType.class, reader);

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    registry.convert("not-a-number", AnotherType.class)
            );

            assertTrue(exception.getMessage().contains("Failed to convert"));
            assertTrue(exception.getMessage().contains("not-a-number"));
            assertTrue(exception.getMessage().contains(AnotherType.class.getName()));
        }

        @Test
        @DisplayName("Should wrap reader exceptions in StructuraException")
        void shouldWrapReaderExceptions() {
            Reader<CustomType> reader = str -> {
                throw new RuntimeException("Custom error");
            };
            registry.register(CustomType.class, reader);

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    registry.convert("test", CustomType.class)
            );

            assertNotNull(exception.getCause());
            assertEquals("Custom error", exception.getCause().getMessage());
        }

        @Test
        @DisplayName("Should handle null string values")
        void shouldHandleNullStringValues() {
            Reader<CustomType> reader = str -> new CustomType(str == null ? "default" : str);
            registry.register(CustomType.class, reader);

            // null is not a String instance, so it should return empty
            Optional<CustomType> result = registry.convert(null, CustomType.class);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodsTest {

        @Test
        @DisplayName("Should return true when reader exists")
        void shouldReturnTrueWhenReaderExists() {
            Reader<CustomType> reader = CustomType::new;
            registry.register(CustomType.class, reader);

            assertTrue(registry.hasReader(CustomType.class));
        }

        @Test
        @DisplayName("Should return false when reader does not exist")
        void shouldReturnFalseWhenReaderDoesNotExist() {
            assertFalse(registry.hasReader(CustomType.class));
        }

        @Test
        @DisplayName("Should return false when checking null class")
        void shouldReturnFalseWhenCheckingNullClass() {
            assertFalse(registry.hasReader(null));
        }

        @Test
        @DisplayName("Should clear all readers")
        void shouldClearAllReaders() {
            Reader<CustomType> reader1 = CustomType::new;
            Reader<AnotherType> reader2 = str -> new AnotherType(Integer.parseInt(str));

            registry.register(CustomType.class, reader1);
            registry.register(AnotherType.class, reader2);

            registry.clear();

            assertEquals(0, registry.size());
            assertFalse(registry.hasReader(CustomType.class));
            assertFalse(registry.hasReader(AnotherType.class));
        }

        @Test
        @DisplayName("Should return correct size")
        void shouldReturnCorrectSize() {
            assertEquals(0, registry.size());

            Reader<CustomType> reader1 = CustomType::new;
            registry.register(CustomType.class, reader1);
            assertEquals(1, registry.size());

            Reader<AnotherType> reader2 = str -> new AnotherType(Integer.parseInt(str));
            registry.register(AnotherType.class, reader2);
            assertEquals(2, registry.size());

            registry.unregister(CustomType.class);
            assertEquals(1, registry.size());

            registry.clear();
            assertEquals(0, registry.size());
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTest {

        // Wrapper classes for thread safety testing
        record Wrapper0(String value) {}
        record Wrapper1(String value) {}
        record Wrapper2(String value) {}
        record Wrapper3(String value) {}
        record Wrapper4(String value) {}
        record Wrapper5(String value) {}
        record Wrapper6(String value) {}
        record Wrapper7(String value) {}
        record Wrapper8(String value) {}
        record Wrapper9(String value) {}

        @Test
        @DisplayName("Should handle concurrent registrations")
        void shouldHandleConcurrentRegistrations() throws InterruptedException {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            threads[0] = new Thread(() -> registry.register(Wrapper0.class, Wrapper0::new));
            threads[1] = new Thread(() -> registry.register(Wrapper1.class, Wrapper1::new));
            threads[2] = new Thread(() -> registry.register(Wrapper2.class, Wrapper2::new));
            threads[3] = new Thread(() -> registry.register(Wrapper3.class, Wrapper3::new));
            threads[4] = new Thread(() -> registry.register(Wrapper4.class, Wrapper4::new));
            threads[5] = new Thread(() -> registry.register(Wrapper5.class, Wrapper5::new));
            threads[6] = new Thread(() -> registry.register(Wrapper6.class, Wrapper6::new));
            threads[7] = new Thread(() -> registry.register(Wrapper7.class, Wrapper7::new));
            threads[8] = new Thread(() -> registry.register(Wrapper8.class, Wrapper8::new));
            threads[9] = new Thread(() -> registry.register(Wrapper9.class, Wrapper9::new));

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(threadCount, registry.size());
        }
    }
}