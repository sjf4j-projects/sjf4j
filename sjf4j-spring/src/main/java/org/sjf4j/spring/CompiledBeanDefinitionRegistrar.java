package org.sjf4j.spring;

import org.sjf4j.CompiledInstances;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ClassUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

/** Registers indexed compiled SJF4J interfaces as ordinary, lazy Spring Beans. */
public final class CompiledBeanDefinitionRegistrar
        implements BeanDefinitionRegistryPostProcessor, BeanFactoryAware, BeanClassLoaderAware {

    private static final String INDEX = "classpath*:META-INF/sjf4j/compiled.interfaces";
    private ConfigurableListableBeanFactory beanFactory;
    private ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

    @Override
    public void setBeanFactory(BeanFactory factory) throws BeansException {
        if (!(factory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalStateException("SJF4J requires a ConfigurableListableBeanFactory");
        }
        this.beanFactory = (ConfigurableListableBeanFactory) factory;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (String name : indexedTypes(classLoader)) {
            Class<?> type = load(name);
            if (!type.isInterface()) {
                throw new BeanDefinitionStoreException("Indexed SJF4J type is not an interface: " + name);
            }

            // An explicitly registered bean of the same interface takes priority.
            if (beanFactory.getBeanNamesForType(type, true, false).length != 0) {
                continue;
            }

            Class<?> implementation = load(name + CompiledInstances.IMPL_SUFFIX);
            if (!type.isAssignableFrom(implementation)) {
                throw new BeanDefinitionStoreException("Generated implementation " + implementation.getName() +
                        " does not implement " + name);
            }
            if (registry.containsBeanDefinition(name)) {
                throw new BeanDefinitionStoreException("SJF4J Bean name already in use: " + name);
            }

            RootBeanDefinition definition = new RootBeanDefinition(implementation);
            definition.setLazyInit(true);
            definition.setInstanceSupplier(() -> CompiledInstances.create(type));
            registry.registerBeanDefinition(name, definition);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) throws BeansException {
        // Registration occurs before standard singleton initialization.
    }

    static Set<String> indexedTypes(ClassLoader loader) {
        Set<String> names = new TreeSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
        try {
            for (Resource resource : resolver.getResources(INDEX)) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String name = line.trim();
                        if (!name.isEmpty() && !name.startsWith("#")) names.add(name);
                    }
                }
            }
        } catch (IOException e) {
            throw new BeanDefinitionStoreException("Cannot read SJF4J compiled indexes", e);
        }
        return names;
    }

    private Class<?> load(String name) {
        try {
            return ClassUtils.forName(name, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new BeanDefinitionStoreException("Cannot load indexed SJF4J class " + name +
                    "; check annotation processing and packaging", e);
        }
    }
}
