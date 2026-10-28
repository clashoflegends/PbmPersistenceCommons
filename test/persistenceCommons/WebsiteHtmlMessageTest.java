package persistenceCommons;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebsiteHtmlMessageTest {
    @Test
    void plainProtocolResponsesStayPlainAndHtmlFragmentsAreRecognized() {
        for (String plain : new String[]{null, "", "NONE\nsample.player", "a".repeat(40), "7|" + "b".repeat(64),
                "Invalid player token", "3 < 5 and 7 > 2"}) {
            assertNull(WebsiteHtmlMessage.extract(plain));
        }
        assertEquals("File orders.rc.egf was successfully uploaded.",
                WebsiteHtmlMessage.extract("<pre>File orders.rc.egf was successfully uploaded.</pre>"));
        assertEquals("Access denied", WebsiteHtmlMessage.extract("<p>A<b>ccess</b> denied</p>"));
    }

    @Test
    void extractsVisibleTextWithoutScriptsStylesCommentsOrHiddenFormData() {
        String html = "<!doctype html><html><head><title>Fallback title</title>"
                + "<style>.secret {display:none} body {color:red}</style></head><body>"
                + "<h1>Verifica&#231;&#227;o &amp; acesso</h1><p>Tente&nbsp;mais tarde.</p>"
                + "<!-- comment-secret --><script>var secret='script-secret';</script>"
                + "<input type='hidden' value='input-secret'>"
                + "<div hidden><p>hidden-secret</p></div><p style='display: none !important'>style-secret</p>"
                + "<span aria-hidden='true'>aria-secret</span>"
                + "<a href='http://example.invalid/?secret=url-secret'>Help</a>"
                + "<img src='http://example.invalid/image-secret'></body></html>";
        assertEquals("Verifica\u00e7\u00e3o & acesso Tente mais tarde. Help", WebsiteHtmlMessage.extract(html));
    }

    @Test
    void html5AndMalformedHiddenContainersDoNotLeakTheirContents() {
        assertEquals("Visible Done", WebsiteHtmlMessage.extract("<html><body><p>Visible</p>"
                + "<template>secret<template>nested secret</template>more secret</template>"
                + "<svg><text>svg secret</text></svg><p>Done</p></body></html>"));
        assertEquals("Visible", WebsiteHtmlMessage.extract("<p>Visible</p><div hidden><span>secret"));
        assertEquals("Title only", WebsiteHtmlMessage.extract("<html><head><title>Title only</title></head>"
                + "<body><script>unclosed script secret"));
        assertEquals("", WebsiteHtmlMessage.extract("<html><body><script>secret</script></body></html>"));
    }

    @Test
    void decodesAndRedactsBeforeBoundingAndRemovesLogControlCharacters() {
        String token = "b".repeat(40);
        String message = WebsiteHtmlMessage.extract("<p>Password: a&amp;b Token:" + token
                + " EGF:987654 Log:\r\nFORGED\u001b\u202e event</p>", "a&b", token, "987654");
        assertFalse(message.contains("a&b"));
        assertFalse(message.contains(token));
        assertFalse(message.contains("987654"));
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\r"));
        assertFalse(message.contains("\u001b"));
        assertFalse(message.contains("\u202e"));
        assertTrue(message.contains("[redacted]"));
        String boundary = WebsiteHtmlMessage.extract("<p>" + "Z".repeat(580) + "very-secret-password" + "Z".repeat(100) + "</p>",
                "very-secret-password");
        assertTrue(boundary.length() <= WebsiteHtmlMessage.MAX_MESSAGE);
        assertFalse(boundary.contains("very-secret"));
        assertTrue(boundary.endsWith("..."));
        String huge = "<html><title>Service unavailable</title><body><p>" + "Z".repeat(65500) + "very-secret-password</p></body></html>";
        assertFalse(WebsiteHtmlMessage.extract(huge, "very-secret-password").contains("very-secret"));
    }

    @Test
    void entityEscapedMarkupRemainsPlainTextForTheUi() {
        assertEquals("<html><img src=x> not executable", WebsiteHtmlMessage.extract(
                "<p>&lt;html&gt;&lt;img src=x&gt; not executable</p>"));
    }

    @Test
    void longHtmlStaysHtmlEvenWhenTheReadablePrefixMustBeDiscarded() {
        assertEquals("", WebsiteHtmlMessage.extract("<p>" + "Z".repeat(70000) + "player token secret</p>"));
        assertNotNull(WebsiteHtmlMessage.extract("Z".repeat(70000) + "<h1>Late HTML</h1>"));
        assertEquals("", WebsiteHtmlMessage.extract("<!-- hidden comment -->"));
        assertEquals("Temporarily unavailable", WebsiteHtmlMessage.extract("<blockquote>Temporarily unavailable</blockquote>"));
    }

    @Test
    void redactsCredentialsSplitAcrossVisibleBlocksOrLineBreaks() {
        String token = "ab".repeat(20);
        assertEquals("Password: [redacted] Token: [redacted]", WebsiteHtmlMessage.extract(
                "<p>Password: abc</p><div>def</div><p>Token: " + token.substring(0, 20)
                + "<br>" + token.substring(20) + "</p>", "abcdef", token));
        assertEquals("[redacted]", WebsiteHtmlMessage.extract("<p>A&amp;<br>B</p>", "A&B"));
        assertEquals("\u0130 [redacted]", WebsiteHtmlMessage.extract("<p>\u0130 " + token.toUpperCase(java.util.Locale.ROOT)
                + "</p>", token));
    }
}
