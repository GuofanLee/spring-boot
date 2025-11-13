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

package org.springframework.boot.my.test.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.stereotype.Component;

/**
 * 请填写类的描述
 *
 * @author GuofanLee
 * @since 2025-11-13 10:27
 */
@Aspect
@Component
public class LogAspect {

    @Pointcut("execution(* org.springframework.boot.my.test.service.impl.*.*(..))")
    public void pointcut() {}

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint pjp) {
        Object result;
        try {
            System.out.println("环绕通知-前置通知");
            result = pjp.proceed();
            System.out.println("环绕通知-返回通知");
        } catch (Throwable throwable) {
            System.out.println("环绕通知-异常通知");
            throw new RuntimeException(throwable);
        } finally {
            System.out.println("环绕通知-后置通知");
        }
        return result;
    }

}
