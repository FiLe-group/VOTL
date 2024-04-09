package dev.fileeditor.votl.objects.annotation;

import java.lang.annotation.*;

/**
 * The annotated element could be null under some circumstances.
 *
 * @see dev.fileeditor.votl.objects.annotation.Nonnull
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Nullable {}
