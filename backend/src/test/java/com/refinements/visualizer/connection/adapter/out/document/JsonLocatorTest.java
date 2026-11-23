package com.refinements.visualizer.connection.adapter.out.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonLocatorTest {

    @Test
    void locatesMembersByTheirKeyAndArrayElementsByTheirStart() {
        String text =
                "{\n  \"formatVersion\": 2,\n  \"connections\": [\n    { \"name\": \"A\\\"b\", \"source\": \"X\" },\n"
                        + "    {\n      \"target\": \"Y\"\n    }\n  ]\n}\n";
        JsonLocator l = JsonLocator.of(text);
        assertThat(l.locate("/formatVersion")).isEqualTo(new JsonLocator.Position(2, 3));
        assertThat(l.locate("/connections/0")).isEqualTo(new JsonLocator.Position(4, 5));
        assertThat(l.locate("/connections/0/source").line()).isEqualTo(4);
        assertThat(l.locate("/connections/0/source").column()).isGreaterThan(20);
        assertThat(l.locate("/connections/1/target")).isEqualTo(new JsonLocator.Position(6, 7));
        assertThat(l.locate("/connections/1/missing"))
                .as("falls back to the nearest ancestor")
                .isEqualTo(new JsonLocator.Position(5, 5));
    }

    @Test
    void handlesCrlfAndInvalidText() {
        JsonLocator l = JsonLocator.of("{\r\n\"a\": [\r\n1,\r\n2]\r\n}");
        assertThat(l.locate("/a/1").line()).isEqualTo(4);
        JsonLocator broken = JsonLocator.of("{\n\"a\": 1,\n\"b\" 2}");
        assertThat(broken.locate("/a").line()).isEqualTo(2);
        assertThat(broken.locate("/zzz")).isEqualTo(new JsonLocator.Position(1, 1));
    }
}
