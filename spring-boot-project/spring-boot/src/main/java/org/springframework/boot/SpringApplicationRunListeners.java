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

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.util.ReflectionUtils;

/**
 * A collection of {@link SpringApplicationRunListener}.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Chris Bono
 */
class SpringApplicationRunListeners {

    private final Log log;

    private final List<SpringApplicationRunListener> listeners;

    //实际类型：DefaultApplicationStartup
    private final ApplicationStartup applicationStartup;

    SpringApplicationRunListeners(Log log, List<SpringApplicationRunListener> listeners,
            ApplicationStartup applicationStartup) {
        this.log = log;
        this.listeners = List.copyOf(listeners);
        this.applicationStartup = applicationStartup;
    }

    void starting(ConfigurableBootstrapContext bootstrapContext, Class<?> mainApplicationClass) {
        /* 第一个参数和第三个参数没啥用
         * 第二个参数的作用是：调用每个 listener 中的 starting(bootstrapContext) 方法
         * 其中的 bootstrapContext 参数是 DefaultBootstrapContext 的实例
         * 由前可知，这里的 listeners 中只有一个监听器：EventPublishingRunListener
         * EventPublishingRunListener 会找到 SpringApplication 中不同 event（当前 event：starting）需要处理的监听器，并调用这些监听器的 onApplicationEvent() 方法
         *
         * starting 阶段需要处理的监听器有两个：LoggingApplicationListener 和 BackgroundPreinitializer
         *  1、LoggingApplicationListener 中的 onApplicationEvent() 方法调用了 LogbackLoggingSystem 中的 beforeInitialize() 方法
         *     beforeInitialize() 方法的作用：将 rootLogger 中的 ConsoleHandler 替换为 SLF4JBridgeHandler
         *  2、BackgroundPreinitializer 中的 onApplicationEvent() 方法什么都没做
         */
        doWithListeners("spring.boot.application.starting", (listener) -> listener.starting(bootstrapContext),
                (step) -> {
                    if (mainApplicationClass != null) {
                        step.tag("mainApplicationClass", mainApplicationClass.getName());
                    }
                });
    }

    void environmentPrepared(ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
        doWithListeners("spring.boot.application.environment-prepared",
                (listener) -> listener.environmentPrepared(bootstrapContext, environment));
    }

    void contextPrepared(ConfigurableApplicationContext context) {
        doWithListeners("spring.boot.application.context-prepared", (listener) -> listener.contextPrepared(context));
    }

    void contextLoaded(ConfigurableApplicationContext context) {
        doWithListeners("spring.boot.application.context-loaded", (listener) -> listener.contextLoaded(context));
    }

    void started(ConfigurableApplicationContext context, Duration timeTaken) {
        doWithListeners("spring.boot.application.started", (listener) -> listener.started(context, timeTaken));
    }

    void ready(ConfigurableApplicationContext context, Duration timeTaken) {
        doWithListeners("spring.boot.application.ready", (listener) -> listener.ready(context, timeTaken));
    }

    void failed(ConfigurableApplicationContext context, Throwable exception) {
        doWithListeners("spring.boot.application.failed",
                (listener) -> callFailedListener(listener, context, exception), (step) -> {
                    step.tag("exception", exception.getClass().toString());
                    step.tag("message", exception.getMessage());
                });
    }

    private void callFailedListener(SpringApplicationRunListener listener, ConfigurableApplicationContext context,
            Throwable exception) {
        try {
            listener.failed(context, exception);
        }
        catch (Throwable ex) {
            if (exception == null) {
                ReflectionUtils.rethrowRuntimeException(ex);
            }
            if (this.log.isDebugEnabled()) {
                this.log.error("Error handling failed", ex);
            }
            else {
                String message = ex.getMessage();
                message = (message != null) ? message : "no error message";
                this.log.warn("Error handling failed (" + message + ")");
            }
        }
    }

    private void doWithListeners(String stepName, Consumer<SpringApplicationRunListener> listenerAction) {
        doWithListeners(stepName, listenerAction, null);
    }

    private void doWithListeners(String stepName, Consumer<SpringApplicationRunListener> listenerAction,
            Consumer<StartupStep> stepAction) {
        /* 参数 stepName 没啥用
         * applicationStartup 的实际类型：DefaultApplicationStartup，这个方法返回了他的内部类 DefaultStartupStep 的对象
         */
        StartupStep step = this.applicationStartup.start(stepName);
        //根据 listenerAction，调用每个监听器中的相应方法
        this.listeners.forEach(listenerAction);
        if (stepAction != null) {
            //什么都没做
            stepAction.accept(step);
        }
        //什么都没做
        step.end();
    }

}
