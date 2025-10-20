package fr.traqueur.structura.types;

import fr.traqueur.structura.exceptions.StructuraException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypeToken Tests")
class TypeTokenTest {

    @Nested
    @DisplayName("TypeToken Creation")
    class TypeTokenCreationTest {

        @Test
        @DisplayName("Should create TypeToken for simple class")
        void shouldCreateTypeTokenForSimpleClass() {
            TypeToken<String> token = new TypeToken<String>() {};

            assertEquals(String.class, token.getType());
            assertEquals(String.class, token.getRawType());
        }

        @Test
        @DisplayName("Should create TypeToken for generic List")
        void shouldCreateTypeTokenForGenericList() {
            TypeToken<List<String>> token = new TypeToken<List<String>>() {};

            Type type = token.getType();
            assertInstanceOf(ParameterizedType.class, type);

            ParameterizedType paramType = (ParameterizedType) type;
            assertEquals(List.class, paramType.getRawType());
            assertEquals(String.class, paramType.getActualTypeArguments()[0]);

            assertEquals(List.class, token.getRawType());
        }

        @Test
        @DisplayName("Should create TypeToken for nested generics")
        void shouldCreateTypeTokenForNestedGenerics() {
            TypeToken<List<List<String>>> token = new TypeToken<>() {
            };

            Type type = token.getType();
            assertInstanceOf(ParameterizedType.class, type);

            ParameterizedType outerType = (ParameterizedType) type;
            assertEquals(List.class, outerType.getRawType());

            Type innerType = outerType.getActualTypeArguments()[0];
            assertInstanceOf(ParameterizedType.class, innerType);

            ParameterizedType innerParamType = (ParameterizedType) innerType;
            assertEquals(List.class, innerParamType.getRawType());
            assertEquals(String.class, innerParamType.getActualTypeArguments()[0]);
        }

        @Test
        @DisplayName("Should create TypeToken for Map with generics")
        void shouldCreateTypeTokenForMapWithGenerics() {
            TypeToken<Map<String, Integer>> token = new TypeToken<>() {
            };

            Type type = token.getType();
            assertInstanceOf(ParameterizedType.class, type);

            ParameterizedType paramType = (ParameterizedType) type;
            assertEquals(Map.class, paramType.getRawType());
            assertEquals(String.class, paramType.getActualTypeArguments()[0]);
            assertEquals(Integer.class, paramType.getActualTypeArguments()[1]);
        }

        @Test
        @DisplayName("Should create TypeToken for Optional")
        void shouldCreateTypeTokenForOptional() {
            TypeToken<Optional<String>> token = new TypeToken<>() {
            };

            Type type = token.getType();
            assertInstanceOf(ParameterizedType.class, type);

            ParameterizedType paramType = (ParameterizedType) type;
            assertEquals(Optional.class, paramType.getRawType());
            assertEquals(String.class, paramType.getActualTypeArguments()[0]);
        }

        @Test
        @DisplayName("Should throw exception when not instantiated as anonymous class")
        void shouldThrowExceptionWhenNotAnonymousClass() {
            // Create a concrete subclass (wrong usage)
            class ConcreteTypeToken<T> extends TypeToken<T> {
                public ConcreteTypeToken() {
                    super();
                }
            }

            // Trying to instantiate the concrete subclass should fail
            assertThrows(StructuraException.class, ConcreteTypeToken::new);
        }
    }

    @Nested
    @DisplayName("TypeToken Factory Methods")
    class TypeTokenFactoryMethodsTest {

        @Test
        @DisplayName("Should create TypeToken from Class using of()")
        void shouldCreateTypeTokenFromClass() {
            TypeToken<String> token = TypeToken.of(String.class);

            assertEquals(String.class, token.getType());
            assertEquals(String.class, token.getRawType());
        }

