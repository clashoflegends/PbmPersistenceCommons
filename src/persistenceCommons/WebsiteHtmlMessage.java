package persistenceCommons;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Best-effort readable HTML text. Never renders HTML or fetches scripts/styles/resources. */
final class WebsiteHtmlMessage {
    static final int MAX_MESSAGE = 600;
    private static final int MAX_INPUT = 65536;
    private static final Pattern HTML_MARKUP = Pattern.compile(
            "(?is)<(?:!doctype\\s+html\\b|!--|/?(?:"
            + Arrays.stream(HTML.getAllTags()).map(tag -> Pattern.quote(tag.toString())).collect(Collectors.joining("|"))
            + "|section|main|article|header|footer|nav|aside|figure|figcaption|details|summary|template|svg|"
            + "video|audio|source|track|canvas|[a-z][a-z0-9]*-[a-z0-9-]+)(?=[\\s/>]))");
    private static final Set<String> OMIT = Set.of("script", "style", "template", "svg", "iframe", "object", "noscript");
    private static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr");
    private static final Pattern HIDDEN_STYLE = Pattern.compile("(?i)(?:^|;)\\s*(?:display\\s*:\\s*none|visibility\\s*:\\s*hidden)\\b");

    private WebsiteHtmlMessage() { }

    /** Null means plain text; an empty result means HTML with no readable text. */
    static String extract(String body, String... secrets) {
        if (body == null) { return null; }
        // Classification must survive a parse-size limit, including markup beyond that limit.
        // Otherwise a long HTML401 could fall through to the plain bad-token/reset path.
        if (!HTML_MARKUP.matcher(body).find()) { return null; }
        String input = body;
        if (input.length() > MAX_INPUT) {
            // Drop the unfinished final text/tag rather than expose a credential prefix cut in half.
            input = input.substring(0, MAX_INPUT);
            int lastTag = input.lastIndexOf('<');
            input = lastTag < 0 ? "" : input.substring(0, lastTag);
        }
        TextCollector collector = new TextCollector();
        try {
            new ParserDelegator().parse(new StringReader(input), collector, true);
        } catch (IOException | RuntimeException ex) {
            // Parser failure is not a reason to expose raw HTML or treat it as a successful string.
            return "";
        }
        String text = normalize(collector.visible.toString());
        if (text.isEmpty()) { text = normalize(collector.title.toString()); }
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) {
                text = redact(text, normalize(secret));
            }
        }
        // Also suppress long hexadecimal credentials echoed by a proxy or diagnostic page.
        text = text.replaceAll("(?i)[a-f0-9]{32,}", "[redacted]");
        if (text.length() > MAX_MESSAGE) {
            int end = MAX_MESSAGE - 3;
            if (Character.isHighSurrogate(text.charAt(end - 1))) { end--; }
            text = text.substring(0, end).stripTrailing() + "...";
        }
        return text;
    }

    private static String redact(String text, String secret) {
        // Match literal credentials even when a page splits them with <br> or block elements.
        // Index mapping avoids regex injection/backtracking for arbitrary passwords.
        String compactSecret = secret.replace(" ", "");
        if (compactSecret.isEmpty()) { return text; }
        StringBuilder compact = new StringBuilder();
        int[] originalIndex = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') {
                originalIndex[compact.length()] = i;
                compact.append(text.charAt(i));
            }
        }
        String search = compact.toString();
        if (compactSecret.matches("(?i)[a-f0-9]{32,}")) {
            compactSecret = compactSecret.toLowerCase(Locale.ROOT);
            // Fold ASCII hex only: Unicode lowercasing can change length and invalidate the map.
            StringBuilder folded = new StringBuilder(search.length());
            for (int i = 0; i < search.length(); i++) {
                char c = search.charAt(i);
                folded.append(c >= 'A' && c <= 'F' ? (char) (c + ('a' - 'A')) : c);
            }
            search = folded.toString();
        }
        StringBuilder redacted = new StringBuilder();
        int consumed = 0;
        int found = search.indexOf(compactSecret);
        while (found >= 0) {
            int end = found + compactSecret.length();
            redacted.append(text, consumed, originalIndex[found]).append("[redacted]");
            consumed = originalIndex[end - 1] + 1;
            found = search.indexOf(compactSecret, end);
        }
        return redacted.append(text, consumed, text.length()).toString();
    }

    private static String normalize(String input) {
        StringBuilder result = new StringBuilder();
        boolean space = false;
        for (int cp : input.codePoints().toArray()) {
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp)) {
                space = result.length() > 0;
            } else if (Character.getType(cp) != Character.FORMAT) {
                if (space) { result.append(' '); space = false; }
                result.appendCodePoint(cp);
            }
        }
        return result.toString();
    }

    private record Element(String name, boolean hidden) { }

    private static final class TextCollector extends HTMLEditorKit.ParserCallback {
        private final Deque<Element> stack = new ArrayDeque<>();
        private final StringBuilder visible = new StringBuilder();
        private final StringBuilder title = new StringBuilder();

        @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) {
            String name = tag.toString().toLowerCase(Locale.ROOT);
            if (tag.isBlock()) { visible.append(' '); }
            boolean hidden = (!stack.isEmpty() && stack.peek().hidden()) || OMIT.contains(name)
                    || hasAttribute(attrs, "hidden") || "true".equalsIgnoreCase(attribute(attrs, "aria-hidden"))
                    || HIDDEN_STYLE.matcher(attribute(attrs, "style")).find();
            stack.push(new Element(name, hidden));
        }

        @Override public void handleEndTag(HTML.Tag tag, int pos) {
            String name = tag.toString().toLowerCase(Locale.ROOT);
            if (stack.stream().anyMatch(element -> element.name().equals(name))) {
                while (!stack.pop().name().equals(name)) { /* close malformed descendants too */ }
            }
            if (tag.isBlock()) { visible.append(' '); }
        }

        @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) {
            String name = tag.toString().toLowerCase(Locale.ROOT);
            if ("br".equals(name) || "hr".equals(name)) { visible.append(' '); }
            // HTML5 containers arrive as unknown/simple tags in the JDK's HTML3.2 parser.
            if (tag instanceof HTML.UnknownTag && !VOID.contains(name)) {
                if (hasAttribute(attrs, "endtag")) { handleEndTag(tag, pos); }
                else { handleStartTag(tag, attrs, pos); }
            }
        }

        @Override public void handleText(char[] data, int pos) {
            if (!stack.isEmpty() && stack.peek().hidden()) { return; }
            if (stack.stream().anyMatch(element -> element.name().equals("title"))) {
                title.append(data);
            } else if (stack.stream().noneMatch(element -> element.name().equals("head"))) {
                visible.append(data);
            }
        }

        private static boolean hasAttribute(MutableAttributeSet attrs, String name) {
            Enumeration<?> names = attrs.getAttributeNames();
            while (names.hasMoreElements()) {
                if (names.nextElement().toString().equalsIgnoreCase(name)) { return true; }
            }
            return false;
        }

        private static String attribute(MutableAttributeSet attrs, String name) {
            Enumeration<?> names = attrs.getAttributeNames();
            while (names.hasMoreElements()) {
                Object key = names.nextElement();
                if (key.toString().equalsIgnoreCase(name)) { return String.valueOf(attrs.getAttribute(key)); }
            }
            return "";
        }
    }
}
