/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.aot.AotDetector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * Default {@link ApplicationContextFactory} implementation that will create an
 * appropriate context for the {@link WebApplicationType}.
 *
 * @author Phillip Webb
 */
class DefaultApplicationContextFactory implements ApplicationContextFactory {

    @Override
    public Class<? extends ConfigurableEnvironment> getEnvironmentType(WebApplicationType webApplicationType) {
        return getFromSpringFactories(webApplicationType, ApplicationContextFactory::getEnvironmentType, null);
    }

    @Override
    public ConfigurableEnvironment createEnvironment(WebApplicationType webApplicationType) {
        /* 根据 webApplicationType，返回 ApplicationServletEnvironment 对象或者 ApplicationReactiveWebEnvironment 对象
         * 如果 webApplicationType 既不是 WebApplicationType.REACTIVE，也不是 WebApplicationType.SERVLET，则返回 null
         */
        return getFromSpringFactories(webApplicationType, ApplicationContextFactory::createEnvironment, null);
    }

    @Override
    public ConfigurableApplicationContext create(WebApplicationType webApplicationType) {
        try {
            return getFromSpringFactories(webApplicationType, ApplicationContextFactory::create,
                    this::createDefaultApplicationContext);
        }
        catch (Exception ex) {
            throw new IllegalStateException("Unable create a default ApplicationContext instance, "
                    + "you may need a custom ApplicationContextFactory", ex);
        }
    }

    private ConfigurableApplicationContext createDefaultApplicationContext() {
        if (!AotDetector.useGeneratedArtifacts()) {
            return new AnnotationConfigApplicationContext();
        }
        return new GenericApplicationContext();
    }

    private <T> T getFromSpringFactories(WebApplicationType webApplicationType,
            BiFunction<ApplicationContextFactory, WebApplicationType, T> action, Supplier<T> defaultResult) {
        /*
         * 从类路径下（包括第三方 jar 包）的 META-INF/spring.factories 文件中读取配置的
         * org.springframework.boot.ApplicationContextFactory 接口实现类列表并创建其对象
         * 读取到的配置：
         * org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContextFactory
         * org.springframework.boot.web.servlet.context.ServletWebServerApplicationContextFactory
         */
        for (ApplicationContextFactory candidate : SpringFactoriesLoader.loadFactories(ApplicationContextFactory.class,
                getClass().getClassLoader())) {
            //根据 webApplicationType，返回 ApplicationServletEnvironment 对象或者 ApplicationReactiveWebEnvironment 对象
            T result = action.apply(candidate, webApplicationType);
            if (result != null) {
                return result;
            }
        }
        //如果 webApplicationType 既不是 WebApplicationType.REACTIVE，也不是 WebApplicationType.SERVLET，则返回 null
        return (defaultResult != null) ? defaultResult.get() : null;
    }

}
