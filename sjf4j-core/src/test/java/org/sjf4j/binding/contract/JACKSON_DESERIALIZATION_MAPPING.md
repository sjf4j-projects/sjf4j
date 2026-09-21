# Jackson deserialization source map

This is a source-derived map, not a claim of Jackson compatibility. `Structural
source` means the named Jackson method was checked in `../jackson-databind`, but
the SJF4J test uses the stated SJF4J API and deliberately tests only the adapted
behavior.

## Mapped contract methods

| SJF4J contract method | Jackson source method | Relationship |
| --- | --- | --- |
| `JDKScalarsDeserializationContract#testBooleanWrapper` | `jdk/JDKScalarsDeserTest#testBooleanWrapper` | Direct scalar input subset |
| `#testCharacterWrapper` | `jdk/JDKScalarsDeserTest#testCharacterWrapper` | Direct one-character-string subset |
| `#testIntegerAndLongWrapper` | `jdk/JDKScalarsDeserTest#testIntWrapper`, `#testLongWrapper` | Direct native-number subset |
| `#testNullForPrimitivesDefault` | `jdk/JDKScalarsDeserTest#testNullForPrimitivesDefault` | Direct JSON-null field contract; currently exposed failure |
| `#testNullForPrimitiveArrays` | `jdk/JDKScalarsDeserTest#testNullForPrimitiveArrays` | Direct JSON-null array contract; currently exposed failure |
| `CollectionDeserializationContract#testUntypedList` | `jdk/CollectionDeserializationTest#testUntypedList` | Direct untyped-list subset |
| `#testExactStringCollection` | `jdk/CollectionDeserializationTest#testExactStringCollection` | Direct `ArrayList` target |
| `#testSet` | `jdk/CollectionDeserializationTest#testAbstractListAndSet` | Structural: SJF4J requests `Set`, where Jackson requests `AbstractSet` |
| `#testDeque` | `jdk/CollectionDeserializationTest#testJava6Types` | Direct `Deque` target |
| `MapDeserializationContract#testExactStringIntMap` | `jdk/MapDeserializationTest#testExactStringIntMap` | Structural: SJF4J requests `Map`, where Jackson requests `HashMap` |
| `#testGenericStringIntMap` | `jdk/MapDeserializationTest#testGenericStringIntMap` | Direct typed-map values |
| `#testUntypedMap2` | `jdk/MapDeserializationTest#testUntypedMap2` | Structural: SJF4J requests `Map`, where Jackson requests `HashMap` |
| `#testUntypedMap3` | `jdk/MapDeserializationTest#testUntypedMap3` | Direct nested untyped-map subset |
| `#testExactTreeMap` | `jdk/MapDeserializationTest#testExactStringStringMap` | Direct `TreeMap` target |
| `#testTypedNonStringKeys` | `jdk/MapDeserializationTest#testIntBooleanMap` | Structural: SJF4J requests `Map`, where Jackson requests `HashMap` |
| `DateDeserializationContract#testDateUtil` | `jdk/DateDeserializationTest#testDateUtil` | Direct default root numeric `Date` input |
| `#testCalendarAsNumber` | `jdk/DateDeserializationTest#testCalendar` | Direct default root numeric `Calendar` timestamp |
| `EnumDeserializationContract#testSimple` | `enums/EnumDeserializationTest#testSimple` | Structural: named and null inputs only; numeric/invalid branches are not included |
| `#testComplexEnum` | `enums/EnumDeserializationTest#testComplexEnum` | Structural: fixed-token read instead of Jackson serialization round trip |
| `access/UnknownPropertyDeserializationContract#testUnknownHandlingIgnoreWithFeature` | `filter/UnknownPropertyDeserTest#testUnknownHandlingIgnoreWithFeature` | Structural: SJF4J ignores unknown fields by default, without a feature toggle |
| `access/UnknownPropertyDeserializationContract#testClassWithIgnoreUnknown` | `filter/UnknownPropertyDeserTest#testClassWithIgnoreUnknown` | Structural: SJF4J has no class-level ignore annotation |
| `PropertyAliasDeserializationContract#testSimpleAliases` | `deser/PropertyAliasTest#testSimpleAliases` | Structural: `NodeProperty` aliases replace `JsonAlias` |
| `#testAliasDeserializedToLastMatchingKeyAscendingKeys` | `deser/PropertyAliasTest#testAliasDeserializedToLastMatchingKey_ascendingKeys` | Structural: SJF4J aliases retain input-order overwrite |
| `#testNoAliasNameInSerialization` | `deser/PropertyAliasTest#testNoAliasNameInSerialization` | Structural: tests SJF4J primary output name, not Jackson's ignored-getter interaction |
| `#testSnakeCaseWithOneArg` | `creators/CreatorWithNamingStrategyTest#testSnakeCaseWithOneArg` | Structural: SJF4J applies `NodeObject` naming to a field, not a creator parameter |
| `creator/CreatorDeserializationContract#testRequiredAnnotatedParam` | `creators/CreatorPropertyConstraintsTest#testRequiredAnnotatedParam` | Structural: missing primitive gets its Java default because `NodeProperty` has no required flag |
| `creator/CreatorDeserializationContract#testCreatorArgumentsInJsonOrder` | `creators/CreatorPropertyConstraintsTest#testRequiredAnnotatedParam` | Structural: SJF4J out-of-order creator arguments |
| `creator/CreatorDeserializationContract#testCreatorParameterAlias` | `deser/PropertyAliasTest#testSimpleAliases` | Structural: `NodeProperty` creator alias |
| `creator/CreatorDeserializationContract#testAliasInFactoryMethod` | `deser/PropertyAliasTest#testAliasInFactoryMethod` | Structural: `NodeCreator` factory alias |
| `creator/CreatorDeserializationContract#testMissingCreatorReferenceArgument` | `creators/CreatorPropertyConstraintsTest#testRequiredGloballyParam` | Structural: no SJF4J global missing-creator-property configuration |
| `creator/PropertyBasedCreatorDeserializationContract#constructorCreatorAcceptsOutOfOrderPropertiesAndThenCallsSetter` | `creators/TestCreators#testConstructorAndProps` | Structural: `NodeCreator`/`NodeProperty` constructor followed by setter binding |
| `creator/PropertyBasedCreatorDeserializationContract#constructorCreatorAcceptsArrayPropertiesAndThenCallsSetters` | `creators/TestCreators#testFactoryAndProps` | Structural: source method's constructor creator followed by setter binding |
| `creator/PropertyBasedCreatorDeserializationContract#absentPrimitiveCreatorParameterUsesJavaDefault` | `creators/CreatorNullPrimitivesTest#testCreatorAbsentPrimitiveShouldDefault` | Structural: absent creator primitive/default reference values |
| `creator/PropertyBasedCreatorDeserializationContract#absentCreatorParametersUseJavaDefaults` | `creators/CreatorNullPrimitivesTest#testRequiredNonNullParam` | Structural: exact default-configuration `{}` branch; absent reference and primitive creator parameters become `null` and `0` |
| `creator/DelegatingCreatorDeserializationContract#delegatingIntegerCreatorReceivesScalar` | `creators/DelegatingCreatorsTest#testIntegerDelegate` | Structural expected Jackson behavior; retained failure because SJF4J has no delegating creator mode |
| `creator/DelegatingCreatorDeserializationContract#delegatingListCreatorReceivesArray` | `creators/DelegatingArrayCreatorsTest#testDelegatingArray1804` | Structural expected Jackson behavior; retained failure because SJF4J has no delegating creator mode |
| `creator/DelegatingCreatorDeserializationContract#delegatingMapCreatorReceivesObject` | `creators/DelegatingCreatorsTest#testIssue465` | Structural expected Jackson behavior; retained failure because SJF4J has no delegating creator mode |
| `creator/FactoryCreatorDeserializationContract#staticFactoryCreatesPropertyBasedValue` | `creators/TestCreators#testSimpleFactory` | Structural: public SJF4J static property factory |
| `creator/FactoryCreatorDeserializationContract#staticFactoryIsUsedInsteadOfSameTypedConstructor` | `creators/SingleArgCreatorTest#testExplicitFactory660a` | Structural: scalar input selects the public single-argument static factory over the same-typed constructor |
| `testbench/RecordTest#creatorRecordAbsentPrimitivesUseJavaDefaults` | `creators/CreatorNullPrimitivesTest#testRecordAbsentPrimitivesShouldDefault` | Direct record target/default-value behavior |
| `ValueCodecDeserializationContract#testRootInterfaceUsing` | `deser/ValueAnnotationsDeserTest#testRootInterfaceUsing` | Structural: `NodeValue` replaces `JsonDeserialize(using)` |
| `#testValueCodecProperty` | `deser/ValueAnnotationsDeserTest#testRootInterfaceUsing` | Structural: adapts root dispatch to a field |
| `#testValueCodecList` | `deser/ValueAnnotationsDeserTest#testRootMapAsOld` | Structural: source's list root becomes a `NodeValue` list codec |
| `#testValueCodecMap` | `deser/ValueAnnotationsDeserTest#testRootListAsOld` | Structural: source's map root becomes a `NodeValue` map codec |
| `beans/BeanDeserializationContract#readsAllOrdinaryPojoPropertiesAtTheRoot` | `bean/BeanDeserializerVanillaTest#allKnownProperties` | Direct root POJO field/setter/container binding |
| `#preservesJavaDefaultsForAnEmptyRootObject` | `bean/BeanDeserializerVanillaTest#emptyObject` | Direct empty root object/default-field behavior |
| `#bindsPublicFields` | `BeanPropertyDeserTest#testSimpleAutoDetect` | Direct focused public-field binding |
| `#bindsJavaBeanSetters` | — | Retained SJF4J JavaBean setter coverage |
| `#bindsPrimitiveJavaBeanSetters` | — | Retained SJF4J primitive `setAge(int)` coverage formerly covered by `FieldBinderTest` |
| `#skipsUnknownStructuredPropertiesAndContinuesBinding` | `bean/BeanDeserializerVanillaTest#unknownPropertiesInterleaved` | Direct default unknown-value skipping and continuation |
| `#readsNullAtTheRootForAPojoTarget` | `NullHandlingDeserTest#testNull` | Structural: retained SJF4J root-null POJO behavior; the source does not bind a null root POJO |
| `#rejectsAnAbstractRootPojoTarget` | `bean/BeanDeserializerTest#testAbstractFailure` | Direct retained source-derived failure: Jackson rejects the abstract target, while SJF4J leaks `InstantiationError` outside the ordinary binding exception family |

