package fr.traqueur.structura.validation;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.validation.*;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Validation Tests")
class ValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validator.INSTANCE;
    }

    // Test records with validation annotations
    public record ValidatedRecord(
        @Min(value = 0, message = "Age must be non-negative") int age,
        @Max(value = 100, message = "Age must not exceed 100") int maxAge,
        @Size(min = 2, max = 20, message = "Name must be between 2 and 20 characters") String name,
        @NotEmpty(message = "Description cannot be empty") String description,
        @Pattern(value = "^[A-Z]{2,3}$", message = "Code must be 2-3 uppercase letters") String code
    ) implements Loadable {}

    public record NestedValidatedRecord(
        @NotEmpty String parentName,
        ValidatedRecord child
    ) implements Loadable {}

    public enum ValidatedEnum implements Loadable {
        TEST_VALUE;
        
        @Min(10) public int minValue;
        @Max(100) public int maxValue;
        @NotEmpty public String name;
    }

    @Polymorphic(useKey = true, inline = true)
    public interface ItemMetadata extends Loadable {}

    // Implementations
    public record FoodMetadata(int nutrition, double saturation) implements ItemMetadata {}

    public record SimpleFieldConfig(ItemMetadata trim) implements Loadable {}

    @Nested
    @DisplayName("Polymorphic Validation Tests")
    class PolymorphicValidationTest {
        @Test
        @DisplayName("Should fail validation for polymorphic with invalid config")
        void shouldFailValidationForPolymorphicMissUsage() {
            SimpleFieldConfig config = new SimpleFieldConfig(new FoodMetadata(5, 0.5));

            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(config, "");
            });

            assertTrue(exception.getMessage().contains("Invalid @Polymorphic configuration: 'inline' and 'useKey' cannot both be true in"));
        }
    }

    @Nested
    @DisplayName("Min Validation Tests")
    class MinValidationTest {

        @Test
        @DisplayName("Should pass validation for valid min values")
        void shouldPassValidationForValidMinValues() {
            ValidatedRecord record = new ValidatedRecord(25, 50, "ValidName", "Description", "ABC");
            
            assertDoesNotThrow(() -> validator.validate(record, ""));
        }

        @Test
        @DisplayName("Should fail validation for values below minimum")
        void shouldFailValidationForValuesBelowMinimum() {
            ValidatedRecord record = new ValidatedRecord(-5, 50, "ValidName", "Description", "ABC");
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("Age must be non-negative"));
        }

        @Test
        @DisplayName("Should validate at exact minimum value")
        void shouldValidateAtExactMinimumValue() {
            ValidatedRecord record = new ValidatedRecord(0, 50, "ValidName", "Description", "ABC");
            
            assertDoesNotThrow(() -> validator.validate(record, ""));
        }
    }

    @Nested
    @DisplayName("Max Validation Tests")
    class MaxValidationTest {

        @Test
        @DisplayName("Should pass validation for valid max values")
        void shouldPassValidationForValidMaxValues() {
            ValidatedRecord record = new ValidatedRecord(25, 75, "ValidName", "Description", "ABC");
            
            assertDoesNotThrow(() -> validator.validate(record, ""));
        }

        @Test
        @DisplayName("Should fail validation for values above maximum")
        void shouldFailValidationForValuesAboveMaximum() {
            ValidatedRecord record = new ValidatedRecord(25, 150, "ValidName", "Description", "ABC");
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("Age must not exceed 100"));
        }

        @Test
        @DisplayName("Should validate at exact maximum value")
        void shouldValidateAtExactMaximumValue() {
            ValidatedRecord record = new ValidatedRecord(25, 100, "ValidName", "Description", "ABC");
            
            assertDoesNotThrow(() -> validator.validate(record, ""));
        }
    }

    @Nested
    @DisplayName("Size Validation Tests")
    class SizeValidationTest {

        @Test
        @DisplayName("Should pass validation for valid string sizes")
        void shouldPassValidationForValidStringSizes() {
            ValidatedRecord record = new ValidatedRecord(25, 75, "ValidName", "Description", "ABC");
            
            assertDoesNotThrow(() -> validator.validate(record, ""));
        }

        @Test
        @DisplayName("Should fail validation for strings too short")
        void shouldFailValidationForStringsTooShort() {
            ValidatedRecord record = new ValidatedRecord(25, 75, "A", "Description", "ABC");
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("Name must be between 2 and 20 characters"));
        }

        @Test
        @DisplayName("Should fail validation for strings too long")
        void shouldFailValidationForStringsTooLong() {
            String longName = "A".repeat(25);
            ValidatedRecord record = new ValidatedRecord(25, 75, longName, "Description", "ABC");
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("Name must be between 2 and 20 characters"));
        }

        public record CollectionSizeRecord(
            @Size(min = 1, max = 3) List<String> items,
            @Size(min = 2, max = 5) Map<String, String> properties
        ) implements Loadable {}

        @Test
        @DisplayName("Should validate collection sizes")
        void shouldValidateCollectionSizes() {
            CollectionSizeRecord validRecord = new CollectionSizeRecord(
                List.of("item1", "item2"), 
                Map.of("key1", "value1", "key2", "value2")
            );
            
            assertDoesNotThrow(() -> validator.validate(validRecord, ""));
            
            CollectionSizeRecord invalidRecord = new CollectionSizeRecord(
                List.of(), // Empty list, below minimum
                Map.of("key1", "value1", "key2", "value2")
            );
            
            assertThrows(ValidationException.class, () -> {
                validator.validate(invalidRecord, "");
            });
        }
    }

    @Nested
    @DisplayName("NotEmpty Validation Tests")
    class NotEmptyValidationTest {

        @Test
        @DisplayName("Should pass validation for non-empty strings")
        void shouldPassValidationForNonEmptyStrings() {
            ValidatedRecord record = new ValidatedRecord(25, 75, "ValidName", "Description", "ABC");
            
            assertDoesNotThrow(() -> validator.validate(record, ""));
        }

        @Test
        @DisplayName("Should fail validation for empty strings")
        void shouldFailValidationForEmptyStrings() {
            ValidatedRecord record = new ValidatedRecord(25, 75, "ValidName", "", "ABC");
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("Description cannot be empty"));
        }

        public record CollectionNotEmptyRecord(
            @NotEmpty List<String> items,
            @NotEmpty Map<String, String> properties
        ) implements Loadable {}

        @Test
        @DisplayName("Should validate non-empty collections")
        void shouldValidateNonEmptyCollections() {
            CollectionNotEmptyRecord validRecord = new CollectionNotEmptyRecord(
                List.of("item1"), 
                Map.of("key1", "value1")
            );
            
            assertDoesNotThrow(() -> validator.validate(validRecord, ""));
            
            CollectionNotEmptyRecord invalidRecord = new CollectionNotEmptyRecord(
                List.of(), // Empty list
                Map.of("key1", "value1")
            );
            
            assertThrows(ValidationException.class, () -> {
                validator.validate(invalidRecord, "");
            });
        }
    }

    @Nested
    @DisplayName("Pattern Validation Tests")
    class PatternValidationTest {

        @Test
        @DisplayName("Should pass validation for matching patterns")
        void shouldPassValidationForMatchingPatterns() {
            ValidatedRecord record = new ValidatedRecord(25, 75, "ValidName", "Description", "ABC");
            
            assertDoesNotThrow(() -> validator.validate(record, ""));
        }

        @Test
        @DisplayName("Should fail validation for non-matching patterns")
        void shouldFailValidationForNonMatchingPatterns() {
            ValidatedRecord record = new ValidatedRecord(25, 75, "ValidName", "Description", "abc"); // lowercase
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("Code must be 2-3 uppercase letters"));
        }

        @Test
        @DisplayName("Should validate different pattern formats")
        void shouldValidateDifferentPatternFormats() {
            ValidatedRecord validABC = new ValidatedRecord(25, 75, "ValidName", "Description", "ABC");
            ValidatedRecord validAB = new ValidatedRecord(25, 75, "ValidName", "Description", "AB");
            
            assertDoesNotThrow(() -> validator.validate(validABC, ""));
            assertDoesNotThrow(() -> validator.validate(validAB, ""));
            
            ValidatedRecord invalidSingle = new ValidatedRecord(25, 75, "ValidName", "Description", "A");
            ValidatedRecord invalidLong = new ValidatedRecord(25, 75, "ValidName", "Description", "ABCD");
            
            assertThrows(ValidationException.class, () -> validator.validate(invalidSingle, ""));
            assertThrows(ValidationException.class, () -> validator.validate(invalidLong, ""));
        }
    }

    @Nested
    @DisplayName("Nested Validation Tests")
    class NestedValidationTest {

        @Test
        @DisplayName("Should validate nested records")
        void shouldValidateNestedRecords() {
            ValidatedRecord child = new ValidatedRecord(25, 75, "ValidName", "Description", "ABC");
            NestedValidatedRecord parent = new NestedValidatedRecord("ParentName", child);
            
            assertDoesNotThrow(() -> validator.validate(parent, ""));
        }

        @Test
        @DisplayName("Should fail validation for invalid nested records")
        void shouldFailValidationForInvalidNestedRecords() {
            ValidatedRecord invalidChild = new ValidatedRecord(-5, 75, "ValidName", "Description", "ABC");
            NestedValidatedRecord parent = new NestedValidatedRecord("ParentName", invalidChild);
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(parent, "");
            });
            
            assertTrue(exception.getMessage().contains("Age must be non-negative"));
        }

        @Test
        @DisplayName("Should validate parent fields independently")
        void shouldValidateParentFieldsIndependently() {
            ValidatedRecord validChild = new ValidatedRecord(25, 75, "ValidName", "Description", "ABC");
            NestedValidatedRecord invalidParent = new NestedValidatedRecord("", validChild); // Empty parent name
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(invalidParent, "");
            });
            
            assertTrue(exception.getMessage().contains("Value cannot be empty"));
        }
    }

    @Nested
    @DisplayName("Enum Validation Tests")
    class EnumValidationTest {

        @Test
        @DisplayName("Should validate enum fields")
        void shouldValidateEnumFields() {
            ValidatedEnum.TEST_VALUE.minValue = 50;
            ValidatedEnum.TEST_VALUE.maxValue = 75;
            ValidatedEnum.TEST_VALUE.name = "TestName";
            
            assertDoesNotThrow(() -> validator.validate(ValidatedEnum.TEST_VALUE, ""));
        }

        @Test
        @DisplayName("Should fail validation for invalid enum fields")
        void shouldFailValidationForInvalidEnumFields() {
            ValidatedEnum.TEST_VALUE.minValue = 5; // Below minimum
            ValidatedEnum.TEST_VALUE.maxValue = 75;
            ValidatedEnum.TEST_VALUE.name = "TestName";
            
            assertThrows(ValidationException.class, () -> {
                validator.validate(ValidatedEnum.TEST_VALUE, "");
            });
        }
    }

    @Nested
    @DisplayName("Error Message Tests")
    class ErrorMessageTest {

        @Test
        @DisplayName("Should include custom error messages")
        void shouldIncludeCustomErrorMessages() {
            ValidatedRecord record = new ValidatedRecord(-5, 75, "ValidName", "Description", "ABC");
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertEquals("Age must be non-negative", exception.getMessage());
        }

        @Test
        @DisplayName("Should format message placeholders correctly")
        void shouldFormatMessagePlaceholdersCorrectly() {
            ValidatedRecord record = new ValidatedRecord(25, 150, "ValidName", "Description", "ABC");
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("100"));
        }

        public record DefaultMessageRecord(
            @Min(5) int value // Uses default message
        ) implements Loadable {}

        @Test
        @DisplayName("Should use default error messages when not specified")
        void shouldUseDefaultErrorMessagesWhenNotSpecified() {
            DefaultMessageRecord record = new DefaultMessageRecord(3);
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("Value must be at least"));
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should handle null values gracefully")
        void shouldHandleNullValuesGracefully() {
            assertDoesNotThrow(() -> validator.validate(null, ""));
        }

        public record OptionalValidationRecord(
            @Min(0) Integer optionalValue, // Boxed type, can be null
            @NotEmpty String requiredValue
        ) implements Loadable {}

        @Test
        @DisplayName("Should skip validation for null fields")
        void shouldSkipValidationForNullFields() {
            OptionalValidationRecord record = new OptionalValidationRecord(null, "Valid");
            
            assertDoesNotThrow(() -> validator.validate(record, ""));
        }

        public record ArrayValidationRecord(
            @Size(min = 2, max = 5) String[] items
        ) implements Loadable {}

        @Test
        @DisplayName("Should validate array sizes")
        void shouldValidateArraySizes() {
            ArrayValidationRecord validRecord = new ArrayValidationRecord(new String[]{"a", "b", "c"});
            
            assertDoesNotThrow(() -> validator.validate(validRecord, ""));
            
            ArrayValidationRecord invalidRecord = new ArrayValidationRecord(new String[]{"a"});
            
            assertThrows(ValidationException.class, () -> {
                validator.validate(invalidRecord, "");
            });
        }

        public record NonNumericValidationRecord(
            @Min(5) String value // Wrong type for Min
        ) implements Loadable {}

        @Test
        @DisplayName("Should throw exception for non-numeric fields with numeric constraints")
        void shouldThrowExceptionForNonNumericFieldsWithNumericConstraints() {
            NonNumericValidationRecord record = new NonNumericValidationRecord("test");
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("Cannot validate numeric constraint"));
        }

        public record UnsupportedSizeValidationRecord(
            @Size(min = 1) Object unsupported // Unsupported type for Size
        ) implements Loadable {}

        @Test
        @DisplayName("Should throw exception for unsupported size validation types")
        void shouldThrowExceptionForUnsupportedSizeValidationTypes() {
            UnsupportedSizeValidationRecord record = new UnsupportedSizeValidationRecord(new Object());
            
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                validator.validate(record, "");
            });
            
            assertTrue(exception.getMessage().contains("Cannot validate size constraint"));
        }
    }
}