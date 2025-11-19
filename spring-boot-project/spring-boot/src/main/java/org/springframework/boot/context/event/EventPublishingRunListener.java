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

package org.springframework.boot.context.event;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ErrorHandler;

/**
 * {@link SpringApplicationRunListener} to publish {@link SpringApplicationEvent}s.
 * <p>
 * Uses an internal {@link ApplicationEventMulticaster} for the events that are fired
 * before the context is actually refreshed.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Artsiom Yudovin
 * @author Brian Clozel
 * @author Chris Bono
 */
class EventPublishingRunListener implements SpringApplicationRunListener, Ordered {

    private final SpringApplication application;

    private final String[] args;

    private final SimpleApplicationEventMulticaster initialMulticaster;

    /**
     * 该构造器的第一个参数就是主启动类的 main() 方法中调用 SpringApplication.run() 的 SpringApplication 对象
     * 第二个参数就是主启动类的 main() 方法的入参
     */
    EventPublishingRunListener(SpringApplication application, String[] args) {
        this.application = application;
        this.args = args;
        this.initialMulticaster = new SimpleApplicationEventMulticaster();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void starting(ConfigurableBootstrapContext bootstrapContext) {
        /* bootstrapContext 的实际类型：DefaultBootstrapContext
         * application 就是主启动类的 main() 方法中调用 SpringApplication.run() 的 SpringApplication 对象
         * args 就是主启动类的 main() 方法的入参
         *
         * multicastInitialEvent() 方法的作用：找到 SpringApplication 中不同 event（当前 event：starting）需要处理的监听器，并调用这些监听器的 onApplicationEvent() 方法
         * starting 阶段需要处理的监听器有两个：LoggingApplicationListener 和 BackgroundPreinitializer
         *  1、LoggingApplicationListener 中的 onApplicationEvent() 方法调用了 LogbackLoggingSystem 中的 beforeInitialize() 方法
         *     beforeInitialize() 方法的作用：将 rootLogger 中的 ConsoleHandler 替换为 SLF4JBridgeHandler
         *  2、BackgroundPreinitializer 中的 onApplicationEvent() 方法什么都没做
         */
        multicastInitialEvent(new ApplicationStartingEvent(bootstrapContext, this.application, this.args));
    }

    @Override
    public void environmentPrepared(ConfigurableBootstrapContext bootstrapContext,
            ConfigurableEnvironment environment) {
        /* bootstrapContext 的实际类型：DefaultBootstrapContext
         * application 就是主启动类的 main() 方法中调用 SpringApplication.run() 的 SpringApplication 对象
         * args 就是主启动类的 main() 方法的入参
         * environment 中包含各种系统属性、环境变量、命令行参数、当前应用信息资源（只包含当前服务的进程 ID）
         *
         * multicastInitialEvent() 方法的作用：找到 SpringApplication 中不同 event（当前 event：environmentPrepared）需要处理的监听器，并调用这些监听器的 onApplicationEvent() 方法
         * environmentPrepared 阶段需要处理的监听器有五个：EnvironmentPostProcessorApplicationListener、AnsiOutputApplicationListener、LoggingApplicationListener、BackgroundPreinitializer 和 FileEncodingApplicationListener
         *  1、EnvironmentPostProcessorApplicationListener
         *     读取所有类路径下的 META-INF/spring.factories 配置文件，找到所有 org.springframework.boot.env.EnvironmentPostProcessor 接口的实现类并创建对象，然后调用每个对象的 postProcessEnvironment(environment, application) 方法
         *     总共找到并创建了 7 个 org.springframework.boot.env.EnvironmentPostProcessor 接口的实现类对象，其中：
         *      1、RandomValuePropertySourceEnvironmentPostProcessor：给 environment 中的 SystemEnvironmentPropertySource 资源后面添加了一个 RandomValuePropertySource 资源对象
         *      2、SystemEnvironmentPropertySourceEnvironmentPostProcessor 将 environment 中的 SystemEnvironmentPropertySource 资源替换为了 SystemEnvironmentPropertySourceEnvironmentPostProcessor 中的内部类 OriginAwareSystemEnvironmentPropertySource（资源名称和内容都没变，只是换了个包装类）
         *      3、CloudFoundryVcapEnvironmentPostProcessor 想做啥但啥都没做
         *      4、SpringApplicationJsonEnvironmentPostProcessor 想做啥但啥都没做
         *      5、ConfigDataEnvironmentPostProcessor：解析配置文件，将配置文件中的配置项封装到一个 OriginTrackedMapPropertySource 中（一个配置文件对应一个 OriginTrackedMapPropertySource），然后将其添加到环境资源的最后面
         *         OriginTrackedMapPropertySource 中有一个 Map<String, String> 类型的 source，其中 Map 的 Key 为配置项的 Key，Map 的 Value 为配置项的 Value
         *      6、IntegrationPropertiesEnvironmentPostProcessor 想做啥但啥都没做
         *      7、ReactorEnvironmentPostProcessor 想做啥但啥都没做
         *  2、AnsiOutputApplicationListener：没看懂干了啥
         *  3、LoggingApplicationListener：没看懂干了啥
         *  4、BackgroundPreinitializer：没看懂干了啥
         *  5、FileEncodingApplicationListener：想做啥但啥都没做
         */
        multicastInitialEvent(
                new ApplicationEnvironmentPreparedEvent(bootstrapContext, this.application, this.args, environment));
    }

    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        multicastInitialEvent(new ApplicationContextInitializedEvent(this.application, this.args, context));
    }

    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {
        for (ApplicationListener<?> listener : this.application.getListeners()) {
            if (listener instanceof ApplicationContextAware contextAware) {
                contextAware.setApplicationContext(context);
            }
            context.addApplicationListener(listener);
        }
        multicastInitialEvent(new ApplicationPreparedEvent(this.application, this.args, context));
    }