`JDKScalarsDeserializationContract#testBigNumberRoots`,
`ValueCodecDeserializationContract#testNullValueCodec`, and every method in
`RetainedValueCodecDeserializationContract` are retained SJF4J coverage with no
Jackson method attribution. The retained codec class covers Boolean and Number
`NodeValue` dispatch at roots and fields.

`DynamicPropertyDeserializationContract` has no Jackson mapping: its first two
methods test `NodeBinding(readDynamic/writeDynamic = false)` dynamic storage and
its last two test `StreamingContext` null inclusion during serialization.

`CollectionDeserializationContract#testList`, `#testQueue`, and
`#testConcreteCollections`, plus
`MapDeserializationContract#testConcreteStringMaps`, are retained SJF4J
default-type coverage with no Jackson source attribution.

## Attempted but unsupported source cases

| Jackson source method | Missing SJF4J support / reason |
| --- | --- |
| `creators/CreatorPropertyConstraintsTest#testRequiredViaParameter2591` | `NodeProperty` has no required-property flag. |
| `deser/PropertyAliasTest#testAliasWithPolymorphic` | Jackson wrapper polymorphism is outside this batch. |
| `deser/PropertyAliasTest#testAliasOnRecordUpdateWithIgnoredGetter` | SJF4J has no reader-for-updating equivalent. |

The four JDK entries formerly listed here are now deferred in the JDK batch
inventory below. When their default Java target types are covered, current
SJF4J differences are deliberately exposed by the corresponding JDK contract
tests rather than being treated as exclusions.

## JDK batch 1 status (2026-09-18)

