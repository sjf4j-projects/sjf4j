# Comprehensive Test Cases for SJF4J Constructor and Record Support

## Overview
This document describes the comprehensive test suite created for testing SJF4J's support for:
1. **Parameterized constructors** with @JsonProperty and @JsonAlias annotations
2. **Record-style immutable classes** (Java 8 compatible)

## Test Files

### 1. ConstructorTest.java (25 test cases)
Tests for classes using parameterized constructors with Jackson annotations.

#### Test Categories:

**Simple Constructor with @JsonProperty**
- `testSimpleConstructorWithJsonProperty()` - Basic constructor with @JsonProperty

**Constructor with @JsonAlias**
- `testConstructorWithJsonAlias_MainProperty()` - Using main property name
- `testConstructorWithJsonAlias_Alias1()` - Using first alias (snake_case)
- `testConstructorWithJsonAlias_Alias2()` - Using second alias (camelCase)
- `testConstructorWithJsonAlias_Alias3()` - Using third alias (login)

**Constructor with Required Fields**
- `testConstructorWithRequiredFields()` - All required fields present

**Constructor with Mixed Required/Optional Fields**
- `testConstructorWithMixedFields_AllPresent()` - All fields provided
- `testConstructorWithMixedFields_OnlyRequired()` - Only required fields provided

**Constructor with Nested Objects**
- `testConstructorWithNestedObject()` - Nested POJO deserialization

**Constructor with Collections**
- `testConstructorWithCollection()` - List of strings
- `testConstructorWithEmptyCollection()` - Empty list handling

**Complex Constructor with Multiple Types**
- `testComplexConstructor_MainProperties()` - Using main property names
- `testComplexConstructor_WithAliases()` - Using all aliases
- `testComplexConstructor_MixedAliases()` - Mixing main names and aliases

**Edge Cases: Null Values**
- `testConstructorWithNullValues()` - Explicit null values
- `testConstructorWithMissingOptionalFields()` - Missing optional fields

**Edge Cases: Empty and Special Strings**
- `testConstructorWithEmptyString()` - Empty string values
- `testConstructorWithSpecialCharacters()` - Chinese characters (测试用户)
- `testConstructorWithEscapedCharacters()` - Escaped characters (\n, \t)
- `testConstructorWithUnicodeAndEmoji()` - Unicode and emoji (👋 🌍)
- `testConstructorWithLongString()` - Very long strings (1000 chars)

**Edge Cases: Numeric Boundaries**
- `testConstructorWithMaxInteger()` - Integer.MAX_VALUE
- `testConstructorWithMinInteger()` - Integer.MIN_VALUE

**Nested Lists with Complex Objects**
- `testConstructorWithNestedListOfObjects()` - Order with list of items using aliases

**Deeply Nested Objects**
- `testDeeplyNestedConstructors()` - Three levels of nested objects

### 2. RecordStyleTest.java (35 test cases)
Tests for immutable classes with record-style accessors (Java 8 compatible).

#### Test Categories:

**Simple Record-Style**
- `testSimpleRecordStyle()` - Basic record-style class

**Record-Style with @JsonAlias**
- `testRecordStyleWithAlias_MainProperty()` - Using main property name
- `testRecordStyleWithAlias_Alias1()` - Using first alias (snake_case)
- `testRecordStyleWithAlias_Alias2()` - Using second alias (camelCase)
- `testRecordStyleWithAlias_MixedAliases()` - Mixing main and aliases

**Record-Style with Multiple Fields and Types**
- `testProductRecordStyle_MainProperties()` - Product with multiple types
- `testProductRecordStyle_WithAliases()` - Using aliases
- `testProductRecordStyle_MixedAliases()` - Mixing names and aliases

**Nested Record-Style Classes**
- `testNestedRecordStyle()` - Person with Address
- `testNestedRecordStyle_WithAliases()` - Using aliases for nested objects

**Record-Style with Collections**
- `testRecordStyleWithCollections()` - Lists of strings and integers
- `testRecordStyleWithCollections_Aliases()` - Collections with aliases
- `testRecordStyleWithEmptyCollections()` - Empty collections

**Record-Style with List of Nested Classes**
- `testRecordStyleWithListOfNested()` - Order with items using aliases

**Record-Style with Required Fields**
- `testRecordStyleWithRequiredFields_AllPresent()` - All fields present
- `testRecordStyleWithRequiredFields_OnlyRequired()` - Only required fields

