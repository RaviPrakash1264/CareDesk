package com.project.caredesk.tools;


import com.project.caredesk.entity.HelpDeskTicket;
import com.project.caredesk.model.TicketRequest;
import com.project.caredesk.service.HelpDeskTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
public class HelpDeskTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(HelpDeskTools.class);

    private final HelpDeskTicketService service;
    private final ChatClient webSearchChatClient;

    public HelpDeskTools(HelpDeskTicketService service,
                         @Qualifier("webSearchRAGChatClient") ChatClient webSearchChatClient) {
        this.service = service;
        this.webSearchChatClient = webSearchChatClient;
    }


    @Tool(name = "createTicket", description = "Create the Support Ticket", returnDirect = true)
    String createTicket(@ToolParam(description = "Details to create a Support ticket")
                        TicketRequest ticketRequest, ToolContext toolContext) {
        markToolInvoked(toolContext, "createTicket");
        String username = (String) toolContext.getContext().get("username");
        LOGGER.info("Creating support ticket for user: {} with details: {}", username, ticketRequest);
        HelpDeskTicket savedTicket = service.createTicket(ticketRequest,username);
        LOGGER.info("Ticket created successfully. Ticket ID: {}, Username: {}", savedTicket.getId(), savedTicket.getUsername());
        return "Ticket #" + savedTicket.getId() + " created successfully for user " + savedTicket.getUsername();
    }

    @Tool(description = "Fetch the status of the tickets based on a given username")
    List<HelpDeskTicket> getTicketStatus(ToolContext toolContext) {
        markToolInvoked(toolContext, "getTicketStatus");
        String username = (String) toolContext.getContext().get("username");
        LOGGER.info("Fetching tickets for user: {}", username);
        List<HelpDeskTicket> tickets =  service.getTicketsByUsername(username);
        LOGGER.info("Found {} tickets for user: {}", tickets.size(), username);
        // throw new RuntimeException("Unable to fetch ticket status");
        return tickets;
    }

    @Tool(name = "searchWebForITIssue",
            description = "Search the public web for IT or software troubleshooting answers. " +
                    "Use ONLY for IT/software questions, only after the company handbook has no " +
                    "answer or the user is unsatisfied, and only after the user has agreed to a web search.")
    String searchWebForITIssue(@ToolParam(description = "The IT/software question to search the web for")
                               String query, ToolContext toolContext) {
        String username = (String) toolContext.getContext().get("username");
        LOGGER.info("Web RAG search for query: {} (user: {})", query, username);
        return webSearchChatClient.prompt()
                .advisors(a -> a.param(CONVERSATION_ID, username))
                .user(query)
                .call().content();
    }

    /**
     * Records that a tool ran, so the controller can skip fact-checking for tool-driven
     * responses (e.g. ticket create/status), which aren't factual claims and must not be
     * re-executed by a retry.
     */
    @SuppressWarnings("unchecked")
    private void markToolInvoked(ToolContext toolContext, String toolName) {
        Object holder = toolContext.getContext().get("invokedTools");
        if (holder instanceof Set<?>) {
            ((Set<String>) holder).add(toolName);
        }
    }
}