The inventory below covers all **404** `@Test` methods in the 31 files in
`jackson-databind/src/test/java/tools/jackson/databind/deser/jdk`.  `remaining`
means every `@Test` method in that source file other than the methods named in
the preceding columns; it is an exhaustive classification, not an omission.
The count is **41 migrated-direct**, **4 migrated-structural**, **250 deferred
to a later batch**, and **109 annotation/config/module/parser-specific skips**.
The following row audit is the accounting for the source-method enumeration in
the table: each tuple is `(direct, structural, deferred, skipped)` and sums to
the number of `@Test` methods in that row.

```
ArrayBlockingQueueDeserTest                 (0, 0, 11, 0)
ArrayDeserializationTest                    (11, 0, 5, 5)
Base64DecodingTest                          (0, 0, 2, 0)
BigNumbersDeserTest                         (0, 0, 15, 0)
ByteBufferDeserializerTest                  (0, 0, 7, 0)
ClassDeserNoStaticInitTest                  (0, 0, 0, 2)
CollectionDeserializationTest               (3, 1, 9, 9)
CustomMapKeyDeserializationTest             (0, 0, 0, 5)
DateDeserializationTest                     (2, 0, 26, 6)
DateDeserializationTZTest                   (0, 0, 6, 5)
JavaLangObjectDeserializationTest           (0, 0, 31, 0)
JavaUtilCollectionsTypesTest                (0, 0, 0, 16)
JavaUtilPropertiesDeserializationTest       (0, 0, 1, 0)
JDK7TypesTest                               (1, 0, 2, 4)
JDKAtomicTypesDeserTest                     (4, 0, 4, 15)
JDKNumberDeserTest + JDKNumberLeniencyTest
  + JDKScalarsDeserTest                     (6, 0, 45, 11)
JDKStringLikeTypeDeserTest                  (9, 0, 6, 0)
LocaleDeserializationTest                   (1, 0, 14, 4)
MapDeserializationTest                      (3, 3, 16, 5)
MapDeserializerCachingTest + MapRawWithGeneric2846Test
                                             (0, 0, 2, 0)
MapEntryDeserializationTest                 (0, 0, 14, 0)
MapKeyDeserializationTest                   (0, 0, 10, 6)
NullContentHandling5165Test                 (0, 0, 0, 10)
ObjectArrayDeserArrayStoreExc5646Test       (0, 0, 1, 0)
StackTraceElementDeserTest                  (0, 0, 12, 5)
ThreadGroupDeserTest                        (0, 0, 2, 0)
UUIDDeserializationTest                     (1, 0, 7, 0)
VoidValuedPropertiesDeserializationTest     (0, 0, 1, 1)
TOTAL                                       (41, 4, 250, 109)
```