**Edge Cases: Null Values**
- `testRecordStyleWithNullValues()` - Explicit null values
- `testRecordStyleWithMissingFields()` - Missing optional fields

**Edge Cases: Empty and Special Strings**
- `testRecordStyleWithEmptyString()` - Empty string handling
- `testRecordStyleWithSpecialCharacters()` - Chinese characters
- `testRecordStyleWithEscapedCharacters()` - Escaped characters
- `testRecordStyleWithUnicodeAndEmoji()` - Unicode and emoji
- `testRecordStyleWithLongString()` - Very long strings

**Edge Cases: Numeric Boundaries**
- `testRecordStyleWithMaxInteger()` - Integer.MAX_VALUE
- `testRecordStyleWithMinInteger()` - Integer.MIN_VALUE

**Deeply Nested Record-Style**
- `testDeeplyNestedRecordStyle()` - Three levels deep
- `testDeeplyNestedRecordStyle_WithAliases()` - Nested with aliases

**Complex Record-Style with Multiple Aliases**
- `testComplexRecordStyle_MainProperties()` - All main properties
- `testComplexRecordStyle_AllAliases()` - All aliases
- `testComplexRecordStyle_MixedAliases()` - Mixed names and aliases

**Record-Style with Primitive vs Boxed Types**
- `testRecordStyleWithPrimitiveTypes()` - Primitive types (int, boolean, double)
- `testRecordStyleWithBoxedTypes_AllPresent()` - Boxed types with values
- `testRecordStyleWithBoxedTypes_NullValues()` - Boxed types with nulls

**Record-Style with Arrays**
- `testRecordStyleWithArrays()` - String[] and int[] arrays
- `testRecordStyleWithEmptyArrays()` - Empty arrays

## Test Coverage Summary

### Total Test Cases: 60
- ConstructorTest: 25 tests
- RecordStyleTest: 35 tests

### Coverage Areas:

1. **@JsonProperty Support**: ✓ Comprehensive
   - Main property names
   - Required vs optional fields
   - Various data types (String, int, BigDecimal, Boolean, etc.)

2. **@JsonAlias Support**: ✓ Comprehensive
   - Single alias
   - Multiple aliases per field
   - Different naming conventions (snake_case, camelCase, etc.)
   - Mixed usage of main names and aliases

3. **Data Types**: ✓ Comprehensive
   - Primitives: int, boolean, double
   - Boxed: Integer, Boolean, Double
   - String with various encodings
   - BigDecimal
   - Collections: List<String>, List<Integer>
   - Arrays: String[], int[]
   - Nested objects
   - Lists of nested objects

4. **Edge Cases**: ✓ Comprehensive
   - Null values
   - Empty strings
   - Empty collections
   - Missing optional fields
   - Special characters (Chinese, Japanese, etc.)
   - Escaped characters (\n, \t, etc.)
   - Unicode and emoji
   - Very long strings (1000+ chars)
   - Integer boundaries (MAX_VALUE, MIN_VALUE)
   - Deeply nested structures (3+ levels)

5. **Nesting Levels**: ✓ Comprehensive
   - Simple flat objects
   - Single level nesting
   - Multiple level nesting (up to 3 levels)
   - Lists of nested objects

## Usage Examples

All tests use the pattern:
```java
String json = "{...}";
MyClass obj = Sjf4j.fromJson(json, MyClass.class);
```

## Test Execution

Run all constructor and record-style tests:
```bash
./gradlew :sjf4j:test --tests "org.sjf4j.models.ConstructorTest" --tests "org.sjf4j.models.RecordStyleTest"
```

Run specific test:
```bash
./gradlew :sjf4j:test --tests "org.sjf4j.models.ConstructorTest.testSimpleConstructorWithJsonProperty"
```

## Notes

1. **Java 8 Compatibility**: While the test file is named "RecordStyleTest", it uses regular Java classes with record-style accessors (methods named like record components) because the project targets Java 8, which doesn't support Java records.

2. **Immutability**: Both test suites use final fields and constructors to ensure immutability, which is a best practice for value objects.

3. **Logging**: All tests include logging statements to help with debugging and verification.

4. **Assertions**: All tests use JUnit 5 assertions to verify:
   - Object is not null
   - Field values match expected values
   - Collections have correct size and content
   - Nested objects are properly initialized
