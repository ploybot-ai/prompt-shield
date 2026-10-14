package com.ploybot.promptshield.injection;

/**
 * Where a piece of text comes from. The same sentence carries a different risk
 * depending on its origin: "call the delete_files tool" is a normal request from the
 * user but an attack when it appears inside a web page returned by a search tool.
 */
public enum ContentSource {

    /** Text typed by the end user (direct prompt injection). */
    USER,

    /** The application's own system prompt. Trusted; only scanned on request. */
    SYSTEM,

    /** Retrieved or uploaded content: web pages, e-mails, files, RAG chunks. */
    DOCUMENT,

    /** The result of a tool or MCP server call. */
    TOOL_RESULT,

    /** A tool or MCP server description or schema (tool poisoning). */
    TOOL_DEFINITION;

    /** Whether the text is third-party data the model should never take orders from. */
    public boolean isIndirect() {
        return this == DOCUMENT || this == TOOL_RESULT || this == TOOL_DEFINITION;
    }
}