| Jackson source | Migrated direct | Migrated structural | Deferred to later batch | Skipped only for annotation/config/module/parser-specific behavior |
| --- | --- | --- | --- | --- |
| `ArrayBlockingQueueDeserTest` | — | — | all 11 methods (dedicated bounded-queue construction batch) | — |
| `ArrayDeserializationTest` | `testUntypedArray`, `testIntegerArray`, `testStringArray`, `testCharArray`, `testBooleanArray`, `testByteArrayAsNumbers`, `testShortArray`, `testIntArray`, `testLongArray`, `testDoubleArray`, `testFloatArray` | — | `testUntypedArrayOfArrays`, `testByteArrayAsBase64`, `testByteArraysAsBase64`, `testByteArraysWith763`, `testBeanArray` | `testFromEmptyString`, `testFromEmptyString2`, `testSingleStringToPrimitiveArray`, `testByteArrayTypeOverride890`, `testCustomDeserializers` |
| `Base64DecodingTest` | — | — | `testInvalidBase64`, `testBase64ViaWrapperByteArray` | — |
| `BigNumbersDeserTest` | — | — | all 15 methods (numeric coercion/key batch) | — |
| `ByteBufferDeserializerTest` | — | — | all 7 methods (ByteBuffer batch) | — |
| `ClassDeserNoStaticInitTest` | — | — | — | `classValueDoesNotTriggerStaticInitializer`, `polymorphicInstantiationTriggersStaticInitializer` |
| `CollectionDeserializationTest` | `testUntypedList`, `testExactStringCollection`, `testJava6Types` | `testAbstractListAndSet` | `testHashSet`, `testArrayBlockingQueue`, `testIterableWithStrings`, `testIterableWithBeans`, `testArrayIndexForExceptions1`, `testArrayIndexForExceptions2`, `testArrayIndexForExceptions3`, `testNullsWithTreeSet`, `testSingletonCollections` | `testCustomDeserializer`, `testImplicitArrays`, `testFromEmptyString`, `testWrapExceptions`, `testWrapExceptions3068`, `testUnmodifiableSet`, `testCustomNumberCollectionDeserialize5522`, `testCustomStringCollectionDeserialize5522`, `testStringCollectionDeserializeInField5522` |
| `CustomMapKeyDeserializationTest` | — | — | — | all 5 methods (custom key serializer/deserializer or annotation) |
| `DateDeserializationTest` | `testDateUtil`, `testCalendar` | — | `testDateUtilWithStringTimestamp`, `testSignedStringTimestampViaObjectMapper`, `testDateUtilRFC1123`, `testDateUtilRFC1123OnNonUSLocales`, `testDateUtilISO8601`, `testISO8601PartialMilliseconds`, `testISO8601FractionalTimezoneOffset`, `testISO8601FractSecondsLong`, `testISO8601MissingSeconds`, `testDateUtilISO8601NoTimezone`, `testDateUtilISO8601NoTimezoneNonDefault`, `testFormatAndCtors1722`, `testDateUtilISO8601NoMilliseconds`, `testDateUtilISO8601JustDate`, `testDatesWithEmptyStrings`, `test8601DateTimeNoMilliSecs`, `testTimeZone`, `testDateAsInteger`, `testDateAsIntegerAmbiguous`, `testDateAsNumber`, `testDateEndingWithZNonDefTZ1651`, `testContextTimezone`, `testLenientJDKDateTypes`, `testInvalidFormat`, `testDateRoundTripWithMaxValue`, `testDateRoundTripWithMinValue` | `testCustom`, `testCustomDateWithAnnotation`, `testCustomCalendarWithAnnotation`, `testCustomCalendarWithTimeZone`, `testCalendarArrayUnwrap`, `testLenientJDKDateTypesViaGlobal` |
| `DateDeserializationTZTest` | — | — | `testDateUtilISO8601_Timezone`, `testDateUtilISO8601_DateTimeMillis`, `testDateUtilISO8601_DateTime`, `testDateUtilISO8601_Date`, `testDateUtil_Numeric`, `testWithTimezones1153` | `testDateUtil_Annotation`, `testDateUtil_Annotation_PatternAndLocale`, `testDateUtil_Annotation_TimeZone`, `testDateUtil_customDateFormat_withoutTZ`, `testDateUtil_customDateFormat_withTZ` |
| `JavaLangObjectDeserializationTest` | — | — | all 31 methods (untyped object/numeric policy batch) | — |
| `JavaUtilCollectionsTypesTest` | — | — | — | all 16 methods (default typing) |
| `JavaUtilPropertiesDeserializationTest` | — | — | `testReadProperties` | — |
| `JDK7TypesTest` | `testPathRoundTrip` | — | `testRejectNonFileSchemes`, `testAllowedSchemeCaseInsensitive` | `testCustomAllowedSchemes`, `testAllowedSchemeWithNoProvider`, `testNullAllowedSchemes`, `testPolymorphicPath` |
| `JDKAtomicTypesDeserTest` | `testAtomicBoolean`, `testAtomicInt`, `testAtomicLong`, `testAtomicReference` | — | `testAtomicLongFromStringAboveIntRange`, `testAtomicLongFromFloatAboveIntRange`, `testNullValueHandling`, `testNullWithinNested` | all remaining 15 methods (annotations, mapper inclusion/configuration, polymorphism, update, or custom deserializer) |
| `JDKNumberDeserTest`, `JDKNumberLeniencyTest`, `JDKScalarsDeserTest` | `JDKScalarsDeserTest#testBooleanWrapper`, `#testCharacterWrapper`, `#testIntWrapper`, `#testLongWrapper`, `#testNullForPrimitivesDefault`, `#testNullForPrimitiveArrays` | — | all remaining default-input methods | all remaining feature/override methods |
| `JDKStringLikeTypeDeserTest` | `testCharset`, `testCurrency`, `testFile`, `testCharSequence`, `testPattern`, `testStringBuilder`, `testStringBuffer`, `testURI`, `testURL` | — | `testClass`, `testClassWithParams`, `testInetAddress`, `testInetAddressNoDNSLookup`, `testInetAddressNonAsciiDigits`, `testInetSocketAddress` | — |
| `LocaleDeserializationTest` | `testLocale` | — | all locale-format and fuzz methods | `testLocaleWithFeatureDisabled`, `testLocaleWithFeatureEnabled`, `testLocaleVarargMultiple5231`, `testLocaleVarargSingle5231` |
| `MapDeserializationTest` | `testUntypedMap3`, `testGenericStringIntMap`, `testExactStringStringMap` | `testUntypedMap2`, `testExactStringIntMap`, `testIntBooleanMap` | `testBigUntypedMap`, `testSpecialMap`, `testGenericMap`, `testAbstractMapDefault`, `testEnumMap`, `testMapWithEnums`, `testDateMap`, `testCalendarMap`, `testUUIDKeyMap`, `testLocaleKeyMap`, `testCurrencyKeyMap`, `testClassKeyMap`, `testcharSequenceKeyMap`, `testMapError`, `testNoCtorMap`, `testCanDeserializeMap2757` | `testFromEmptyString`, `testMapUpdate`, `testEnumPolymorphicSerializationTest`, `testKeyWithCreator`, `testMapWithDeserializer` |
| `MapDeserializerCachingTest`, `MapRawWithGeneric2846Test` | — | — | all methods (cache/raw-generic batch) | — |
| `MapEntryDeserializationTest` | — | — | all 14 methods (Map.Entry batch) | — |
| `MapKeyDeserializationTest` | — | — | `testBooleanMapKeyDeserialization`, `testByteMapKeyDeserialization`, `testShortMapKeyDeserialization`, `testIntegerMapKeyDeserialization`, `testLongMapKeyDeserialization`, `testFloatMapKeyDeserialization`, `testDoubleMapKeyDeserialization`, `testByteArrayMapKeyDeserialization`, `testDeserializeInvalidKey`, `testNormalizeKey` | all remaining 6 creator/annotation key methods |
| `NullContentHandling5165Test` | — | — | — | all 10 methods (per-property null configuration) |
| `ObjectArrayDeserArrayStoreExc5646Test` | — | — | `testArrayStoreExceptionInObjectArrayDeserializer` | — |
| `StackTraceElementDeserTest` | — | — | all default StackTraceElement methods | `testSingleValueArrayUnwrap`, `testDeserWithMixInPropertyNames`, `testRoundTripWithMixIn`, `testStandardDeserUnaffectedByMixInFeature`, `testStackTraceElementWithCustom` |
| `ThreadGroupDeserTest` | — | — | `deserThreadGroupFromEmpty`, `roundtripThreadGroup` | — |
| `UUIDDeserializationTest` | `testUUID` | — | `testUUIDInvalid`, `testUUIDAux`, `testCanDeserializeUUIDFromString`, `testCanDeserializeUUIDFromBase64`, `testCanDeserializeUUIDFromBase64WithoutPadding`, `testCanDeserializeUUIDFromBase64Url`, `testCanDeserializeUUIDFromBase64UrlWithoutPadding` | — |
| `VoidValuedPropertiesDeserializationTest` | — | — | `testVoidBeanDeserialization` | `testVoidBeanSerialization` |

`Optional` and `java.time.Instant` are intentionally represented by
`JDKDefaultUnsupportedTypeDeserializationContract`: Jackson's default mapper
rejects their non-null string inputs without `jackson-datatype-jdk8` and
`jackson-datatype-jsr310`, respectively.  They are failure tests rather than
being silently classified as SJF4J limitations.

## Creator batch 2 status (2026-09-18)

This is the exhaustive inventory of the **36 source files / 226 `@Test`
methods** in `jackson-databind/src/test/java/tools/jackson/databind/deser/creators`.
The tuple in every row is `(direct, structural, deferred, skipped)` and sums to
that source file's exact `@Test` count. `Skipped` is used only for Jackson
annotation, mapper configuration, module, parser/token-buffer, update, object
id, polymorphic, or internal implementation behavior for which there is no
public SJF4J equivalent. `Deferred` remains accountable for a later explicit
creator sub-batch; it is not an exclusion.