        @Test
        @DisplayName("Should create TypeToken from Type using of()")
        void shouldCreateTypeTokenFromType() {
            Type type = new TypeToken<List<String>>() {}.getType();
            TypeToken<?> token = TypeToken.of(type);

            assertEquals(type, token.getType());
            assertEquals(List.class, token.getRawType());
        }
    }

    @Nested
    @DisplayName("TypeToken Equality")
    class TypeTokenEqualityTest {

        @Test
        @DisplayName("Should be equal for same simple types")
        void shouldBeEqualForSameSimpleTypes() {
            TypeToken<String> token1 = new TypeToken<>() {};
            TypeToken<String> token2 = new TypeToken<>() {};

            assertEquals(token1, token2);
            assertEquals(token1.hashCode(), token2.hashCode());
        }

        @Test
        @DisplayName("Should be equal for same generic types")
        void shouldBeEqualForSameGenericTypes() {
            TypeToken<List<String>> token1 = new TypeToken<>() {
            };
            TypeToken<List<String>> token2 = new TypeToken<>() {
            };

            assertEquals(token1, token2);
            assertEquals(token1.hashCode(), token2.hashCode());
        }

        @Test
        @DisplayName("Should NOT be equal for different generic types")
        void shouldNotBeEqualForDifferentGenericTypes() {
            TypeToken<List<String>> token1 = new TypeToken<>() {
            };
            TypeToken<List<Integer>> token2 = new TypeToken<>() {
            };

            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("Should NOT be equal for raw vs parameterized type")
        void shouldNotBeEqualForRawVsParameterizedType() {
            TypeToken<List> token1 = new TypeToken<>() {};
            TypeToken<List<String>> token2 = new TypeToken<>() {
            };

            assertNotEquals(token1, token2);
        }
    }

    @Nested
    @DisplayName("TypeToken Edge Cases")
    class TypeTokenEdgeCasesTest {

        @Test
        @DisplayName("Should handle Set generic types")
        void shouldHandleSetGenericTypes() {
            TypeToken<Set<Integer>> token = new TypeToken<>() {
            };

            Type type = token.getType();
            assertInstanceOf(ParameterizedType.class, type);

            ParameterizedType paramType = (ParameterizedType) type;
            assertEquals(Set.class, paramType.getRawType());
            assertEquals(Integer.class, paramType.getActualTypeArguments()[0]);
        }

        @Test
        @DisplayName("Should handle complex nested generics")
        void shouldHandleComplexNestedGenerics() {
            TypeToken<Map<String, List<Integer>>> token = new TypeToken<>() {
            };

            Type type = token.getType();
            assertInstanceOf(ParameterizedType.class, type);

            ParameterizedType paramType = (ParameterizedType) type;
            assertEquals(Map.class, paramType.getRawType());
            assertEquals(String.class, paramType.getActualTypeArguments()[0]);

            Type valueType = paramType.getActualTypeArguments()[1];
            assertInstanceOf(ParameterizedType.class, valueType);

            ParameterizedType valueParamType = (ParameterizedType) valueType;
            assertEquals(List.class, valueParamType.getRawType());
            assertEquals(Integer.class, valueParamType.getActualTypeArguments()[0]);
        }
    }

    @Nested
    @DisplayName("TypeToken toString")
    class TypeTokenToStringTest {

        @Test
        @DisplayName("Should have meaningful toString for simple type")
        void shouldHaveMeaningfulToStringForSimpleType() {
            TypeToken<String> token = new TypeToken<>() {};

            String result = token.toString();
            assertTrue(result.contains("TypeToken"));
            assertTrue(result.contains("String"));
        }

        @Test
        @DisplayName("Should have meaningful toString for generic type")
        void shouldHaveMeaningfulToStringForGenericType() {
            TypeToken<List<String>> token = new TypeToken<>() {
            };

            String result = token.toString();
            assertTrue(result.contains("TypeToken"));
            assertTrue(result.contains("List"));
        }
    }
}
