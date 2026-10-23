package com.vertice.api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The same reading as vertice-mobile's {@code youtubeVideoId}: an http(s) URL whose host, without
 * a leading {@code www.} or {@code m.}, is youtu.be (id = first path segment) or youtube.com /
 * youtube-nocookie.com ({@code /watch?v=}, or {@code /shorts/}, {@code /embed/}, {@code /live/}
 * + id), with an 11-character {@code [A-Za-z0-9_-]} id.
 */
public class YouTubeVideoUrlValidator implements ConstraintValidator<YouTubeVideoUrl, String> {

    private static final Pattern VIDEO_ID = Pattern.compile("^[\\w-]{11}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isEmpty() || videoId(value).isPresent();
    }

    /** The video id of a YouTube watch/short/embed/live/youtu.be link, if it is one. */
    public static Optional<String> videoId(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http") || uri.getHost() == null) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^(www|m)\\.", "");
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String id = null;
        if (host.equals("youtu.be")) {
            id = path.length() > 1 ? path.substring(1).split("/")[0] : null;
        } else if (host.equals("youtube.com") || host.equals("youtube-nocookie.com")) {
            if (path.equals("/watch")) {
                id = queryParam(uri.getRawQuery(), "v");
            } else {
                String[] parts = path.split("/");
                if (parts.length >= 3 && (parts[1].equals("shorts") || parts[1].equals("embed") || parts[1].equals("live"))) {
                    id = parts[2];
                }
            }
        }
        return id != null && VIDEO_ID.matcher(id).matches() ? Optional.of(id) : Optional.empty();
    }

    private static String queryParam(String query, String name) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
