package com.ploybot.promptshield.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Obfuscates {@code String} arguments of Spring bean methods.
 *
 * <ul>
 *   <li>On a parameter: only that argument is obfuscated.</li>
 *   <li>On a method or class: every {@code String} argument is obfuscated.</li>
 * </ul>
 *
 * <p>With {@code restore = true} (method or class level) each invocation uses its own
 * scope: a {@code String} return value is restored and the mappings are released when
 * the method returns. Without it, tags are created in the engine's default scope so
 * they can be restored later with {@code ObfuscationEngine#restaurar}; that scope is
 * shared by every caller, so prefer explicit scopes for multi-user data.
 */
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Obfuscate {
    /** Types to obfuscate; all registered types when empty. */
    String[] types() default {};

    /** Restores obfuscated tags in a {@code String} return value. */
    boolean restore() default false;
}