The named migrated methods are the per-source audit trail. `remaining` means
the exact number of other `@Test` methods in that source file, partitioned by
the tuple, rather than an unaccounted omission. The structural tests use the
public `NodeCreator` / `NodeProperty` API where Jackson uses annotations.

| Jackson creator source | Tuple | Migrated source methods / disposition |
| --- | --- | --- |
| `SingleArgCreatorTest` | `(0, 1, 2, 5)` | structural: `testExplicitFactory660a` (scalar input selects the explicit single-argument static factory); remaining: 2 deferred scalar/creator-selection cases, 5 annotation-introspector or explicit-mode cases |
| `MultipleCreatorsTest` | `(0, 0, 3, 0)` | all 3 deferred: mixed property/delegating creator selection |
| `DelegatingArrayCreatorsTest` | `(0, 1, 4, 0)` | structural: `testDelegatingArray1804`; remaining 4 deferred: generic bags, conflicting delegates, and default typing |
| `FactoryCreatorTypeBinding2894Test` | `(0, 0, 2, 0)` | all 2 deferred: generic static factory type binding |
| `DisablingCreatorsTest` | `(0, 0, 0, 1)` | `testDisabling`: annotation/config creator disabling |
| `JsonCreatorModeForEnum3566Test` | `(0, 0, 0, 8)` | all 8: `JsonCreator.Mode` enum annotation behavior |
| `CreatorWithMultipleUnwrapped917Test` | `(0, 0, 0, 3)` | all 3: `JsonUnwrapped` annotation behavior |
| `ConstructorDetectorTest` | `(0, 0, 0, 22)` | all 22: `ConstructorDetector`, visibility, or mapper configuration behavior |
| `NoParamsCreator5318Test` | `(0, 1, 0, 4)` | structural: `creatorDetectionWithNoParamsCtor`; remaining 4: explicit annotation, ignore, feature, or custom introspector behavior |
| `DelegatingCreatorWithUnsupportedMap3216Test` | `(0, 0, 0, 2)` | both methods: `JsonIgnore` handling |
| `EnumCreatorTest` | `(0, 0, 21, 0)` | all 21 deferred: enum creator/coercion sub-batch |
| `SingleImmutableFieldCreatorTest` | `(0, 1, 4, 0)` | structural: `testSetterlessPropertyWithJsonCreator`; remaining 4 deferred immutable/setterless combinations |
| `CreatorImplicitNameTest` | `(0, 0, 5, 0)` | all 5 deferred: implicit parameter-name and rename sub-batch |
| `TestCustomValueInstDefaults` | `(0, 0, 0, 7)` | all 7: custom `ValueInstantiator` module behavior |
| `MultiArgConstructorTest` | `(0, 1, 0, 3)` | structural: `testMultiArgVisible`; remaining 3: custom introspector, visibility configuration, or large-creator configuration |
| `JsonCreatorNoArgs4777Test` | `(0, 0, 0, 1)` | module creator-detection behavior |
| `JsonCreatorDefaultAndPropertiesCtors5840Test` | `(0, 2, 0, 0)` | structural: `testDeserWithPropertiesCreator5840`, `testDeserWithEmptyJson5840` |
| `CreatorWithNamingStrategyTest` | `(0, 1, 0, 2)` | structural: `testSnakeCaseWithOneArg` (also mapped above in the generic property contract); remaining 2: mapper naming strategy configuration |
| `PolymorphicPropsCreatorsTest` | `(0, 0, 5, 0)` | all 5 deferred: polymorphic property creators |
| `CreatorNullPrimitivesTest` | `(1, 2, 2, 3)` | direct: `testRecordAbsentPrimitivesShouldDefault` in `sjf4j-testbench/RecordTest`; structural: `testCreatorAbsentPrimitiveShouldDefault`, `testRequiredNonNullParam` default-input subset; deferred: nested/record-null coverage; skipped: `testCreatorNullPrimitive`, `testCreatorNullPrimitiveInNestedObject`, `defaultingWithNull2977` feature/introspector cases |
| `InnerClassCreatorTest` | `(0, 0, 3, 0)` | all 3 deferred: non-static inner-class creators |
| `AnySetterForCreator562Test` | `(0, 0, 0, 10)` | all 10: `JsonAnySetter` annotation/config behavior |
| `ImplicitParamsForCreatorTest` | `(0, 0, 0, 4)` | all 4: implicit-name, naming-strategy, or `JsonValue` annotation behavior |
| `CreatorWithUnwrapped2369Test` | `(0, 0, 0, 2)` | both methods: `JsonUnwrapped` annotation behavior |
| `DelegatingCreatorsTest` | `(0, 2, 8, 6)` | structural: `testIntegerDelegate`, `testIssue465`; remaining 8 deferred delegate combinations and 6 injection/token-buffer/polymorphic/explicit-mode cases |
| `BeanDeserializerFactory4920Test` | `(0, 0, 0, 1)` | abstract-type factory internals |
| `TestCreators2` | `(0, 1, 13, 0)` | structural: `testSimpleConstructor`; remaining 13 deferred constructor choice, duplicate names, abstract factory, and property-creator cases |
| `CreatorPropertyReaderForUpdating5281Test` | `(0, 0, 0, 2)` | both methods: reader-for-updating behavior |
| `CreatorPropertyConstraintsTest` | `(0, 2, 2, 1)` | structural: `testRequiredAnnotatedParam`, `testRequiredGloballyParam` (both also mapped above in `creator/CreatorDeserializationContract`); remaining 2 deferred fallback/read-only cases and 1 required-property annotation case |
| `CreatorReturningNullTest` | `(0, 0, 0, 8)` | all 8: custom deserializer/module, any-setter, or creator-return-null internals |
| `TestCreators3` | `(0, 0, 6, 2)` | 6 deferred creator value/constructor cases; 2 serialization/annotation-specific cases |
| `DelegatingCreatorImplicitNamesTest` | `(0, 0, 1, 3)` | 1 deferred implicit delegating-name case; 3 annotation/introspector cases |
| `TestCreators` | `(0, 3, 9, 8)` | structural: `testSimpleFactory`, `testConstructorAndProps`, `testFactoryAndProps`; remaining 9 deferred ordinary creator/map/selection cases and 8 annotation, mix-in, parser, or multi-creator cases |
| `CreatorParamShadowedByReadOnly5975Test` | `(0, 0, 0, 4)` | all 4: read-only/write-only annotation and serialization behavior |
| `CreatorWithObjectIdTest` | `(0, 0, 0, 2)` | both methods: object identity annotations |
| `DelegatingCreatorAnnotationsTest` | `(0, 0, 0, 3)` | all 3: `JsonDeserialize` / custom deserializer annotations |
| **TOTAL** | **`(1, 18, 90, 117)`** | **226 methods** |

