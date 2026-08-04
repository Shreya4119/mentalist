package com.hrragassistant.backend.ingestion;

import java.util.ArrayList;
import java.util.List;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.ai.transformer.splitter.TextSplitter;

/**
 * Spring AI's built-in TokenTextSplitter has no overlap support (see
 * spring-projects/spring-ai#2123), so this slides a fixed-size token window
 * across each document with a configurable overlap, using the same
 * tokenizer (cl100k_base via jtokkit) Spring AI's own splitter uses.
 */
public class OverlappingTokenTextSplitter extends TextSplitter {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    private final int chunkSizeTokens;
    private final int overlapTokens;

    public OverlappingTokenTextSplitter(int chunkSizeTokens, int overlapTokens) {
        if (overlapTokens >= chunkSizeTokens) {
            throw new IllegalArgumentException("overlapTokens must be smaller than chunkSizeTokens");
        }
        this.chunkSizeTokens = chunkSizeTokens;
        this.overlapTokens = overlapTokens;
    }

    @Override
    protected List<String> splitText(String text) {
        IntArrayList tokens = ENCODING.encode(text);
        int step = chunkSizeTokens - overlapTokens;

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < tokens.size(); start += step) {
            int end = Math.min(start + chunkSizeTokens, tokens.size());

            IntArrayList window = new IntArrayList(end - start);
            for (int i = start; i < end; i++) {
                window.add(tokens.get(i));
            }
            chunks.add(ENCODING.decode(window));

            if (end == tokens.size()) {
                break;
            }
        }
        return chunks;
    }
}
