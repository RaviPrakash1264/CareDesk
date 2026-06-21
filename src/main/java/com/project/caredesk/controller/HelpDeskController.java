package com.project.caredesk.controller;


import com.project.caredesk.exception.InvalidAnswerException;
import com.project.caredesk.tools.HelpDeskTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/tools")
public class HelpDeskController {

    private final ChatClient chatClient;
    private final ChatClient webSearchChatClient;
    private final HelpDeskTools helpDeskTools;
    private final FactCheckingEvaluator factCheckingEvaluator;

    public HelpDeskController(@Qualifier("helpDeskChatClient") ChatClient chatClient,
                              @Qualifier("webSearchRAGChatClient") ChatClient webSearchChatClient,
                              HelpDeskTools helpDeskTools,
                              ChatClient.Builder chatClientBuilder,
                              @Value("classpath:/promptTemplates/factcheck.st") Resource factCheckTemplate)
            throws IOException {
        this.chatClient = chatClient;
        this.webSearchChatClient = webSearchChatClient;
        this.helpDeskTools = helpDeskTools;
        this.factCheckingEvaluator = FactCheckingEvaluator.builder(chatClientBuilder)
                .evaluationPrompt(factCheckTemplate.getContentAsString(Charset.defaultCharset()))
                .build();
    }

    @Retryable(retryFor = InvalidAnswerException.class, maxAttempts = 3)
    @GetMapping("/help-desk")
    public ResponseEntity<String> helpDesk(@RequestHeader("username") String username,
            @RequestParam("message") String message) {
        // Tools register their name here when they run, so we can tell whether the
        // response was tool-driven.
        Set<String> invokedTools = ConcurrentHashMap.newKeySet();
        String answer = chatClient.prompt()
                .user(message)
                .tools(helpDeskTools)
                .tools(toolSpec -> toolSpec.context(Map.of(
                        "username", username,
                        "invokedTools", invokedTools)))
                .call().content();
        // Skip fact-checking for ticket create/status responses: they aren't factual
        // claims, and retrying would re-execute side-effecting tools (e.g. createTicket).
        if (invokedTools.isEmpty()) {
            validateAnswer(message, answer);
        }
        return ResponseEntity.ok(answer);
    }

    private void validateAnswer(String message, String answer) {
        EvaluationRequest evaluationRequest =
                new EvaluationRequest(message, List.of(), answer);
        EvaluationResponse evaluationResponse = factCheckingEvaluator.evaluate(evaluationRequest);
        if (!evaluationResponse.isPass()) {
            throw new InvalidAnswerException(message, answer);
        }
    }

    @Recover
    public ResponseEntity<String> recover(InvalidAnswerException exception, String username, String message) {
        return ResponseEntity.ok("I'm sorry, I couldn't answer your question. Please try rephrasing it.");
    }
}