Batch-2 tests are in `binding/creator` (with the simple-binder adapter retained
in `binding/simple` for the shared `CreatorDeserializationContract`). Current
default differences deliberately retained as failing tests are
`DelegatingCreatorsTest#testIntegerDelegate`,
`DelegatingArrayCreatorsTest#testDelegatingArray1804`, and
`DelegatingCreatorsTest#testIssue465`: SJF4J has no public delegating creator
mode, so scalar, array, and object delegate inputs fail before the creator is
called. `SingleArgCreatorTest#testExplicitFactory660a` also fails: SJF4J does
not select a static `NodeCreator` factory for scalar input. Record coverage is
kept in `sjf4j-testbench` as required.

## Filter/access batch 3 status (2026-09-18)

This is the exhaustive inventory of all **23 source files / 147 `@Test`
methods** in `jackson-databind/src/test/java/tools/jackson/databind/deser/filter`.
The tuple is `(direct, structural, deferred, skipped)` and every row sums to
the source file's test count. There are **no direct ports**, **6 structural
ports**, **1 deferred default case**, and **140 skips**. A skip is used only
for Jackson annotation/configuration, problem-handler, parser filter, update,
record update, polymorphic, or internal/cache behavior; unported default input
behavior is deferred instead.

| Jackson filter source | Tuple | Exact disposition |
| --- | --- | --- |
| `AnySetterIgnoreProperties6115Test` | `(0,0,0,9)` | all 9: `JsonAnySetter`, `JsonIgnoreProperties`, record-update, unwrapped, or fail-on-ignored configuration |
| `DeserializationProblemHandlerTest` | `(0,0,0,32)` | all 32: `DeserializationProblemHandler`, invalid type-id, parser/token, coercion-handler, or context-handler configuration |
| `IgnorePropertiesCaseInsensitive5962Test` | `(0,0,0,2)` | `test5962_negativeControl_withoutCaseInsensitivity`, `test5962_caseInsensitiveRebuildRestoresIgnoredProperty`: mapper case-insensitive ignore configuration |
| `IgnoreUnknownPropertyUsingPropertyBasedTest` | `(0,0,0,2)` | both: `JsonIgnoreProperties` with `JsonAnySetter` value capture or `JsonUnwrapped` |
| `JsonIgnoreCreatorProp1317Test` | `(0,0,0,1)` | `testThatJsonIgnoreWorksWithConstructorProperties`: `JsonIgnore` creator annotation |
| `JsonIgnoreProperties1622Test` | `(0,0,0,7)` | all 7: ignored-property annotation cache/race, builder, nested, or record behavior |
| `JsonIgnorePropertiesDeserTest` | `(0,1,0,8)` | structural: `testIssue426` → `access/AccessDeserializationContract#testNodeIgnoreLeavesTheExistingFieldValueUntouched`; remaining 8: `JsonIgnoreProperties` annotations or config overrides |
| `JsonIgnorePropsWithCreatorTest` | `(0,0,0,2)` | both: class/field `JsonIgnoreProperties` creator annotation |
| `NullConversionsAsEmptyPOJO2572Test` | `(0,0,0,3)` | all 3: `JsonSetter(nulls = AS_EMPTY)` configuration |
| `NullConversionsForContentTest` | `(0,0,0,18)` | all 18: `JsonSetter` content-null `FAIL`/`AS_EMPTY`/`SKIP` configuration |
| `NullConversionsForEnumsTest` | `(0,0,0,5)` | all 5: enum `JsonSetter` null-content configuration |
| `NullConversionsGenericTest` | `(0,0,0,5)` | all 5: `JsonSetter(nulls = AS_EMPTY)` and empty-string feature configuration |
| `NullConversionsPojoTest` | `(0,0,0,6)` | all 6: `JsonSetter` null conversion configuration |
| `NullConversionsSkipTest` | `(0,0,0,4)` | all 4: `JsonSetter(nulls = SKIP)` configuration |
| `NullConversionsViaCreator2458Test` | `(0,0,0,2)` | both: creator `JsonSetter(nulls = AS_EMPTY)` configuration |
| `NullSkip4441Test` | `(0,0,0,2)` | both: `JsonSetter(nulls = SKIP)` configuration |
| `NullSkipForCollections4309Test` | `(0,0,0,2)` | both: `JsonSetter` unknown enum/subtype configuration |
| `ParserFilterViaMapTest` | `(0,0,0,1)` | `testSimplePropertyExcludeFilter`: Jackson `TokenFilter` parser pipeline |
| `ReadOnlyDeserTest` | `(0,1,0,3)` | structural: `testReadOnlyProps95` → `access/AccessDeserializationContract#testGetterOnlyPropertyIsIgnoredWithoutConvertingItsValue`; remaining 3: `JsonProperty.Access` / fail-on-ignored configuration |
| `ReadOnlyListDeserTest` | `(0,1,0,3)` | structural: `testAccessReadOnly2118` → `#testReadDynamicRetainsOnlyTheRealDynamicEquivalent`; remaining 3 require `JsonProperty.Access` and/or `USE_GETTERS_AS_SETTERS` |
| `ReadOrWriteOnlyTest` | `(0,0,0,8)` | all 8: `JsonProperty.Access`, allow-getters, or `INVERSE_READ_WRITE_ACCESS` configuration |
| `RecursiveIgnorePropertiesTest` | `(0,0,0,5)` | all 5: recursive `JsonIgnoreProperties` annotations (including serialization) |
| `UnknownPropertyDeserTest` | `(0,3,1,10)` | structural: `testUnknownHandlingIgnoreWithFeature`, `testClassWithIgnoreUnknown`, `testIssue987` → `access/UnknownPropertyDeserializationContract`; deferred: `testUnknownHandlingDefault` — Jackson's default strict rejection has no SJF4J strict-unknown switch and is not annotation/config-only; skipped: `testUnknownHandlingIgnoreWithHandler`, `testUnknownHandlingIgnoreWithHandlerAndObjectReader`, `testWithClassIgnore`, `testClassIgnoreWithMap`, `testAnySetterWithFailOnUnknownDisabled`, `testUnwrappedWithFailOnUnknownDisabled`, `testClassWithUnknownAndIgnore`, `testPropertyIgnoral`, `testPropertyIgnoralWithClass`, `testPropertyIgnoralForMap` (handlers, `JsonIgnore*`, any-setter, unwrapped, or property-level ignore annotations) |
| **TOTAL** | **`(0,6,1,140)`** | **147 methods** |

