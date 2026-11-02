package org.sjf4j.meta;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PropertyContentTypeInfoTest {

    public static class Address {
        public String city;
    }

    public static class Holder {
        public Address address;
        public List<Address> addresses;
        public Set<Address> uniqueAddresses;
        public Map<String, Address> addressMap;
        public Address[] addressArray;
        public Map<String, List<Address>> nested;
        public String scalar;
    }

    public static class GenericHolder<T> {
        public Map<String, T> values;
    }

    @Test
    void cachesDeclaredObjectAndContainerContentMetadata() {
        PojoInfo pojo = TypeRegistry.requireRegisteredPojoInfo(Holder.class);
        TypeInfo addressInfo = TypeRegistry.registerTypeInfo(Address.class);

        PropertyInfo address = pojo.getPropertyNoAlias("address");
        assertNotNull(address.typeInfo);
        assertEquals(Address.class, address.typeInfo.clazz);
        assertSame(addressInfo, address.typeInfo.get());
        assertNull(address.contentType);
        assertNull(address.contentTypeInfo);

        for (String name : new String[] {"addresses", "uniqueAddresses", "addressMap", "addressArray"}) {
            PropertyInfo property = pojo.getPropertyNoAlias(name);
            assertEquals(Address.class, property.contentType);
            assertEquals(Address.class, property.contentBoxed);
            assertNotNull(property.contentTypeInfo, name);
            assertSame(addressInfo, property.contentTypeInfo.get(), name);
        }

        PropertyInfo nested = pojo.getPropertyNoAlias("nested");
        assertInstanceOf(ParameterizedType.class, nested.contentType);
        assertEquals(List.class, nested.contentBoxed);
        // List.class has no registered metadata and is handled directly.
        assertNull(nested.contentTypeInfo);

        PropertyInfo scalar = pojo.getPropertyNoAlias("scalar");
        assertNull(scalar.typeInfo);
        assertNull(scalar.contentTypeInfo);
    }

    @Test
    void lazyFactoryReusesTypeRegistryFastNoneClassification() {
        assertNull(LazyTypeInfo.of(null));
        assertNull(LazyTypeInfo.of(Object.class));
        assertNull(LazyTypeInfo.of(String.class));
        assertNull(LazyTypeInfo.of(Integer.class));
        assertNull(LazyTypeInfo.of(Map.class));
        assertNull(LazyTypeInfo.of(List.class));
        assertNull(LazyTypeInfo.of(Address[].class));

        LazyTypeInfo address = LazyTypeInfo.of(Address.class);
        assertNotNull(address);
        assertSame(TypeRegistry.registerTypeInfo(Address.class), address.get());
    }

    @Test
    void doesNotCacheUnresolvedTypeVariablesAsObject() {
        PropertyInfo property = TypeRegistry.requireRegisteredPojoInfo(GenericHolder.class)
                .getPropertyNoAlias("values");
        assertTrue(property.genericDependent);
        assertNull(property.typeInfo);
        assertNull(property.contentTypeInfo);
        assertNotNull(property.contentType);
    }
}
