package com.cmbc.oms.infrastructure.facadeimpl.apama.anno;

import java.lang.annotation.*;

/**
 * @Author: Cly
 * @Date: 2026/01/23  19:09
 * @Description:
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface EventField {

    String name() default "";

    int order() default 99999;

    String fieldType() default "";
}
