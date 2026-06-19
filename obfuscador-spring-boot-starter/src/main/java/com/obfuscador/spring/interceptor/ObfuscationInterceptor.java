package com.obfuscador.spring.interceptor;

import com.obfuscador.engine.ObfuscationEngine;
import com.obfuscador.spring.annotation.Obfuscate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Parameter;

@Aspect
public class ObfuscationInterceptor {

    private final ObfuscationEngine engine;

    public ObfuscationInterceptor(ObfuscationEngine engine) {
        this.engine = engine;
    }

    @Around("@annotation(com.obfuscador.spring.annotation.Obfuscate) || @within(com.obfuscador.spring.annotation.Obfuscate)")
    public Object handleObfuscation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        Obfuscate methodAnnotation = signature.getMethod().getAnnotation(Obfuscate.class);

        for (int i = 0; i < parameters.length; i++) {
            Obfuscate paramAnnotation = parameters[i].getAnnotation(Obfuscate.class);
            Obfuscate annotation = paramAnnotation != null ? paramAnnotation : methodAnnotation;

            if (annotation != null && args[i] instanceof String) {
                String value = (String) args[i];
                if (annotation.types().length > 0) {
                    for (String type : annotation.types()) {
                        value = engine.ofuscar(value, type);
                    }
                } else {
                    value = engine.ofuscar(value);
                }
                args[i] = value;
            }
        }

        Object result = joinPoint.proceed(args);

        if (methodAnnotation != null && methodAnnotation.restore() && result instanceof String) {
            result = engine.restaurar((String) result);
        }

        return result;
    }
}
