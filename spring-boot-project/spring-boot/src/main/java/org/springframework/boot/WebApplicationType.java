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

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.ClassUtils;

/**
 * An enumeration of possible types of web application.
 * <p>
 * Web 应用类型枚举
 * @author Andy Wilkinson
 * @author Brian Clozel
 * @since 2.0.0
 */
public enum WebApplicationType {

    /**
     * The application should not run as a web application and should not start an
     * embedded web server.
     * <p>
     * 非 Web 应用，即普通 Spring 应用，不启动嵌入式 Web 服务器，只启动 Spring 容器。
     */
    NONE,

    /**
     * The application should run as a servlet-based web application and should start an
     * embedded servlet web server.
     * <p>
     * Servlet 应用，即基于 Servlet 的 Web 应用，会启动嵌入式 Servlet Web 服务器（默认为 Tomcat）。
     */
    SERVLET,

    /**
     * The application should run as a reactive web application and should start an
     * embedded reactive web server.
     * <p>
     * Reactive 应用，即基于 Reactive 的 Web 应用，会启动嵌入式 Reactive Web 服务器（默认为 Netty）。
     */
    REACTIVE;

    /**
     * Servlet 应用特有类
     * 用于根据类路径推断应用类型
     */
    private static final String[] SERVLET_INDICATOR_CLASSES = { "jakarta.servlet.Servlet",
            "org.springframework.web.context.ConfigurableWebApplicationContext" };

    /**
     * Spring MVC 应用特有类
     * 用于根据类路径推断应用类型
     */
    private static final String WEBMVC_INDICATOR_CLASS = "org.springframework.web.servlet.DispatcherServlet";

    /**
     * Reactive 应用特有类
     * 用于根据类路径推断应用类型
     */
    private static final String WEBFLUX_INDICATOR_CLASS = "org.springframework.web.reactive.DispatcherHandler";

    /**
     * Jersey 应用特有类（推测是一种 Servlet 容器）
     * 用于根据类路径推断应用类型
     */
    private static final String JERSEY_INDICATOR_CLASS = "org.glassfish.jersey.servlet.ServletContainer";

    /**
     * 根据类路径是否存在特定的类，来推断应用类型
     */
    static WebApplicationType deduceFromClasspath() {
        //Reactive 应用，即基于 Reactive 的 Web 应用，会启动嵌入式 Reactive Web 服务器（默认为 Netty）
        if (ClassUtils.isPresent(WEBFLUX_INDICATOR_CLASS, null) && !ClassUtils.isPresent(WEBMVC_INDICATOR_CLASS, null)
                && !ClassUtils.isPresent(JERSEY_INDICATOR_CLASS, null)) {
            return WebApplicationType.REACTIVE;
        }
        //非 Web 应用，即普通 Spring 应用，不启动嵌入式 Web 服务器，只启动 Spring 容器
        for (String className : SERVLET_INDICATOR_CLASSES) {
            if (!ClassUtils.isPresent(className, null)) {
                return WebApplicationType.NONE;
            }
        }
        //Servlet 应用，即基于 Servlet 的 Web 应用，会启动嵌入式 Servlet Web 服务器（默认为 Tomcat）
        return WebApplicationType.SERVLET;
    }

    static class WebApplicationTypeRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            for (String servletIndicatorClass : SERVLET_INDICATOR_CLASSES) {
                registerTypeIfPresent(servletIndicatorClass, classLoader, hints);
            }
            registerTypeIfPresent(JERSEY_INDICATOR_CLASS, classLoader, hints);
            registerTypeIfPresent(WEBFLUX_INDICATOR_CLASS, classLoader, hints);
            registerTypeIfPresent(WEBMVC_INDICATOR_CLASS, classLoader, hints);
        }

        private void registerTypeIfPresent(String typeName, ClassLoader classLoader, RuntimeHints hints) {
            if (ClassUtils.isPresent(typeName, classLoader)) {
                hints.reflection().registerType(TypeReference.of(typeName));
            }
        }

    }

}