The three `UnknownPropertyDeserTest` structural cases test SJF4J's documented
default of ignoring undeclared POJO members and, for `testIssue987`, continuing
past nested unknown values. `NodeIgnore`, getter-only properties, and
`NodeBinding(readDynamic = false)` are the only public SJF4J equivalents used
in this batch. The creator-unknown, bean-field/setter, public-field, and
duplicate-alias tests remain as explicitly uncited SJF4J structural coverage;
they are not included in the source tuple. There is no record-only filter test
with a public SJF4J record equivalent, so no testbench record test was added.

## Enum batch 4 status (2026-09-18)

This inventory covers all **19 source files / 150 `@Test` methods** in
`jackson-databind/src/test/java/tools/jackson/databind/deser/enums`.
The tuple is `(direct, structural, deferred, skipped)` and every row sums to
its source method count. Totals are **(10, 3, 5, 132)**. Direct cases are
Jackson's unconfigured name/null/ordinal/error/container behavior; current
SJF4J differences are intentionally left as failing contracts.  Structural
means the public `NodeValue` or containing-property `NodeProperty(aliases=...)`
API is used instead of a Jackson enum annotation.  Deferred cases are listed
explicitly and remain non-annotation default behavior; every other method in
each row is skipped only because it uses Jackson annotations, mapper/reader
features, modules, mix-ins, polymorphism, parser/serialization round trips,
custom deserializers, update, or internal behavior with no public equivalent.

| Jackson enum source | Tuple | Direct / structural / deferred methods (all other methods in this source are skip) |
| --- | --- | --- |
| `EnumDeserialization3638Test` | `(0,0,0,2)` | — |
| `EnumDeserializerJsonValue5271Test` | `(0,1,0,0)` | structural: `convertStringToEnum` (`NodeValue`) |
| `EnumDeserMixin2787Test` | `(0,0,0,11)` | — |
| `EnumSetDeserializationWithDefaultTyping4849Test` | `(0,0,0,2)` | — |
| `EnumAltIdTest` | `(0,0,0,17)` | — |
| `EnumNamingDeserializationTest` | `(0,0,0,19)` | — |
| `EnumAliasDeser2352Test` | `(0,1,0,2)` | structural: `testEnumWithAlias` (containing `NodeProperty` alias; enum-value aliases have no equivalent) |
| `EnumDeserFromIntJsonValueTest` | `(0,0,0,4)` | — |
| `EnumDeserializationFeatureOrderTest` | `(0,0,0,9)` | — |
| `EnumDefaultReadTest` | `(1,0,0,7)` | direct: `testWithoutCustomFeatures` |
| `EnumDeserNumberJsonProperty5330Test` | `(0,0,0,6)` | — |
| `EnumMapDeserializationTest` | `(1,0,1,17)` | direct: `testEnumMaps` → `EnumMap<TestEnum,String>`; deferred: `testUnknownKeyFailsWithHashMapByDefault` (plain typed-map key failure, separate map-key batch) |
| `EnumSetPolymorphicDeser4214Test` | `(0,0,0,1)` | — |
| `EnumWithNullToString4355Test` | `(0,0,0,1)` | — |
| `EnumDeserializationTest` | `(7,1,3,28)` | direct: `testSimple`, `testComplexEnum`, `testNumbersToEnums`, `testIndexAsString`, `testUnwrappedEnumException`, `testDoNotAllowUnknownEnumValuesAsMapKeysWhenReadAsNullDisabled`, `testEnumValuesCaseSensitivity`; structural: `testEnumsWithJsonValue` (`NodeValue`); deferred: `testAllowUnknownEnumValuesForEnumSets` (plain `EnumSet` null-content default), `testEnumFeature_EnumIndexAsKey` (plain enum-key index input), `testEnumReadFromEmptyString` (plain empty enum-name input); skipped `testSubclassedEnums`: enum-subclass-specific coverage has no migration |
| `EnumSameName4302Test` | `(1,0,0,1)` | direct: `testWrappedShouldWork`; skipped `testStandaloneShouldWork`: configured property naming plus serialization round trip |
| `EnumDeserDupName4409Test` | `(0,0,0,2)` | — |
| `EnumSetDeserializer5203Test` | `(0,0,0,3)` | — |
| `EnumDeserialization3369Test` | `(0,0,1,0)` | deferred: `testReadEnums3369` (plain enum factory result/null behavior; no enum `NodeCreator` scalar mode) |
| **TOTAL** | **`(10,3,5,132)`** | **150 methods** |

The moved `binding/enums/EnumDeserializationContract` covers the direct root,
array/list, enum-map-key, nested-POJO, null, case-sensitive, unknown text,
unknown numeric, and ordinal cases. Its `Map<TestEnum,TestEnum>` value case is
retained as uncited SJF4J coverage because this checkout has no precise plain
Jackson DEFAULT source for enum map values. It deliberately leaves the six
current simple-binder differences visible: numeric and quoted-numeric ordinal
binding, typed enum map keys/values, acceptance of case-insensitive enum map
keys, and rejection of unknown enum map keys. No source
in this batch has a plain record enum case with a public SJF4J equivalent, so
no testbench record test was added.

## Root and bean batch 5 status (2026-09-18)

