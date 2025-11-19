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

import java.lang.StackWalker.StackFrame;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.crac.management.CRaCMXBean;

import org.springframework.aot.AotDetector;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.NativeDetector;
import org.springframework.core.OrderComparator;
import org.springframework.core.OrderComparator.OrderSourceProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.ThrowingConsumer;
import org.springframework.util.function.ThrowingSupplier;

/**
 * Class that can be used to bootstrap and launch a Spring application from a Java main
 * method. By default class will perform the following steps to bootstrap your
 * application:
 *
 * <ul>
 * <li>Create an appropriate {@link ApplicationContext} instance (depending on your
 * classpath)</li>
 * <li>Register a {@link CommandLinePropertySource} to expose command line arguments as
 * Spring properties</li>
 * <li>Refresh the application context, loading all singleton beans</li>
 * <li>Trigger any {@link CommandLineRunner} beans</li>
 * </ul>
 *
 * In most circumstances the static {@link #run(Class, String[])} method can be called
 * directly from your {@literal main} method to bootstrap your application:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication  {
 *
 *   // ... Bean definitions
 *
 *   public static void main(String[] args) {
 *     SpringApplication.run(MyApplication.class, args);
 *   }
 * }
 * </pre>
 *
 * <p>
 * For more advanced configuration a {@link SpringApplication} instance can be created and
 * customized before being run:
 *
 * <pre class="code">
 * public static void main(String[] args) {
 *   SpringApplication application = new SpringApplication(MyApplication.class);
 *   // ... customize application settings here
 *   application.run(args)
 * }
 * </pre>
 *
 * {@link SpringApplication}s can read beans from a variety of different sources. It is
 * generally recommended that a single {@code @Configuration} class is used to bootstrap
 * your application, however, you may also set {@link #getSources() sources} from:
 * <ul>
 * <li>The fully qualified class name to be loaded by
 * {@link AnnotatedBeanDefinitionReader}</li>
 * <li>The location of an XML resource to be loaded by {@link XmlBeanDefinitionReader}, or
 * a groovy script to be loaded by {@link GroovyBeanDefinitionReader}</li>
 * <li>The name of a package to be scanned by {@link ClassPathBeanDefinitionScanner}</li>
 * </ul>
 *
 * Configuration properties are also bound to the {@link SpringApplication}. This makes it
 * possible to set {@link SpringApplication} properties dynamically, like additional
 * sources ("spring.main.sources" - a CSV list) the flag to indicate a web environment
 * ("spring.main.web-application-type=none") or the flag to switch off the banner
 * ("spring.main.banner-mode=off").
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @author Madhura Bhave
 * @author Brian Clozel
 * @author Ethan Rubinson
 * @author Chris Bono
 * @author Moritz Halbritter
 * @author Tadaya Tsuyukubo
 * @author Lasse Wulff
 * @author Yanming Zhou
 * @since 1.0.0
 * @see #run(Class, String[])
 * @see #run(Class[], String[])
 * @see #SpringApplication(Class...)
 */
public class SpringApplication {

    /**
     * Default banner location.
     */
    public static final String BANNER_LOCATION_PROPERTY_VALUE = SpringApplicationBannerPrinter.DEFAULT_BANNER_LOCATION;

    /**
     * Banner location property key.
     */
    public static final String BANNER_LOCATION_PROPERTY = SpringApplicationBannerPrinter.BANNER_LOCATION_PROPERTY;

    private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

    private static final Log logger = LogFactory.getLog(SpringApplication.class);

    static final SpringApplicationShutdownHook shutdownHook = new SpringApplicationShutdownHook();

    private static final ThreadLocal<SpringApplicationHook> applicationHook = new ThreadLocal<>();

    private final Set<Class<?>> primarySources;

    private Class<?> mainApplicationClass;

    private boolean addCommandLineProperties = true;

    private boolean addConversionService = true;

    private Banner banner;

    private ResourceLoader resourceLoader;

    private BeanNameGenerator beanNameGenerator;

    private ConfigurableEnvironment environment;

    private boolean headless = true;

    private List<ApplicationContextInitializer<?>> initializers;

    private List<ApplicationListener<?>> listeners;

    private Map<String, Object> defaultProperties;

    private final List<BootstrapRegistryInitializer> bootstrapRegistryInitializers;

    private Set<String> additionalProfiles = Collections.emptySet();

    private boolean isCustomEnvironment;

    private String environmentPrefix;

    private ApplicationContextFactory applicationContextFactory = ApplicationContextFactory.DEFAULT;

    private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

    final ApplicationProperties properties = new ApplicationProperties();

    /**
     * Create a new {@link SpringApplication} instance. The application context will load
     * beans from the specified primary sources (see {@link SpringApplication class-level}
     * documentation for details). The instance can be customized before calling
     * {@link #run(String...)}.
     * @param primarySources the primary bean sources
     * @see #run(Class, String[])
     * @see #SpringApplication(ResourceLoader, Class...)
     * @see #setSources(Set)
     */
    public SpringApplication(Class<?>... primarySources) {
        this(null, primarySources);
    }