    @Override
    public void started(ConfigurableApplicationContext context, Duration timeTaken) {
        context.publishEvent(new ApplicationStartedEvent(this.application, this.args, context, timeTaken));
        AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
    }

    @Override
    public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
        context.publishEvent(new ApplicationReadyEvent(this.application, this.args, context, timeTaken));
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        ApplicationFailedEvent event = new ApplicationFailedEvent(this.application, this.args, context, exception);
        if (context != null && context.isActive()) {
            // Listeners have been registered to the application context so we should
            // use it at this point if we can
            context.publishEvent(event);
        }
        else {
            // An inactive context may not have a multicaster so we use our multicaster to
            // call all the context's listeners instead
            if (context instanceof AbstractApplicationContext abstractApplicationContext) {
                for (ApplicationListener<?> listener : abstractApplicationContext.getApplicationListeners()) {
                    this.initialMulticaster.addApplicationListener(listener);
                }
            }
            this.initialMulticaster.setErrorHandler(new LoggingErrorHandler());
            this.initialMulticaster.multicastEvent(event);
        }
    }

    private void multicastInitialEvent(ApplicationEvent event) {
        //将 SpringApplication 中的所有监听器添加到 SimpleApplicationEventMulticaster 中
        refreshApplicationListeners();
        /* 调用 SimpleApplicationEventMulticaster 中的 multicastEvent() 方法
         * multicastEvent() 方法的作用：找到 SpringApplication 中不同 event 需要处理的监听器，并调用这些监听器的 onApplicationEvent() 方法
         */
        this.initialMulticaster.multicastEvent(event);
    }

    private void refreshApplicationListeners() {
        //将 SpringApplication 中的所有监听器添加到 SimpleApplicationEventMulticaster 中
        this.application.getListeners().forEach(this.initialMulticaster::addApplicationListener);
    }

    private static final class LoggingErrorHandler implements ErrorHandler {

        private static final Log logger = LogFactory.getLog(EventPublishingRunListener.class);

        @Override
        public void handleError(Throwable throwable) {
            logger.warn("Error calling ApplicationEventListener", throwable);
        }

    }

}
