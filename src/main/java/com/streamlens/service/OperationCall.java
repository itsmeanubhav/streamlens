package com.streamlens.service;

/** One method call in a stream chain, as extracted from the user's source. */
public record OperationCall(String name, String argsText, int line) {
    public String toSourceFragment() {
        return "." + name + "(" + argsText + ")";
    }
}
