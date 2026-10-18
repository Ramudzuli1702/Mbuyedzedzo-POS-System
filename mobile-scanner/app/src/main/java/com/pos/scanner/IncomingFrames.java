package com.pos.scanner;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/** Handles old multiline receipts alongside new single-line JSON envelopes. */
public final class IncomingFrames {
    private final StringBuilder legacy=new StringBuilder();
    public static final class Frame {
        public final String json,receipt;
        Frame(String json,String receipt){this.json=json;this.receipt=receipt;}
    }
    public Frame accept(String line) {
        String trimmed=line.trim();
        if(trimmed.equals("===END_RECEIPT===")) {
            String text=legacy.toString();legacy.setLength(0);return new Frame(null,text.trim().isEmpty()?null:text);
        }
        if(trimmed.startsWith("{")||trimmed.startsWith("[")) {
            try {
                JsonElement value=JsonParser.parseString(trimmed);
                JsonElement envelope=value.isJsonArray()&&value.getAsJsonArray().size()>0?value.getAsJsonArray().get(0):value;
                if(envelope.isJsonObject()&&envelope.getAsJsonObject().has("type"))return new Frame(trimmed,null);
            } catch(RuntimeException ignored) { /* A legacy receipt line may start with punctuation. */ }
        }
        if(legacy.length()+line.length()>1024*1024) { legacy.setLength(0);throw new IllegalArgumentException("Receipt is too large"); }
        legacy.append(line).append('\n');return new Frame(null,null);
    }
}