    /**
     * Create a new {@link SpringApplication} instance. The application context will load
     * beans from the specified primary sources (see {@link SpringApplication class-level}
     * documentation for details). The instance can be customized before calling
     * {@link #run(String...)}.
     * @param resourceLoader the resource loader to use
     * @param primarySources the primary bean sources
     * @see #run(Class, String[])
     * @see #setSources(Set)
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
        //该参数传进来时是个null
        this.resourceLoader = resourceLoader;
        Assert.notNull(primarySources, "'primarySources' must not be null");
        //将主配置类（一般都是将主启动类作为主配置类）放到一个 LinkedHashSet 中
        this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
        //设置 Web 应用类型（根据类路径是否存在特定的类，来推断得出）
        this.properties.setWebApplicationType(WebApplicationType.deduceFromClasspath());
        //返回了一个空 List
        this.bootstrapRegistryInitializers = new ArrayList<>(
                /*
                 * 读取所有类路径下（包括第三方 jar 包）的 META-INF/spring.factories 文件中的 Key-Value 对，并将其合并到一个 Map<String, List<String>> 中
                 * 注意：一个 Key 可能对应多个 Value，且多个 META-INF/spring.factories 文件中可能存在相同的 Key，这些 Key 中有的 Value 也可能会重复
                 *      所以才会将所有相同的 Key 对应的 Value 都放到一个 List 中，且对 List 中的元素进行了去重
                 *      这里的 Key 一般是一个接口的全限定名，而 Value 是这些接口的实现类
                 * 然后再查找 Key-Value 对中是否存在名为 org.springframework.boot.BootstrapRegistryInitializer 的 Key
                 * 如果存在，则创建其 Value 对应的对象，并将对象放到一个 List 中返回
                 * 查找结果：
                 * 所有 META-INF/spring.factories 文件中没有名为 org.springframework.boot.BootstrapRegistryInitializer 的 Key
                 * 所以这里返回了一个空 List
                 */
                getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
        //跟上面调用的是同一个方法，功能相同，但这里返回的不是一个空 List
        setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
        //跟上面调用的是同一个方法，功能相同，但这里返回的不是一个空 List
        setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
        //获取主启动类（main()方法所在类）
        this.mainApplicationClass = deduceMainApplicationClass();
    }

    private Class<?> deduceMainApplicationClass() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(this::findMainClass)
                .orElse(null);
    }

    private Optional<Class<?>> findMainClass(Stream<StackFrame> stack) {
        return stack.filter((frame) -> Objects.equals(frame.getMethodName(), "main"))
                .findFirst()
                .map(StackWalker.StackFrame::getDeclaringClass);
    }

    /**
     * Run the Spring application, creating and refreshing a new
     * {@link ApplicationContext}.
     * @param args the application arguments (usually passed from a Java main method)
     * @return a running {@link ApplicationContext}
     */
    public ConfigurableApplicationContext run(String... args) {
        /* 创建并返回了一个 Startup 类的子类 StandardStartup 对象，该对象中什么也没做，
         * 只记录了容器启动时间，即初始化了其成员变量 startTime = System.currentTimeMillis();
         */
        Startup startup = Startup.create();
        //允许调用容器关闭事件回调方法
        if (this.properties.isRegisterShutdownHook()) {
            //将 SpringApplicationShutdownHook 中的 shutdownHook 设置为了 true
            SpringApplication.shutdownHook.enableShutdownHookAddition();
        }
        //创建并返回了一个 DefaultBootstrapContext 对象
        DefaultBootstrapContext bootstrapContext = createBootstrapContext();
        ConfigurableApplicationContext context = null;
        //设置一个系统属性：java.awt.headless=true
        configureHeadlessProperty();
        /* 获取容器启动监听器，这些监听器包装在 listeners 对象中，实际上 listeners 中只有一个 EventPublishingRunListener 监听器
         * EventPublishingRunListener 的作用是：在容器启动的不同阶段，从当前对象的 listeners（即 this.listeners）中找到不同阶段需要处理的监听器，并调用其 onApplicationEvent() 方法
         */
        SpringApplicationRunListeners listeners = getRunListeners(args);
        //通知监听器容器启动开始
        listeners.starting(bootstrapContext, this.mainApplicationClass);
        try {
            /* 解析命令行参数，将命令行参数封装到一个 CommandLineArgs 对象中
             * CommandLineArgs 中有一个 Map<String, List<String>> 类型的 optionArgs 变量，其中 Map 的 Key 为参数名，Value 为参数值
             */
            ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
            //准备运行环境
            ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
            Banner printedBanner = printBanner(environment);
            context = createApplicationContext();
            context.setApplicationStartup(this.applicationStartup);
            prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
            refreshContext(context);
            afterRefresh(context, applicationArguments);
            startup.started();
            if (this.properties.isLogStartupInfo()) {
                new StartupInfoLogger(this.mainApplicationClass, environment).logStarted(getApplicationLog(), startup);
            }
            listeners.started(context, startup.timeTakenToStarted());
            callRunners(context, applicationArguments);
        }
        catch (Throwable ex) {
            throw handleRunFailure(context, ex, listeners);
        }
        try {
            if (context.isRunning()) {
                listeners.ready(context, startup.ready());
            }
        }
        catch (Throwable ex) {
            throw handleRunFailure(context, ex, null);
        }
        return context;
    }

    /**
     * 创建并返回一个 DefaultBootstrapContext 对象
     */
    private DefaultBootstrapContext createBootstrapContext() {
        DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
        //bootstrapRegistryInitializers 是个空 List，所以这个循环不会执行
        this.bootstrapRegistryInitializers.forEach((initializer) -> initializer.initialize(bootstrapContext));
        return bootstrapContext;
    }

    private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
            DefaultBootstrapContext bootstrapContext, ApplicationArguments applicationArguments) {
        // Create and configure the environment
        //创建运行环境，根据应用类型，返回 ApplicationServletEnvironment 或者 ApplicationReactiveWebEnvironment 或者 ApplicationEnvironment
        ConfigurableEnvironment environment = getOrCreateEnvironment();
        /* 配置环境资源，在已有的各种系统属性、环境变量等资源基础上，添加命令行参数资源和当前应用信息资源（只添加了当前服务的进程 ID）
         * applicationArguments.getSourceArgs() 返回的是主启动类中 main() 方法的原始参数，即 String[] args 数组
         */
        configureEnvironment(environment, applicationArguments.getSourceArgs());
        /* 将所有环境资源打包进 ConfigurationPropertySourcesPropertySource，并命名为 configurationProperties，然后将其添加到环境资源的第一个位置
         * 最终结果是：环境资源 sources 中第一个元素包含了 sources 中的所有元素，相当于 List 中的第一个元素是该 List 的引用本身
         */
        ConfigurationPropertySources.attach(environment);
        //通知监听器环境准备完毕
        listeners.environmentPrepared(bootstrapContext, environment);
        //将包含当前服务进程 ID 的应用信息资源移动到环境资源的最后一个位置
        ApplicationInfoPropertySource.moveToEnd(environment);
        //环境资源中没有名为 defaultProperties 的资源，所以下面的方法没有做什么真实操作
        DefaultPropertiesPropertySource.moveToEnd(environment);
        Assert.state(!environment.containsProperty("spring.main.environment-prefix"),
                "Environment prefix cannot be set via properties.");
        //没看懂里面具体干了个啥
        bindToSpringApplication(environment);
        //!this.isCustomEnvironment 为 true，所以下面的分支会执行
        if (!this.isCustomEnvironment) {
            //使用 AppClassLoader 创建了一个 EnvironmentConverter 对象
            EnvironmentConverter environmentConverter = new EnvironmentConverter(getClassLoader());
            /* 如果当前 environment 已经是 ApplicationServletEnvironment 类型或者 ApplicationReactiveWebEnvironment 类型或者 ApplicationEnvironment 类型，则直接返回其本身
             * deduceEnvironmentClass()：根据当前应用类型，返回 ApplicationServletEnvironment.class 或者 ApplicationReactiveWebEnvironment.class 或者 ApplicationEnvironment.class
             * 实际情况是当前 environment 对象就是 deduceEnvironmentClass() 方法返回的类型
             * 所以 environmentConverter.convertEnvironmentIfNecessary(environment, deduceEnvironmentClass()) 返回的还是当前 environment 对象
             */
            environment = environmentConverter.convertEnvironmentIfNecessary(environment, deduceEnvironmentClass());
        }
        //上面已经调用过该方法，所以这次调用并没有改变什么
        ConfigurationPropertySources.attach(environment);
        //返回运行环境
        return environment;
    }

    /**
     * 根据当前应用类型，返回 ApplicationServletEnvironment.class 或者 ApplicationReactiveWebEnvironment.class 或者 ApplicationEnvironment.class
     */
    private Class<? extends ConfigurableEnvironment> deduceEnvironmentClass() {
        //当前应用类型
        WebApplicationType webApplicationType = this.properties.getWebApplicationType();
        //根据当前应用类型，返回 ApplicationServletEnvironment.class 或者 ApplicationReactiveWebEnvironment.class 或者 null
        Class<? extends ConfigurableEnvironment> environmentType = this.applicationContextFactory
                .getEnvironmentType(webApplicationType);
        //this.applicationContextFactory != ApplicationContextFactory.DEFAULT 为 false，所以下面的分支不会执行
        if (environmentType == null && this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
            environmentType = ApplicationContextFactory.DEFAULT.getEnvironmentType(webApplicationType);
        }
        //如果既不是 ApplicationServletEnvironment.class，也不是 ApplicationReactiveWebEnvironment.class，则返回 ApplicationEnvironment.class
        return (environmentType != null) ? environmentType : ApplicationEnvironment.class;
    }

    private void prepareContext(DefaultBootstrapContext bootstrapContext, ConfigurableApplicationContext context,
            ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
            ApplicationArguments applicationArguments, Banner printedBanner) {
        context.setEnvironment(environment);
        postProcessApplicationContext(context);
        addAotGeneratedInitializerIfNecessary(this.initializers);
        applyInitializers(context);
        listeners.contextPrepared(context);
        bootstrapContext.close(context);
        if (this.properties.isLogStartupInfo()) {
            logStartupInfo(context.getParent() == null);
            logStartupInfo(context);
            logStartupProfileInfo(context);
        }
        // Add boot specific singleton beans
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
        if (printedBanner != null) {
            beanFactory.registerSingleton("springBootBanner", printedBanner);
        }
        if (beanFactory instanceof AbstractAutowireCapableBeanFactory autowireCapableBeanFactory) {
            autowireCapableBeanFactory.setAllowCircularReferences(this.properties.isAllowCircularReferences());
            if (beanFactory instanceof DefaultListableBeanFactory listableBeanFactory) {
                listableBeanFactory.setAllowBeanDefinitionOverriding(this.properties.isAllowBeanDefinitionOverriding());
            }
        }
        if (this.properties.isLazyInitialization()) {
            context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
        }
        if (this.properties.isKeepAlive()) {
            context.addApplicationListener(new KeepAlive());
        }
        context.addBeanFactoryPostProcessor(new PropertySourceOrderingBeanFactoryPostProcessor(context));
        if (!AotDetector.useGeneratedArtifacts()) {
            // Load the sources
            Set<Object> sources = getAllSources();
            Assert.state(!ObjectUtils.isEmpty(sources), "No sources defined");
            load(context, sources.toArray(new Object[0]));
        }
        listeners.contextLoaded(context);
    }

    private void addAotGeneratedInitializerIfNecessary(List<ApplicationContextInitializer<?>> initializers) {
        if (AotDetector.useGeneratedArtifacts()) {
            List<ApplicationContextInitializer<?>> aotInitializers = new ArrayList<>(
                    initializers.stream().filter(AotApplicationContextInitializer.class::isInstance).toList());
            if (aotInitializers.isEmpty()) {
                String initializerClassName = this.mainApplicationClass.getName() + "__ApplicationContextInitializer";
                if (!ClassUtils.isPresent(initializerClassName, getClassLoader())) {
                    throw new AotInitializerNotFoundException(this.mainApplicationClass, initializerClassName);
                }
                aotInitializers.add(AotApplicationContextInitializer.forInitializerClasses(initializerClassName));
            }
            initializers.removeAll(aotInitializers);
            initializers.addAll(0, aotInitializers);
        }
    }

    private void refreshContext(ConfigurableApplicationContext context) {
        if (this.properties.isRegisterShutdownHook()) {
            shutdownHook.registerApplicationContext(context);
        }
        refresh(context);
    }

    /**
     * 设置一个系统属性：java.awt.headless=true
     */
    private void configureHeadlessProperty() {
        System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS,
                System.getProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, Boolean.toString(this.headless)));
    }

    /**
     * 获取容器启动监听器列表，该列表包装在 SpringApplicationRunListeners 对象中
     */
    private SpringApplicationRunListeners getRunListeners(String[] args) {
        //ArgumentResolver 相当于一个 Map<Class<T>, T>，Map 的 Key 是一个 Class 对象，Map 的 Value 是 Class 类型对应的参数值
        ArgumentResolver argumentResolver = ArgumentResolver.of(SpringApplication.class, this);
        argumentResolver = argumentResolver.and(String[].class, args);
        /*
        /* 从类路径下（包括第三方 jar 包）的 META-INF/spring.factories 文件中读取配置的
         * org.springframework.boot.SpringApplicationRunListener 实现类列表并创建其对象
         * 其中，创建对象需要的参数从 argumentResolver 中获取
         *
         * 读取所有类路径下（包括第三方 jar 包）的 META-INF/spring.factories 文件中的 Key-Value 对，并将其合并到一个 Map<String, List<String>> 中
         * 注意：一个 Key 可能对应多个 Value，且多个 META-INF/spring.factories 文件中可能存在相同的 Key，这些 Key 中有的 Value 也可能会重复
         *      所以才会将所有相同的 Key 对应的 Value 都放到一个 List 中，且对 List 中的元素进行了去重
         *      这里的 Key 一般是一个接口的全限定名，而 Value 是这些接口的实现类
         * 然后再查找 Key-Value 对中是否存在名为 org.springframework.boot.SpringApplicationRunListener 的 Key
         * 如果存在，则创建其 Value 对应的对象，并将对象放到一个 List 中返回
         * 这里最终获取到了一个 EventPublishingRunListener 监听器对象
         * 该监听器的作用是：在容器启动的不同阶段，从当前对象的 listeners（即 this.listeners）中找到不同阶段需要处理的监听器，并调用其 onApplicationEvent() 方法
         */
        List<SpringApplicationRunListener> listeners = getSpringFactoriesInstances(SpringApplicationRunListener.class,
                argumentResolver);
        /*
         * 从 ThreadLocal<SpringApplicationHook> 中获取容器启动监听事件包装器
         * 因为从来没有在 ThreadLocal<SpringApplicationHook> 中设置过 SpringApplicationHook 对象
         * 所以这里获取到的 SpringApplicationHook 对象为 null
         */
        SpringApplicationHook hook = applicationHook.get();
        //从容器启动监听事件包装器中获取监听器（由上可知，这里返回的是 null）
        SpringApplicationRunListener hookListener = (hook != null) ? hook.getRunListener(this) : null;
        //如果从容器启动监听事件包装器中获取到了监听器，则将其添加到监听器列表中（由上可知，这里并没有获取到）
        if (hookListener != null) {
            listeners = new ArrayList<>(listeners);
            listeners.add(hookListener);
        }
        /* 将监听器列表包装在 SpringApplicationRunListeners 对象中，并将其返回
         * 实际上，listeners 中只有一个 EventPublishingRunListener 监听器对象
         */
        return new SpringApplicationRunListeners(logger, listeners, this.applicationStartup);
    }

    private <T> List<T> getSpringFactoriesInstances(Class<T> type) {
        return getSpringFactoriesInstances(type, null);
    }

    private <T> List<T> getSpringFactoriesInstances(Class<T> type, ArgumentResolver argumentResolver) {
        return SpringFactoriesLoader.forDefaultResourceLocation(getClassLoader()).load(type, argumentResolver);
    }

    private ConfigurableEnvironment getOrCreateEnvironment() {
        if (this.environment != null) {
            return this.environment;
        }
        //获取 Web 应用类型
        WebApplicationType webApplicationType = this.properties.getWebApplicationType();
        /* 根据 webApplicationType，创建运行环境
         * applicationContextFactory 的实际类型为 DefaultApplicationContextFactory
         * 根据 webApplicationType，返回 ApplicationServletEnvironment 对象或者 ApplicationReactiveWebEnvironment 对象
         * 如果 webApplicationType 既不是 WebApplicationType.REACTIVE，也不是 WebApplicationType.SERVLET，则返回 null
         */
        ConfigurableEnvironment environment = this.applicationContextFactory.createEnvironment(webApplicationType);
        if (environment == null && this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
            //this.applicationContextFactory != ApplicationContextFactory.DEFAULT 的结果为 false，所以即使 environment 为 null，也不会走这个分支
            environment = ApplicationContextFactory.DEFAULT.createEnvironment(webApplicationType);
        }
        //如果 environment 不为 null，则返回 environment，否则创建并返回一个 ApplicationEnvironment 对象作为默认的运行环境
        return (environment != null) ? environment : new ApplicationEnvironment();
    }

    /**
     * Template method delegating to
     * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
     * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
     * Override this method for complete control over Environment customization, or one of
     * the above for fine-grained control over property sources or profiles, respectively.
     * @param environment this application's environment
     * @param args arguments passed to the {@code run} method
     * @see #configureProfiles(ConfigurableEnvironment, String[])
     * @see #configurePropertySources(ConfigurableEnvironment, String[])
     */
    protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
        //这个参数是 true
        if (this.addConversionService) {
            //给环境设置一个 ApplicationConversionService 对象
            environment.setConversionService(new ApplicationConversionService());
        }
        //加载命令行参数资源和当前应用信息资源（只加载了当前服务的进程 ID）
        configurePropertySources(environment, args);
        //空方法，什么都没做
        configureProfiles(environment, args);
    }

    /**
     * Add, remove or re-order any {@link PropertySource}s in this application's
     * environment.
     * @param environment this application's environment
     * @param args arguments passed to the {@code run} method
     * @see #configureEnvironment(ConfigurableEnvironment, String[])
     */
    protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
        //获取已经加载的所有环境资源，其中已经包含了各种系统属性、环境变量等
        MutablePropertySources sources = environment.getPropertySources();
        //this.defaultProperties 是 null，所以不会走下面的 if
        if (!CollectionUtils.isEmpty(this.defaultProperties)) {
            DefaultPropertiesPropertySource.addOrMerge(this.defaultProperties, sources);
        }
        //this.addCommandLineProperties 本身是 true，所以只要 args.length > 0，就会进入下面的 if
        if (this.addCommandLineProperties && args.length > 0) {
            String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
            if (sources.contains(name)) {
                //刚开始 sources 中没有命令行参数资源，所以不会走这个分支
                PropertySource<?> source = sources.get(name);
                CompositePropertySource composite = new CompositePropertySource(name);
                composite
                        .addPropertySource(new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
                composite.addPropertySource(source);
                sources.replace(name, composite);
            }
            else {
                /* 创建一个解析了命令行参数的 SimpleCommandLinePropertySource 资源对象，并将其添加到环境资源的第一个位置
                 * SimpleCommandLinePropertySource：将命令行参数解析并封装到一个 CommandLineArgs 对象中
                 * CommandLineArgs 中有一个 Map<String, List<String>> 类型的 optionArgs 变量，其中 Map 的 Key 为参数名，Value 为参数值
                 */
                sources.addFirst(new SimpleCommandLinePropertySource(args));
            }
        }
        //创建一个包含当前服务进程 ID 的 ApplicationInfoPropertySource 资源对象，并将其添加到环境资源的最后一个位置
        environment.getPropertySources().addLast(new ApplicationInfoPropertySource(this.mainApplicationClass));
    }

    /**
     * Configure which profiles are active (or active by default) for this application
     * environment. Additional profiles may be activated during configuration file
     * processing through the {@code spring.profiles.active} property.
     * @param environment this application's environment
     * @param args arguments passed to the {@code run} method
     * @see #configureEnvironment(ConfigurableEnvironment, String[])
     */
    protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
    }

    /**
     * Bind the environment to the {@link ApplicationProperties}.
     * @param environment the environment to bind
     */
    protected void bindToSpringApplication(ConfigurableEnvironment environment) {
        try {
            /* Binder.get(environment)：获取配置属性绑定器，里面包含了所有环境资源、配置属性占位符解析器、配置属性绑定器
             * Bindable.ofInstance(this.properties)：获取 Bindable，里面主要包含了当前 this.properties 对象（当前 SpringApplication 的配置属性）
             * .bind("spring.main", Bindable.ofInstance(this.properties))：没看懂干了个啥
             */
            Binder.get(environment).bind("spring.main", Bindable.ofInstance(this.properties));
        }
        catch (Exception ex) {
            throw new IllegalStateException("Cannot bind to SpringApplication", ex);
        }
    }

    private Banner printBanner(ConfigurableEnvironment environment) {
        if (this.properties.getBannerMode(environment) == Banner.Mode.OFF) {
            return null;
        }
        ResourceLoader resourceLoader = (this.resourceLoader != null) ? this.resourceLoader
                : new DefaultResourceLoader(null);
        SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
        if (this.properties.getBannerMode(environment) == Mode.LOG) {
            return bannerPrinter.print(environment, this.mainApplicationClass, logger);
        }
        return bannerPrinter.print(environment, this.mainApplicationClass, System.out);
    }

    /**
     * Strategy method used to create the {@link ApplicationContext}. By default this
     * method will respect any explicitly set application context class or factory before
     * falling back to a suitable default.
     * @return the application context (not yet refreshed)
     * @see #setApplicationContextFactory(ApplicationContextFactory)
     */
    protected ConfigurableApplicationContext createApplicationContext() {
        return this.applicationContextFactory.create(this.properties.getWebApplicationType());
    }

    /**
     * Apply any relevant post-processing to the {@link ApplicationContext}. Subclasses
     * can apply additional processing as required.
     * @param context the application context
     */
    protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
        if (this.beanNameGenerator != null) {
            context.getBeanFactory()
                    .registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, this.beanNameGenerator);
        }
        if (this.resourceLoader != null) {
            if (context instanceof GenericApplicationContext genericApplicationContext) {
                genericApplicationContext.setResourceLoader(this.resourceLoader);
            }
            if (context instanceof DefaultResourceLoader defaultResourceLoader) {
                defaultResourceLoader.setClassLoader(this.resourceLoader.getClassLoader());
            }
        }
        if (this.addConversionService) {
            context.getBeanFactory().setConversionService(context.getEnvironment().getConversionService());
        }
    }

    /**
     * Apply any {@link ApplicationContextInitializer}s to the context before it is
     * refreshed.
     * @param context the configured ApplicationContext (not refreshed yet)
     * @see ConfigurableApplicationContext#refresh()
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void applyInitializers(ConfigurableApplicationContext context) {
        for (ApplicationContextInitializer initializer : getInitializers()) {
            Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(),
                    ApplicationContextInitializer.class);
            Assert.state(requiredType.isInstance(context), "Unable to call initializer");
            initializer.initialize(context);
        }
    }

    /**
     * Called to log startup information, subclasses may override to add additional
     * logging.
     * @param context the application context
     * @since 3.4.0
     */
    protected void logStartupInfo(ConfigurableApplicationContext context) {
        boolean isRoot = context.getParent() == null;
        if (isRoot) {
            new StartupInfoLogger(this.mainApplicationClass, context.getEnvironment()).logStarting(getApplicationLog());
        }
    }

    /**
     * Called to log startup information, subclasses may override to add additional
     * logging.
     * @param isRoot true if this application is the root of a context hierarchy
     * @deprecated since 3.4.0 for removal in 4.0.0 in favor of
     * {@link #logStartupInfo(ConfigurableApplicationContext)}
     */
    @Deprecated(since = "3.4.0", forRemoval = true)
    protected void logStartupInfo(boolean isRoot) {
    }

    /**
     * Called to log active profile information.
     * @param context the application context
     */
    protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
        Log log = getApplicationLog();
        if (log.isInfoEnabled()) {
            List<String> activeProfiles = quoteProfiles(context.getEnvironment().getActiveProfiles());
            if (ObjectUtils.isEmpty(activeProfiles)) {
                List<String> defaultProfiles = quoteProfiles(context.getEnvironment().getDefaultProfiles());
                String message = String.format("%s default %s: ", defaultProfiles.size(),
                        (defaultProfiles.size() <= 1) ? "profile" : "profiles");
                log.info("No active profile set, falling back to " + message
                        + StringUtils.collectionToDelimitedString(defaultProfiles, ", "));
            }
            else {
                String message = (activeProfiles.size() == 1) ? "1 profile is active: "
                        : activeProfiles.size() + " profiles are active: ";
                log.info("The following " + message + StringUtils.collectionToDelimitedString(activeProfiles, ", "));
            }
        }
    }

    private List<String> quoteProfiles(String[] profiles) {
        return Arrays.stream(profiles).map((profile) -> "\"" + profile + "\"").toList();
    }

    /**
     * Returns the {@link Log} for the application. By default will be deduced.
     * @return the application log
     */
    protected Log getApplicationLog() {
        if (this.mainApplicationClass == null) {
            return logger;
        }
        return LogFactory.getLog(this.mainApplicationClass);
    }

    /**
     * Load beans into the application context.
     * @param context the context to load beans into
     * @param sources the sources to load
     */
    protected void load(ApplicationContext context, Object[] sources) {
        if (logger.isDebugEnabled()) {
            logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
        }
        BeanDefinitionLoader loader = createBeanDefinitionLoader(getBeanDefinitionRegistry(context), sources);
        if (this.beanNameGenerator != null) {
            loader.setBeanNameGenerator(this.beanNameGenerator);
        }
        if (this.resourceLoader != null) {
            loader.setResourceLoader(this.resourceLoader);
        }
        if (this.environment != null) {
            loader.setEnvironment(this.environment);
        }
        loader.load();
    }

    /**
     * The ResourceLoader that will be used in the ApplicationContext.
     * @return the resourceLoader the resource loader that will be used in the
     * ApplicationContext (or null if the default)
     */
    public ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    /**
     * Either the ClassLoader that will be used in the ApplicationContext (if
     * {@link #setResourceLoader(ResourceLoader) resourceLoader} is set), or the context
     * class loader (if not null), or the loader of the Spring {@link ClassUtils} class.
     * @return a ClassLoader (never null)
     */
    public ClassLoader getClassLoader() {
        if (this.resourceLoader != null) {
            return this.resourceLoader.getClassLoader();
        }
        return ClassUtils.getDefaultClassLoader();
    }

    /**
     * Get the bean definition registry.
     * @param context the application context
     * @return the BeanDefinitionRegistry if it can be determined
     */
    private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
        if (context instanceof BeanDefinitionRegistry registry) {
            return registry;
        }
        if (context instanceof AbstractApplicationContext abstractApplicationContext) {
            return (BeanDefinitionRegistry) abstractApplicationContext.getBeanFactory();
        }
        throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
    }

    /**
     * Factory method used to create the {@link BeanDefinitionLoader}.
     * @param registry the bean definition registry
     * @param sources the sources to load
     * @return the {@link BeanDefinitionLoader} that will be used to load beans
     */
    protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
        return new BeanDefinitionLoader(registry, sources);
    }

    /**
     * Refresh the underlying {@link ApplicationContext}.
     * @param applicationContext the application context to refresh
     */
    protected void refresh(ConfigurableApplicationContext applicationContext) {
        applicationContext.refresh();
    }

    /**
     * Called after the context has been refreshed.
     * @param context the application context
     * @param args the application arguments
     */
    protected void afterRefresh(ConfigurableApplicationContext context, ApplicationArguments args) {
    }

    private void callRunners(ConfigurableApplicationContext context, ApplicationArguments args) {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        String[] beanNames = beanFactory.getBeanNamesForType(Runner.class);
        Map<Runner, String> instancesToBeanNames = new IdentityHashMap<>();
        for (String beanName : beanNames) {
            instancesToBeanNames.put(beanFactory.getBean(beanName, Runner.class), beanName);
        }
        Comparator<Object> comparator = getOrderComparator(beanFactory)
                .withSourceProvider(new FactoryAwareOrderSourceProvider(beanFactory, instancesToBeanNames));
        instancesToBeanNames.keySet().stream().sorted(comparator).forEach((runner) -> callRunner(runner, args));
    }

    private OrderComparator getOrderComparator(ConfigurableListableBeanFactory beanFactory) {
        Comparator<?> dependencyComparator = (beanFactory instanceof DefaultListableBeanFactory defaultListableBeanFactory)
                ? defaultListableBeanFactory.getDependencyComparator() : null;
        return (dependencyComparator instanceof OrderComparator orderComparator) ? orderComparator
                : AnnotationAwareOrderComparator.INSTANCE;
    }

    private void callRunner(Runner runner, ApplicationArguments args) {
        if (runner instanceof ApplicationRunner) {
            callRunner(ApplicationRunner.class, runner, (applicationRunner) -> applicationRunner.run(args));
        }
        if (runner instanceof CommandLineRunner) {
            callRunner(CommandLineRunner.class, runner,
                    (commandLineRunner) -> commandLineRunner.run(args.getSourceArgs()));
        }
    }

    @SuppressWarnings("unchecked")
    private <R extends Runner> void callRunner(Class<R> type, Runner runner, ThrowingConsumer<R> call) {
        call.throwing(
                        (message, ex) -> new IllegalStateException("Failed to execute " + ClassUtils.getShortName(type), ex))
                .accept((R) runner);
    }

    private RuntimeException handleRunFailure(ConfigurableApplicationContext context, Throwable exception,
            SpringApplicationRunListeners listeners) {
        if (exception instanceof AbandonedRunException abandonedRunException) {
            return abandonedRunException;
        }
        try {
            try {
                handleExitCode(context, exception);
                if (listeners != null) {
                    listeners.failed(context, exception);
                }
            }
            finally {
                reportFailure(getExceptionReporters(context), exception);
                if (context != null) {
                    context.close();
                    shutdownHook.deregisterFailedApplicationContext(context);
                }
            }
        }
        catch (Exception ex) {
            logger.warn("Unable to close ApplicationContext", ex);
        }
        return (exception instanceof RuntimeException runtimeException) ? runtimeException
                : new IllegalStateException(exception);
    }

    private Collection<SpringBootExceptionReporter> getExceptionReporters(ConfigurableApplicationContext context) {
        try {
            ArgumentResolver argumentResolver = ArgumentResolver.of(ConfigurableApplicationContext.class, context);
            return getSpringFactoriesInstances(SpringBootExceptionReporter.class, argumentResolver);
        }
        catch (Throwable ex) {
            return Collections.emptyList();
        }
    }

    private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters, Throwable failure) {
        try {
            for (SpringBootExceptionReporter reporter : exceptionReporters) {
                if (reporter.reportException(failure)) {
                    registerLoggedException(failure);
                    return;
                }
            }
        }
        catch (Throwable ex) {
            // Continue with normal handling of the original failure
        }
        if (logger.isErrorEnabled()) {
            if (NativeDetector.inNativeImage()) {
                // Depending on how early the failure was, logging may not work in a
                // native image so we output the stack trace directly to System.out
                // instead.
                System.out.println("Application run failed");
                failure.printStackTrace(System.out);
            }
            else {
                logger.error("Application run failed", failure);
            }
            registerLoggedException(failure);
        }
    }

    /**
     * Register that the given exception has been logged. By default, if the running in
     * the main thread, this method will suppress additional printing of the stacktrace.
     * @param exception the exception that was logged
     */
    protected void registerLoggedException(Throwable exception) {
        SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
        if (handler != null) {
            handler.registerLoggedException(exception);
        }
    }

    private void handleExitCode(ConfigurableApplicationContext context, Throwable exception) {
        int exitCode = getExitCodeFromException(context, exception);
        if (exitCode != 0) {
            if (context != null) {
                context.publishEvent(new ExitCodeEvent(context, exitCode));
            }
            SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
            if (handler != null) {
                handler.registerExitCode(exitCode);
            }
        }
    }

    private int getExitCodeFromException(ConfigurableApplicationContext context, Throwable exception) {
        int exitCode = getExitCodeFromMappedException(context, exception);
        if (exitCode == 0) {
            exitCode = getExitCodeFromExitCodeGeneratorException(exception);
        }
        return exitCode;
    }

    private int getExitCodeFromMappedException(ConfigurableApplicationContext context, Throwable exception) {
        if (context == null || !context.isActive()) {
            return 0;
        }
        ExitCodeGenerators generators = new ExitCodeGenerators();
        Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class).values();
        generators.addAll(exception, beans);
        return generators.getExitCode();
    }

    private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
        if (exception == null) {
            return 0;
        }
        if (exception instanceof ExitCodeGenerator generator) {
            return generator.getExitCode();
        }
        return getExitCodeFromExitCodeGeneratorException(exception.getCause());
    }

    SpringBootExceptionHandler getSpringBootExceptionHandler() {
        if (isMainThread(Thread.currentThread())) {
            return SpringBootExceptionHandler.forCurrentThread();
        }
        return null;
    }

    private boolean isMainThread(Thread currentThread) {
        return ("main".equals(currentThread.getName()) || "restartedMain".equals(currentThread.getName()))
                && "main".equals(currentThread.getThreadGroup().getName());
    }

    /**
     * Returns the main application class that has been deduced or explicitly configured.
     * @return the main application class or {@code null}
     */
    public Class<?> getMainApplicationClass() {
        return this.mainApplicationClass;
    }

    /**
     * Set a specific main application class that will be used as a log source and to
     * obtain version information. By default the main application class will be deduced.
     * Can be set to {@code null} if there is no explicit application class.
     * @param mainApplicationClass the mainApplicationClass to set or {@code null}
     */
    public void setMainApplicationClass(Class<?> mainApplicationClass) {
        this.mainApplicationClass = mainApplicationClass;
    }

    /**
     * Returns the type of web application that is being run.
     * @return the type of web application
     * @since 2.0.0
     */
    public WebApplicationType getWebApplicationType() {
        return this.properties.getWebApplicationType();
    }

    /**
     * Sets the type of web application to be run. If not explicitly set the type of web
     * application will be deduced based on the classpath.
     * @param webApplicationType the web application type
     * @since 2.0.0
     */
    public void setWebApplicationType(WebApplicationType webApplicationType) {
        Assert.notNull(webApplicationType, "'webApplicationType' must not be null");
        this.properties.setWebApplicationType(webApplicationType);
    }

    /**
     * Sets if bean definition overriding, by registering a definition with the same name
     * as an existing definition, should be allowed. Defaults to {@code false}.
     * @param allowBeanDefinitionOverriding if overriding is allowed
     * @since 2.1.0
     * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)
     */
    public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
        this.properties.setAllowBeanDefinitionOverriding(allowBeanDefinitionOverriding);
    }

    /**
     * Sets whether to allow circular references between beans and automatically try to
     * resolve them. Defaults to {@code false}.
     * @param allowCircularReferences if circular references are allowed
     * @since 2.6.0
     * @see AbstractAutowireCapableBeanFactory#setAllowCircularReferences(boolean)
     */
    public void setAllowCircularReferences(boolean allowCircularReferences) {
        this.properties.setAllowCircularReferences(allowCircularReferences);
    }

    /**
     * Sets if beans should be initialized lazily. Defaults to {@code false}.
     * @param lazyInitialization if initialization should be lazy
     * @since 2.2
     * @see BeanDefinition#setLazyInit(boolean)
     */
    public void setLazyInitialization(boolean lazyInitialization) {
        this.properties.setLazyInitialization(lazyInitialization);
    }

    /**
     * Sets if the application is headless and should not instantiate AWT. Defaults to
     * {@code true} to prevent java icons appearing.
     * @param headless if the application is headless
     */
    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    /**
     * Sets if the created {@link ApplicationContext} should have a shutdown hook
     * registered. Defaults to {@code true} to ensure that JVM shutdowns are handled
     * gracefully.
     * @param registerShutdownHook if the shutdown hook should be registered
     * @see #getShutdownHandlers()
     */
    public void setRegisterShutdownHook(boolean registerShutdownHook) {
        this.properties.setRegisterShutdownHook(registerShutdownHook);
    }

    /**
     * Sets the {@link Banner} instance which will be used to print the banner when no
     * static banner file is provided.
     * @param banner the Banner instance to use
     */
    public void setBanner(Banner banner) {
        this.banner = banner;
    }

    /**
     * Sets the mode used to display the banner when the application runs. Defaults to
     * {@code Banner.Mode.CONSOLE}.
     * @param bannerMode the mode used to display the banner
     */
    public void setBannerMode(Banner.Mode bannerMode) {
        this.properties.setBannerMode(bannerMode);
    }

    /**
     * Sets if the application information should be logged when the application starts.
     * Defaults to {@code true}.
     * @param logStartupInfo if startup info should be logged.
     */
    public void setLogStartupInfo(boolean logStartupInfo) {
        this.properties.setLogStartupInfo(logStartupInfo);
    }

    /**
     * Sets if a {@link CommandLinePropertySource} should be added to the application
     * context in order to expose arguments. Defaults to {@code true}.
     * @param addCommandLineProperties if command line arguments should be exposed
     */
    public void setAddCommandLineProperties(boolean addCommandLineProperties) {
        this.addCommandLineProperties = addCommandLineProperties;
    }

    /**
     * Sets if the {@link ApplicationConversionService} should be added to the application
     * context's {@link Environment}.
     * @param addConversionService if the application conversion service should be added
     * @since 2.1.0
     */
    public void setAddConversionService(boolean addConversionService) {
        this.addConversionService = addConversionService;
    }

    /**
     * Adds {@link BootstrapRegistryInitializer} instances that can be used to initialize
     * the {@link BootstrapRegistry}.
     * @param bootstrapRegistryInitializer the bootstrap registry initializer to add
     * @since 2.4.5
     */
    public void addBootstrapRegistryInitializer(BootstrapRegistryInitializer bootstrapRegistryInitializer) {
        Assert.notNull(bootstrapRegistryInitializer, "'bootstrapRegistryInitializer' must not be null");
        this.bootstrapRegistryInitializers.addAll(Arrays.asList(bootstrapRegistryInitializer));
    }

    /**
     * Set default environment properties which will be used in addition to those in the
     * existing {@link Environment}.
     * @param defaultProperties the additional properties to set
     */
    public void setDefaultProperties(Map<String, Object> defaultProperties) {
        this.defaultProperties = defaultProperties;
    }

    /**
     * Convenient alternative to {@link #setDefaultProperties(Map)}.
     * @param defaultProperties some {@link Properties}
     */
    public void setDefaultProperties(Properties defaultProperties) {
        this.defaultProperties = new HashMap<>();
        for (Object key : Collections.list(defaultProperties.propertyNames())) {
            this.defaultProperties.put((String) key, defaultProperties.get(key));
        }
    }

    /**
     * Set additional profile values to use (on top of those set in system or command line
     * properties).
     * @param profiles the additional profiles to set
     */
    public void setAdditionalProfiles(String... profiles) {
        this.additionalProfiles = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(profiles)));
    }

    /**
     * Return an immutable set of any additional profiles in use.
     * @return the additional profiles
     */
    public Set<String> getAdditionalProfiles() {
        return this.additionalProfiles;
    }

    /**
     * Sets the bean name generator that should be used when generating bean names.
     * @param beanNameGenerator the bean name generator
     */
    public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator = beanNameGenerator;
    }

    /**
     * Sets the underlying environment that should be used with the created application
     * context.
     * @param environment the environment
     */
    public void setEnvironment(ConfigurableEnvironment environment) {
        this.isCustomEnvironment = true;
        this.environment = environment;
    }

    /**
     * Add additional items to the primary sources that will be added to an
     * ApplicationContext when {@link #run(String...)} is called.
     * <p>
     * The sources here are added to those that were set in the constructor. Most users
     * should consider using {@link #getSources()}/{@link #setSources(Set)} rather than
     * calling this method.
     * @param additionalPrimarySources the additional primary sources to add
     * @see #SpringApplication(Class...)
     * @see #getSources()
     * @see #setSources(Set)
     * @see #getAllSources()
     */
    public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
        this.primarySources.addAll(additionalPrimarySources);
    }

    /**
     * Returns a mutable set of the sources that will be added to an ApplicationContext
     * when {@link #run(String...)} is called.
     * <p>
     * Sources set here will be used in addition to any primary sources set in the
     * constructor.
     * @return the application sources.
     * @see #SpringApplication(Class...)
     * @see #getAllSources()
     */
    public Set<String> getSources() {
        return this.properties.getSources();
    }

    /**
     * Set additional sources that will be used to create an ApplicationContext. A source
     * can be: a class name, package name, or an XML resource location.
     * <p>
     * Sources set here will be used in addition to any primary sources set in the
     * constructor.
     * @param sources the application sources to set
     * @see #SpringApplication(Class...)
     * @see #getAllSources()
     */
    public void setSources(Set<String> sources) {
        Assert.notNull(sources, "'sources' must not be null");
        this.properties.setSources(sources);
    }

    /**
     * Return an immutable set of all the sources that will be added to an
     * ApplicationContext when {@link #run(String...)} is called. This method combines any
     * primary sources specified in the constructor with any additional ones that have
     * been {@link #setSources(Set) explicitly set}.
     * @return an immutable set of all sources
     */
    public Set<Object> getAllSources() {
        Set<Object> allSources = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(this.primarySources)) {
            allSources.addAll(this.primarySources);
        }
        if (!CollectionUtils.isEmpty(this.properties.getSources())) {
            allSources.addAll(this.properties.getSources());
        }
        return Collections.unmodifiableSet(allSources);
    }

    /**
     * Sets the {@link ResourceLoader} that should be used when loading resources.
     * @param resourceLoader the resource loader
     */
    public void setResourceLoader(ResourceLoader resourceLoader) {
        Assert.notNull(resourceLoader, "'resourceLoader' must not be null");
        this.resourceLoader = resourceLoader;
    }

    /**
     * Return a prefix that should be applied when obtaining configuration properties from
     * the system environment.
     * @return the environment property prefix
     * @since 2.5.0
     */
    public String getEnvironmentPrefix() {
        return this.environmentPrefix;
    }

    /**
     * Set the prefix that should be applied when obtaining configuration properties from
     * the system environment.
     * @param environmentPrefix the environment property prefix to set
     * @since 2.5.0
     */
    public void setEnvironmentPrefix(String environmentPrefix) {
        this.environmentPrefix = environmentPrefix;
    }

    /**
     * Sets the factory that will be called to create the application context. If not set,
     * defaults to a factory that will create
     * {@link AnnotationConfigServletWebServerApplicationContext} for servlet web
     * applications, {@link AnnotationConfigReactiveWebServerApplicationContext} for
     * reactive web applications, and {@link AnnotationConfigApplicationContext} for
     * non-web applications.
     * @param applicationContextFactory the factory for the context
     * @since 2.4.0
     */
    public void setApplicationContextFactory(ApplicationContextFactory applicationContextFactory) {
        this.applicationContextFactory = (applicationContextFactory != null) ? applicationContextFactory
                : ApplicationContextFactory.DEFAULT;
    }

    /**
     * Sets the {@link ApplicationContextInitializer} that will be applied to the Spring
     * {@link ApplicationContext}.
     * @param initializers the initializers to set
     */
    public void setInitializers(Collection<? extends ApplicationContextInitializer<?>> initializers) {
        this.initializers = new ArrayList<>(initializers);
    }

    /**
     * Add {@link ApplicationContextInitializer}s to be applied to the Spring
     * {@link ApplicationContext}.
     * @param initializers the initializers to add
     */
    public void addInitializers(ApplicationContextInitializer<?>... initializers) {
        this.initializers.addAll(Arrays.asList(initializers));
    }

    /**
     * Returns read-only ordered Set of the {@link ApplicationContextInitializer}s that
     * will be applied to the Spring {@link ApplicationContext}.
     * @return the initializers
     */
    public Set<ApplicationContextInitializer<?>> getInitializers() {
        return asUnmodifiableOrderedSet(this.initializers);
    }

    /**
     * Sets the {@link ApplicationListener}s that will be applied to the SpringApplication
     * and registered with the {@link ApplicationContext}.
     * @param listeners the listeners to set
     */
    public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
        this.listeners = new ArrayList<>(listeners);
    }

    /**
     * Add {@link ApplicationListener}s to be applied to the SpringApplication and
     * registered with the {@link ApplicationContext}.
     * @param listeners the listeners to add
     */
    public void addListeners(ApplicationListener<?>... listeners) {
        this.listeners.addAll(Arrays.asList(listeners));
    }

    /**
     * Returns read-only ordered Set of the {@link ApplicationListener}s that will be
     * applied to the SpringApplication and registered with the {@link ApplicationContext}
     * .
     * @return the listeners
     */
    public Set<ApplicationListener<?>> getListeners() {
        return asUnmodifiableOrderedSet(this.listeners);
    }

    /**
     * Set the {@link ApplicationStartup} to use for collecting startup metrics.
     * @param applicationStartup the application startup to use
     * @since 2.4.0
     */
    public void setApplicationStartup(ApplicationStartup applicationStartup) {
        this.applicationStartup = (applicationStartup != null) ? applicationStartup : ApplicationStartup.DEFAULT;
    }

    /**
     * Returns the {@link ApplicationStartup} used for collecting startup metrics.
     * @return the application startup
     * @since 2.4.0
     */
    public ApplicationStartup getApplicationStartup() {
        return this.applicationStartup;
    }

    /**
     * Whether to keep the application alive even if there are no more non-daemon threads.
     * @return whether to keep the application alive even if there are no more non-daemon
     * threads
     * @since 3.2.0
     */
    public boolean isKeepAlive() {
        return this.properties.isKeepAlive();
    }

    /**
     * Set whether to keep the application alive even if there are no more non-daemon
     * threads.
     * @param keepAlive whether to keep the application alive even if there are no more
     * non-daemon threads
     * @since 3.2.0
     */
    public void setKeepAlive(boolean keepAlive) {
        this.properties.setKeepAlive(keepAlive);
    }

    /**
     * Return a {@link SpringApplicationShutdownHandlers} instance that can be used to add
     * or remove handlers that perform actions before the JVM is shutdown.
     * @return a {@link SpringApplicationShutdownHandlers} instance
     * @since 2.5.1
     */
    public static SpringApplicationShutdownHandlers getShutdownHandlers() {
        return shutdownHook.getHandlers();
    }

    /**
     * Static helper that can be used to run a {@link SpringApplication} from the
     * specified source using default settings.
     * @param primarySource the primary source to load
     * @param args the application arguments (usually passed from a Java main method)
     * @return the running {@link ApplicationContext}
     */
    public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
        return run(new Class<?>[] { primarySource }, args);
    }

    /**
     * Static helper that can be used to run a {@link SpringApplication} from the
     * specified sources using default settings and user supplied arguments.
     * @param primarySources the primary sources to load
     * @param args the application arguments (usually passed from a Java main method)
     * @return the running {@link ApplicationContext}
     */
    public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
        return new SpringApplication(primarySources).run(args);
    }

    /**
     * A basic main that can be used to launch an application. This method is useful when
     * application sources are defined through a {@literal --spring.main.sources} command
     * line argument.
     * <p>
     * Most developers will want to define their own main method and call the
     * {@link #run(Class, String...) run} method instead.
     * @param args command line arguments
     * @throws Exception if the application cannot be started
     * @see SpringApplication#run(Class[], String[])
     * @see SpringApplication#run(Class, String...)
     */
    public static void main(String[] args) throws Exception {
        SpringApplication.run(new Class<?>[0], args);
    }

    /**
     * Static helper that can be used to exit a {@link SpringApplication} and obtain a
     * code indicating success (0) or otherwise. Does not throw exceptions but should
     * print stack traces of any encountered. Applies the specified
     * {@link ExitCodeGenerator ExitCodeGenerators} in addition to any Spring beans that
     * implement {@link ExitCodeGenerator}. When multiple generators are available, the
     * first non-zero exit code is used. Generators are ordered based on their
     * {@link Ordered} implementation and {@link Order @Order} annotation.
     * @param context the context to close if possible
     * @param exitCodeGenerators exit code generators
     * @return the outcome (0 if successful)
     */
    public static int exit(ApplicationContext context, ExitCodeGenerator... exitCodeGenerators) {
        Assert.notNull(context, "'context' must not be null");
        int exitCode = 0;
        try {
            try {
                ExitCodeGenerators generators = new ExitCodeGenerators();
                Collection<ExitCodeGenerator> beans = context.getBeansOfType(ExitCodeGenerator.class).values();
                generators.addAll(exitCodeGenerators);
                generators.addAll(beans);
                exitCode = generators.getExitCode();
                if (exitCode != 0) {
                    context.publishEvent(new ExitCodeEvent(context, exitCode));
                }
            }
            finally {
                close(context);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
            exitCode = (exitCode != 0) ? exitCode : 1;
        }
        return exitCode;
    }

    /**
     * Create an application from an existing {@code main} method that can run with
     * additional {@code @Configuration} or bean classes. This method can be helpful when
     * writing a test harness that needs to start an application with additional
     * configuration.
     * @param main the main method entry point that runs the {@link SpringApplication}
     * @return a {@link SpringApplication.Augmented} instance that can be used to add
     * configuration and run the application
     * @since 3.1.0
     * @see #withHook(SpringApplicationHook, Runnable)
     */
    public static SpringApplication.Augmented from(ThrowingConsumer<String[]> main) {
        Assert.notNull(main, "'main' must not be null");
        return new Augmented(main, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Perform the given action with the given {@link SpringApplicationHook} attached if
     * the action triggers an {@link SpringApplication#run(String...) application run}.
     * @param hook the hook to apply
     * @param action the action to run
     * @since 3.0.0
     * @see #withHook(SpringApplicationHook, ThrowingSupplier)
     */
    public static void withHook(SpringApplicationHook hook, Runnable action) {
        withHook(hook, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Perform the given action with the given {@link SpringApplicationHook} attached if
     * the action triggers an {@link SpringApplication#run(String...) application run}.
     * @param <T> the result type
     * @param hook the hook to apply
     * @param action the action to run
     * @return the result of the action
     * @since 3.0.0
     * @see #withHook(SpringApplicationHook, Runnable)
     */
    public static <T> T withHook(SpringApplicationHook hook, ThrowingSupplier<T> action) {
        applicationHook.set(hook);
        try {
            return action.get();
        }
        finally {
            applicationHook.remove();
        }
    }

    private static void close(ApplicationContext context) {
        if (context instanceof ConfigurableApplicationContext closable) {
            closable.close();
        }
    }

    private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
        List<E> list = new ArrayList<>(elements);
        list.sort(AnnotationAwareOrderComparator.INSTANCE);
        return new LinkedHashSet<>(list);
    }

    /**
     * Used to configure and run an augmented {@link SpringApplication} where additional
     * configuration should be applied.
     *
     * @since 3.1.0
     */
    public static class Augmented {

        private final ThrowingConsumer<String[]> main;

        private final Set<Class<?>> sources;

        private final Set<String> additionalProfiles;

        Augmented(ThrowingConsumer<String[]> main, Set<Class<?>> sources, Set<String> additionalProfiles) {
            this.main = main;
            this.sources = Set.copyOf(sources);
            this.additionalProfiles = additionalProfiles;
        }

        /**
         * Return a new {@link SpringApplication.Augmented} instance with additional
         * sources that should be applied when the application runs.
         * @param sources the sources that should be applied
         * @return a new {@link SpringApplication.Augmented} instance
         */
        public Augmented with(Class<?>... sources) {
            LinkedHashSet<Class<?>> merged = new LinkedHashSet<>(this.sources);
            merged.addAll(Arrays.asList(sources));
            return new Augmented(this.main, merged, this.additionalProfiles);
        }

        /**
         * Return a new {@link SpringApplication.Augmented} instance with additional
         * profiles that should be applied when the application runs.
         * @param profiles the profiles that should be applied
         * @return a new {@link SpringApplication.Augmented} instance
         * @since 3.4.0
         */
        public Augmented withAdditionalProfiles(String... profiles) {
            Set<String> merged = new LinkedHashSet<>(this.additionalProfiles);
            merged.addAll(Arrays.asList(profiles));
            return new Augmented(this.main, this.sources, merged);
        }

        /**
         * Run the application using the given args.
         * @param args the main method args
         * @return the running {@link ApplicationContext}
         */
        public SpringApplication.Running run(String... args) {
            RunListener runListener = new RunListener();
            SpringApplicationHook hook = new SingleUseSpringApplicationHook((springApplication) -> {
                springApplication.addPrimarySources(this.sources);
                springApplication.setAdditionalProfiles(this.additionalProfiles.toArray(String[]::new));
                return runListener;
            });
            withHook(hook, () -> this.main.accept(args));
            return runListener;
        }

        /**
         * {@link SpringApplicationRunListener} to capture {@link Running} application
         * details.
         */
        private static final class RunListener implements SpringApplicationRunListener, Running {

            private final List<ConfigurableApplicationContext> contexts = Collections
                    .synchronizedList(new ArrayList<>());

            @Override
            public void contextLoaded(ConfigurableApplicationContext context) {
                this.contexts.add(context);
            }

            @Override
            public ConfigurableApplicationContext getApplicationContext() {
                List<ConfigurableApplicationContext> rootContexts = this.contexts.stream()
                        .filter((context) -> context.getParent() == null)
                        .toList();
                Assert.state(!rootContexts.isEmpty(), "No root application context located");
                Assert.state(rootContexts.size() == 1, "No unique root application context located");
                return rootContexts.get(0);
            }

        }

    }

    /**
     * Provides access to details of a {@link SpringApplication} run using
     * {@link Augmented#run(String...)}.
     *
     * @since 3.1.0
     */
    public interface Running {

        /**
         * Return the root {@link ConfigurableApplicationContext} of the running
         * application.
         * @return the root application context
         */
        ConfigurableApplicationContext getApplicationContext();

    }

    /**
     * {@link BeanFactoryPostProcessor} to re-order our property sources below any
     * {@code @PropertySource} items added by the {@link ConfigurationClassPostProcessor}.
     */
    private static class PropertySourceOrderingBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

        private final ConfigurableApplicationContext context;

        PropertySourceOrderingBeanFactoryPostProcessor(ConfigurableApplicationContext context) {
            this.context = context;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            DefaultPropertiesPropertySource.moveToEnd(this.context.getEnvironment());
        }

    }

    /**
     * Exception that can be thrown to silently exit a running {@link SpringApplication}
     * without handling run failures.
     *
     * @since 3.0.0
     */
    public static class AbandonedRunException extends RuntimeException {

        private final ConfigurableApplicationContext applicationContext;

        /**
         * Create a new {@link AbandonedRunException} instance.
         */
        public AbandonedRunException() {
            this(null);
        }

        /**
         * Create a new {@link AbandonedRunException} instance with the given application
         * context.
         * @param applicationContext the application context that was available when the
         * run was abandoned
         */
        public AbandonedRunException(ConfigurableApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        /**
         * Return the application context that was available when the run was abandoned or
         * {@code null} if no context was available.
         * @return the application context
         */
        public ConfigurableApplicationContext getApplicationContext() {
            return this.applicationContext;
        }

    }

    /**
     * {@link SpringApplicationHook} decorator that ensures the hook is only used once.
     */
    private static final class SingleUseSpringApplicationHook implements SpringApplicationHook {

        private final AtomicBoolean used = new AtomicBoolean();

        private final SpringApplicationHook delegate;

        private SingleUseSpringApplicationHook(SpringApplicationHook delegate) {
            this.delegate = delegate;
        }

        @Override
        public SpringApplicationRunListener getRunListener(SpringApplication springApplication) {
            return this.used.compareAndSet(false, true) ? this.delegate.getRunListener(springApplication) : null;
        }

    }

    /**
     * Starts a non-daemon thread to keep the JVM alive on {@link ContextRefreshedEvent}.
     * Stops the thread on {@link ContextClosedEvent}.
     */
    private static final class KeepAlive implements ApplicationListener<ApplicationContextEvent> {

        private final AtomicReference<Thread> thread = new AtomicReference<>();

        @Override
        public void onApplicationEvent(ApplicationContextEvent event) {
            if (event instanceof ContextRefreshedEvent) {
                startKeepAliveThread();
            }
            else if (event instanceof ContextClosedEvent) {
                stopKeepAliveThread();
            }
        }

        private void startKeepAliveThread() {
            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    }
                    catch (InterruptedException ex) {
                        break;
                    }
                }
            });
            if (this.thread.compareAndSet(null, thread)) {
                thread.setDaemon(false);
                thread.setName("keep-alive");
                thread.start();
            }
        }

        private void stopKeepAliveThread() {
            Thread thread = this.thread.getAndSet(null);
            if (thread == null) {
                return;
            }
            thread.interrupt();
        }

    }

    /**
     * Strategy used to handle startup concerns.
     */
    abstract static class Startup {

        private Duration timeTakenToStarted;

        protected abstract long startTime();

        protected abstract Long processUptime();

        protected abstract String action();

        final Duration started() {
            long now = System.currentTimeMillis();
            this.timeTakenToStarted = Duration.ofMillis(now - startTime());
            return this.timeTakenToStarted;
        }

        Duration timeTakenToStarted() {
            return this.timeTakenToStarted;
        }

        private Duration ready() {
            long now = System.currentTimeMillis();
            return Duration.ofMillis(now - startTime());
        }

        static Startup create() {
            ClassLoader classLoader = Startup.class.getClassLoader();
            return (ClassUtils.isPresent("jdk.crac.management.CRaCMXBean", classLoader)
                    && ClassUtils.isPresent("org.crac.management.CRaCMXBean", classLoader))
                    /* 条件为 false，创建并返回当前类的子类 StandardStartup 对象，该对象中什么也没做，
                     * 只记录了容器启动时间，即初始化了其成员变量 startTime = System.currentTimeMillis();
                     */
                    ? new CoordinatedRestoreAtCheckpointStartup() : new StandardStartup();
        }

    }

    /**
     * Standard {@link Startup} implementation.
     */
    private static final class StandardStartup extends Startup {

        //容器启动时间，也即容器初始化时间。
        private final Long startTime = System.currentTimeMillis();

        @Override
        protected long startTime() {
            return this.startTime;
        }

        @Override
        protected Long processUptime() {
            try {
                return ManagementFactory.getRuntimeMXBean().getUptime();
            }
            catch (Throwable ex) {
                return null;
            }
        }

        @Override
        protected String action() {
            return "Started";
        }

    }

    /**
     * Coordinated-Restore-At-Checkpoint {@link Startup} implementation.
     */
    private static final class CoordinatedRestoreAtCheckpointStartup extends Startup {

        private final StandardStartup fallback = new StandardStartup();

        @Override
        protected Long processUptime() {
            long uptime = CRaCMXBean.getCRaCMXBean().getUptimeSinceRestore();
            return (uptime >= 0) ? uptime : this.fallback.processUptime();
        }

        @Override
        protected String action() {
            return (restoreTime() >= 0) ? "Restored" : this.fallback.action();
        }

        private long restoreTime() {
            return CRaCMXBean.getCRaCMXBean().getRestoreTime();
        }

        @Override
        protected long startTime() {
            long restoreTime = restoreTime();
            return (restoreTime >= 0) ? restoreTime : this.fallback.startTime();
        }

    }

    /**
     * {@link OrderSourceProvider} used to obtain factory method and target type order
     * sources. Based on internal {@link DefaultListableBeanFactory} code.
     */
    private class FactoryAwareOrderSourceProvider implements OrderSourceProvider {

        private final ConfigurableBeanFactory beanFactory;

        private final Map<?, String> instancesToBeanNames;

        FactoryAwareOrderSourceProvider(ConfigurableBeanFactory beanFactory, Map<?, String> instancesToBeanNames) {
            this.beanFactory = beanFactory;
            this.instancesToBeanNames = instancesToBeanNames;
        }

        @Override
        public Object getOrderSource(Object obj) {
            String beanName = this.instancesToBeanNames.get(obj);
            return (beanName != null) ? getOrderSource(beanName, obj.getClass()) : null;
        }

        private Object getOrderSource(String beanName, Class<?> instanceType) {
            try {
                RootBeanDefinition beanDefinition = (RootBeanDefinition) this.beanFactory
                        .getMergedBeanDefinition(beanName);
                Method factoryMethod = beanDefinition.getResolvedFactoryMethod();
                Class<?> targetType = beanDefinition.getTargetType();
                targetType = (targetType != instanceType) ? targetType : null;
                return Stream.of(factoryMethod, targetType).filter(Objects::nonNull).toArray();
            }
            catch (NoSuchBeanDefinitionException ex) {
                return null;
            }
        }

    }

}
