package org.sjf4j.spring.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;

class Sjf4jSpringConverterTest {

    @Test
    void compiledMapperIsARealSpringConverterBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(Sjf4jAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CompiledFixtures.CarMapper.class);

                    CompiledFixtures.CarMapper mapper =
                            context.getBean(CompiledFixtures.CarMapper.class);
                    assertThat(mapper).isInstanceOf(Converter.class);
                    assertThat(context.getBean(CompiledFixtures.CarMapper.class)).isSameAs(mapper);

                    CompiledFixtures.Car source = new CompiledFixtures.Car("Sedan", 5);
                    CompiledFixtures.CarDto mapped = mapper.convert(source);
                    assertThat(mapped.model).isEqualTo("Sedan");
                    assertThat(mapped.seats).isEqualTo(5);

                    // Verify the actual generated mapper can be used by Spring's
                    // standard ConversionService without an adapter.
                    DefaultConversionService conversions = new DefaultConversionService();
                    conversions.addConverter(mapper);
                    CompiledFixtures.CarDto converted =
                            conversions.convert(source, CompiledFixtures.CarDto.class);
                    assertThat(converted).isNotNull();
                    assertThat(converted.model).isEqualTo("Sedan");
                    assertThat(converted.seats).isEqualTo(5);
                });
    }

    @Test
    void springBootMvcAutomaticallyRegistersTheCompiledConverter() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        Sjf4jAutoConfiguration.class,
                        WebMvcAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CompiledFixtures.CarMapper.class);

                    // MVC collects Converter beans; the starter must register
                    // its BeanDefinition early enough for that discovery.
                    ConversionService mvc = context.getBean(
                            "mvcConversionService", ConversionService.class);
                    assertThat(mvc.canConvert(
                            CompiledFixtures.Car.class, CompiledFixtures.CarDto.class))
                            .isTrue();

                    CompiledFixtures.CarDto dto = mvc.convert(
                            new CompiledFixtures.Car("Coupe", 2),
                            CompiledFixtures.CarDto.class);
                    assertThat(dto).isNotNull();
                    assertThat(dto.model).isEqualTo("Coupe");
                    assertThat(dto.seats).isEqualTo(2);
                });
    }
}
