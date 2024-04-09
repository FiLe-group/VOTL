package dev.fileeditor.votl.objects.annotation;

import java.lang.annotation.*;

/**
 * The annotated element must not be null.
 *
 * @see dev.fileeditor.votl.objects.annotation.Nullable
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Nonnull {}
