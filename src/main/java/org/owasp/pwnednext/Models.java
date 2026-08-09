package org.owasp.pwnednext;

import java.util.List;

// These compact records describe messages, model-issued tool calls, and SQL arguments without forcing everyone into a ceremonial Java class robe.
record Message(String role, String content) {}
record ToolCall(String tool, ToolArguments args) {}
record ToolArguments(String query) {}
// Lets the controller use a real HTTP model client or a test double, because interfaces are cheaper than waiting for a moody model.
@FunctionalInterface interface Generator { String generate(List<Message> messages) throws Exception; }