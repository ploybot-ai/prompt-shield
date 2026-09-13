package com.ploybot.promptshield.spring.interceptor;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.engine.ObfuscationScope;
import com.ploybot.promptshield.spring.annotation.Obfuscate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

@Aspect
public class ObfuscationInterceptor {

    private final ObfuscationEngine engine;

    public ObfuscationInterceptor(ObfuscationEngine engine) {
        this.engine = engine;
    }

    @Around("@annotation(com.ploybot.promptshield.spring.annotation.Obfuscate)"
            + " || @within(com.ploybot.promptshield.spring.annotation.Obfuscate)"
            + " || execution(* *(.., @com.ploybot.promptshield.spring.annotation.Obfuscate (*), ..))")
    public Object handleObfuscation(ProceedingJoinPoint joinPoint) throws Throwable {
        Method declared = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? AopUtils.getTargetClass(joinPoint.getTarget())
                : declared.getDeclaringClass();
        Method method = AopUtils.getMostSpecificMethod(declared, targetClass);

        Obfuscate methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, Obfuscate.class);
        if (methodAnnotation == null) {
            methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(declared, Obfuscate.class);
        }
        if (methodAnnotation == null) {
            methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, Obfuscate.class);
        }

        boolean restore = methodAnnotation != null && methodAnnotation.restore();
        ObfuscationScope scope = restore
                ? engine.forScope("obfuscate-" + UUID.randomUUID())
                : engine.forScope(defaultScopeId());

        try {
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < args.length; i++) {
                Obfuscate parameterAnnotation = parameterAnnotation(method, declared, i);
                Obfuscate annotation = parameterAnnotation != null ? parameterAnnotation : methodAnnotation;
                if (annotation != null && args[i] instanceof String value) {
                    args[i] = annotation.types().length > 0
                            ? scope.obfuscate(value, List.of(annotation.types()))
                            : scope.obfuscate(value);
                }
            }

            Object result = joinPoint.proceed(args);
            return restore && result instanceof String text ? scope.restore(text) : result;
        } finally {
            if (restore) {
                scope.removeCreatedMappings();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private String defaultScopeId() {
        return engine.getConfig().getScopeId();
    }

    private static Obfuscate parameterAnnotation(Method method, Method declared, int index) {
        Obfuscate annotation = AnnotatedElementUtils.findMergedAnnotation(method.getParameters()[index], Obfuscate.class);
        if (annotation == null && method != declared) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(declared.getParameters()[index], Obfuscate.class);
        }
        return annotation;
    }
}
