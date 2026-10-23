package com.vertice.api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates an exercise video link: a YouTube video the client app can play (client workout
 * experience R61). Null or empty passes (no video). The rules mirror vertice-mobile's
 * {@code youtubeVideoId} (src/lib/video.ts) so the phone and the API agree on every link.
 */
@Documented
@Constraint(validatedBy = YouTubeVideoUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface YouTubeVideoUrl {

    String message() default "must be a YouTube video link (youtube.com or youtu.be)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
