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
| `#testExactStringCollection` | `jdk/CollectionDeserializationTest#testExactStringCollection` | Structural: SJF4J requests `List`, where Jackson requests `ArrayList` |
| `MapDeserializationContract#testExactStringIntMap` | `jdk/MapDeserializationTest#testExactStringIntMap` | Structural: SJF4J requests `Map`, where Jackson requests `HashMap` |
| `#testGenericStringIntMap` | `jdk/MapDeserializationTest#testGenericStringIntMap` | Direct typed-map values |
| `#testUntypedMap2` | `jdk/MapDeserializationTest#testUntypedMap2` | Direct untyped-map subset |
| `#testUntypedMap3` | `jdk/MapDeserializationTest#testUntypedMap3` | Direct nested untyped-map subset |
| `EnumDeserializationContract#testSimple` | `enums/EnumDeserializationTest#testSimple` | Structural: named and null inputs only; numeric/invalid branches are not included |
| `#testComplexEnum` | `enums/EnumDeserializationTest#testComplexEnum` | Structural: fixed-token read instead of Jackson serialization round trip |
| `UnknownPropertyDeserializationContract#testUnknownHandlingIgnoreWithFeature` | `filter/UnknownPropertyDeserTest#testUnknownHandlingIgnoreWithFeature` | Structural: SJF4J ignores unknown fields by default, without a feature toggle |
| `#testClassWithIgnoreUnknown` | `filter/UnknownPropertyDeserTest#testClassWithIgnoreUnknown` | Structural: SJF4J has no class-level ignore annotation |
| `PropertyAliasDeserializationContract#testSimpleAliases` | `deser/PropertyAliasTest#testSimpleAliases` | Structural: `NodeProperty` aliases replace `JsonAlias` |
| `#testAliasDeserializedToLastMatchingKeyAscendingKeys` | `deser/PropertyAliasTest#testAliasDeserializedToLastMatchingKey_ascendingKeys` | Structural: SJF4J aliases retain input-order overwrite |
| `#testNoAliasNameInSerialization` | `deser/PropertyAliasTest#testNoAliasNameInSerialization` | Structural: tests SJF4J primary output name, not Jackson's ignored-getter interaction |
| `#testSnakeCaseWithOneArg` | `creators/CreatorWithNamingStrategyTest#testSnakeCaseWithOneArg` | Structural: SJF4J applies `NodeBinding` naming to a field, not a creator parameter |
| `#testOrdinaryPojoFieldsAndJavaBeanAccessors` | `deser/PropertyAliasTest#testCaseInsensitiveAliases` | Structural: ordinary SJF4J field/setter binding only; no mapper case-insensitive setting |
| `CreatorDeserializationContract#testRequiredAnnotatedParam` | `creators/CreatorPropertyConstraintsTest#testRequiredAnnotatedParam` | Structural: missing primitive gets its Java default because `NodeProperty` has no required flag |
| `#testCreatorArgumentsInJsonOrder` | `creators/CreatorPropertyConstraintsTest#testRequiredAnnotatedParam` | Structural: SJF4J out-of-order creator arguments |
| `#testCreatorParameterAlias` | `deser/PropertyAliasTest#testSimpleAliases` | Structural: `NodeProperty` creator alias |
| `#testAliasInFactoryMethod` | `deser/PropertyAliasTest#testAliasInFactoryMethod` | Structural: `NodeCreator` factory alias |
| `#testMissingCreatorReferenceArgument` | `creators/CreatorPropertyConstraintsTest#testRequiredGloballyParam` | Structural: no SJF4J global missing-creator-property configuration |
| `ValueCodecDeserializationContract#testRootInterfaceUsing` | `deser/ValueAnnotationsDeserTest#testRootInterfaceUsing` | Structural: `NodeValue` replaces `JsonDeserialize(using)` |
| `#testValueCodecProperty` | `deser/ValueAnnotationsDeserTest#testRootInterfaceUsing` | Structural: adapts root dispatch to a field |
| `#testValueCodecList` | `deser/ValueAnnotationsDeserTest#testRootMapAsOld` | Structural: source's list root becomes a `NodeValue` list codec |
| `#testValueCodecMap` | `deser/ValueAnnotationsDeserTest#testRootListAsOld` | Structural: source's map root becomes a `NodeValue` map codec |

`JDKScalarsDeserializationContract#testBigNumberRoots`,
`ValueCodecDeserializationContract#testNullValueCodec`, and every method in
`RetainedValueCodecDeserializationContract` are retained SJF4J coverage with no
Jackson method attribution. The retained codec class covers Boolean and Number
`NodeValue` dispatch at roots and fields.

`DynamicPropertyDeserializationContract` has no Jackson mapping: its first two
methods test `NodeBinding(readDynamic/writeDynamic = false)` dynamic storage and
its last two test `StreamingContext` null inclusion during serialization.

## Attempted but unsupported source cases

| Jackson source method | Missing SJF4J support / reason |
| --- | --- |
| `jdk/CollectionDeserializationTest#testHashSet` | `EnumSet<E>` is not supported by `StreamingIO`; do not substitute integer `Set` de-duplication. |
| `jdk/CollectionDeserializationTest#testIterableWithStrings` | `Iterable<String>` fields are not supported by `StreamingIO`. |
| `jdk/CollectionDeserializationTest#testIterableWithBeans` | `Iterable<XBean>` fields are not supported by `StreamingIO`. |
| `jdk/MapDeserializationTest#testAbstractMapDefault` | `TypeRegistry` explicitly does not support `AbstractMap`. |
| `creators/CreatorPropertyConstraintsTest#testRequiredViaParameter2591` | `NodeProperty` has no required-property flag. |
| `deser/PropertyAliasTest#testAliasWithPolymorphic` | Jackson wrapper polymorphism is outside this batch. |
| `deser/PropertyAliasTest#testAliasOnRecordUpdateWithIgnoredGetter` | SJF4J has no reader-for-updating equivalent. |

Other Jackson branches not listed here were not attempted by this suite; this
document intentionally makes no category-wide skip claim.
