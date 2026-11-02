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
            assertEquals(Address.class, property.contentClass);
            assertNotNull(property.contentTypeInfo, name);
            assertSame(addressInfo, property.contentTypeInfo.get(), name);
        }

        PropertyInfo nested = pojo.getPropertyNoAlias("nested");
        assertInstanceOf(ParameterizedType.class, nested.contentType);
        assertEquals(List.class, nested.contentClass);
        assertEquals(List.class, nested.contentTypeInfo.clazz);
        assertSame(TypeInfo.NONE, nested.contentTypeInfo.get());

        PropertyInfo scalar = pojo.getPropertyNoAlias("scalar");
        assertNull(scalar.typeInfo);
        assertNull(scalar.contentTypeInfo);
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
