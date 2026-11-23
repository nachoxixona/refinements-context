package com.refinements.visualizer.card.adapter.out.parser;

import com.refinements.visualizer.card.application.port.out.SpecParserPort;
import java.net.URI;
import org.springframework.stereotype.Component;

/** {@link SpecParserPort} on swagger-parser: {@link SpecParser} then {@link FieldFlattener} (Principle I). */
@Component
class SwaggerSpecParser implements SpecParserPort {

    private final SpecParser parser;
    private final FieldFlattener flattener;

    SwaggerSpecParser(SpecParser parser, FieldFlattener flattener) {
        this.parser = parser;
        this.flattener = flattener;
    }

    @Override
    public ParsedSpecification parse(String content, URI base, String serviceKey) {
        ParsedSpec parsed = parser.parse(content, base);
        return new ParsedSpecification(
                parsed.title(), parsed.version(), parsed.format(), flattener.flatten(parsed.openApi(), serviceKey));
    }
}