This is the exhaustive inventory for the **26 root `deser/*.java` files (264
`@Test` methods) and 5 `deser/bean` files (22 methods): 286 source cases**.
The tuple is `(direct, structural, deferred, skipped)`.  It totals **(5, 8,
30, 243)**.  A deferred case is ordinary default behavior that needs a later
focused batch; a skip is exclusively Jackson annotation/configuration/module,
parser, update, polymorphic, cache/concurrency, error-reporting, or internal
behavior.  `remaining` is the exact set of methods in that file other than the
named methods.

| Source file | Tuple | Source-method disposition |
| --- | --- | --- |
| `AnnotationUsingTest` | `(0,0,0,7)` | all: `JsonDeserialize` annotation custom codecs |
| `AnySetterTest` | `(0,0,0,19)` | all: `JsonAnySetter`, ignore, unwrapped, creator, or polymorphic annotation behavior |
| `BasicAnnotationsTest` | `(0,0,0,12)` | all: annotation/visibility configuration |
| `BeanPropertyDeserTest` | `(1,0,0,17)` | direct: `testSimpleAutoDetect` → `beans/BeanDeserializationContract#bindsPublicFields`; remaining annotation, auto-detect, type-override, conflict, or getter-as-setter behavior |
| `CachingOfDeserTest` | `(0,0,0,4)` | all: deserializer caching internals |
| `CollectingErrorsTest` | `(0,0,0,31)` | all: parser/error collection and location reporting |
| `CreatorWithIgnoreProperties3355Test` | `(0,0,0,2)` | both: ignore-properties annotation |
| `CustomDeserializers4225NullCacheTest` | `(0,0,0,1)` | custom deserializer cache |
| `CustomDeserializersTest` | `(0,0,0,12)` | all: custom/delegating deserializers and parser context |
| `CustomValueInstantiatorsTest` | `(0,0,0,18)` | all: `ValueInstantiator` module behavior |
| `DeserConcurrencyTest` | `(0,0,0,1)` | concurrency/cache internals |
| `DeserFromNonBlockingTest` | `(0,0,0,4)` | non-blocking parser APIs |
| `DeserializerFactoryTest` | `(0,0,0,4)` | factory existence internals |
| `ErrorThrowingDeserializerTest` | `(0,0,0,2)` | custom/module error propagation |
| `FunctionalScalarDeserializer4004Test` | `(0,0,0,30)` | functional custom deserializer/configuration API |
| `GenericTypeDeserTest` | `(0,0,11,0)` | all 11 deferred: generic wrapper and single-value-array default behavior |
| `JacksonTypesDeserTest` | `(0,0,0,7)` | Jackson token stream, token buffer, and `JavaType` APIs |
| `MergePolymorphicTest` | `(0,0,0,5)` | merge/update plus polymorphic annotations |
| `NullHandlingDeserTest` | `(0,1,2,6)` | structural/retained: `testNull` → `beans/BeanDeserializationContract#readsNullAtTheRootForAPojoTarget` (the source does not bind a null root POJO); deferred: `testListOfNulls`, `testMapOfNulls`; remaining custom/any-setter/null-fail/polymorphic configuration |
| `PropertyAliasTest` | `(0,4,0,10)` | structural: `testSimpleAliases`, `testAliasInFactoryMethod`, `testAliasDeserializedToLastMatchingKey_ascendingKeys`, `testNoAliasNameInSerialization` → existing `PropertyAliasDeserializationContract`/creator contracts; remaining `JsonAlias`, ignore, polymorphic, record-update, and mapper-option behavior |
| `PropertyFilteringDeserTest` | `(0,0,0,9)` | filter/include annotation and forward-reference behavior |
| `StdValueInstantiatorsTest` | `(0,0,9,0)` | all 9 deferred: scalar constructor selection/coercion |
| `StructuralTypeDeserTest` | `(0,0,8,0)` | all 8 deferred: structural/cyclic/non-static-inner default targets |
| `UnwrappedCustomDeserCreator6001Test` | `(0,0,0,2)` | unwrapped custom deserializer annotation |
| `ValueAnnotationsDeserTest` | `(0,3,0,21)` | structural: `testRootInterfaceUsing`, `testRootListAsOld`, `testRootMapAsOld` → existing `ValueCodecDeserializationContract`; remaining `JsonDeserialize` override annotations |
| `WithoutParamNamesModule5314Test` | `(0,0,0,1)` | parameter-names module behavior |
| `bean/BeanDeserializerModifier4216Test` | `(0,0,0,1)` | module modifier lifecycle |
| `bean/BeanDeserializerModifier4356Test` | `(0,0,0,2)` | module modifier behavior |
| `bean/BeanDeserializerTest` | `(1,0,0,10)` | direct retained source-derived failure: `testAbstractFailure` → `beans/BeanDeserializationContract#rejectsAnAbstractRootPojoTarget`; Jackson rejects the abstract target, while SJF4J leaks `InstantiationError` outside ordinary binding exceptions; remaining deserializer-modifier modules |
| `bean/BeanDeserializerVanillaTest` | `(3,0,0,4)` | direct: `allKnownProperties`, `emptyObject`, `unknownPropertiesInterleaved` → `beans/BeanDeserializationContract`; remaining vanilla-fast-path, parser-delegate, and error-location internals |
| `bean/BeanPropertyMapTest` | `(0,0,0,1)` | property-map internal bounds behavior |
| **TOTAL** | **`(5,8,30,243)`** | **286 methods** |

The bean contracts are deliberately in `binding/beans`, with the simple-binder
adapter in `binding/simple`.  Root scalar and array cases already attributed to
the JDK batch are not duplicated here.  `FieldBinderTest`'s primitive JavaBean
setter coverage is retained by `BeanDeserializationContract#bindsPrimitiveJavaBeanSetters`;
it is not a `BeanPropertyDeserTest` port.  The unrelated alias contract no
longer labels that coverage as a `PropertyAliasTest` port.

`binding/oneof/OneOfIOTest` is retained **SJF4J semantics**, not a Jackson
mapping.  It covers `@OneOf` discriminator and JSON-type mappings, parent
discriminators before and after their property, fallback-null and failure
policies, dynamic unknown-field preservation, continuation, and list element
resolution.  Jackson `@JsonTypeInfo` and related polymorphic annotations are
therefore not counted as direct or structural batch-5 ports.
