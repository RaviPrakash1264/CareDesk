package com.project.caredesk.controller;


import com.project.caredesk.tools.HelpDeskTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/api/tools")
public class HelpDeskController {

    private final ChatClient chatClient;
    private final ChatClient webSearchChatClient;
    private final HelpDeskTools helpDeskTools;

    public HelpDeskController(@Qualifier("helpDeskChatClient") ChatClient chatClient,
                              @Qualifier("webSearchRAGChatClient") ChatClient webSearchChatClient,
                              HelpDeskTools helpDeskTools) {
        this.chatClient = chatClient;
        this.webSearchChatClient = webSearchChatClient;
        this.helpDeskTools = helpDeskTools;
    }

    @GetMapping("/help-desk")
    public ResponseEntity<String> helpDesk(@RequestHeader("username") String username,
            @RequestParam("message") String message) {
        String answer = chatClient.prompt()
                .user(message)
                .tools(helpDeskTools)
                .tools(toolSpec -> toolSpec.context(Map.of("username", username)))
                .call().content();
        return ResponseEntity.ok(answer);
    }
}
