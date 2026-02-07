package org.flymars.devtools.midas.analysis;

import com.google.gson.Gson;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.*;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.config.PluginConfig;
import org.flymars.devtools.midas.data.CommitInfo;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for AI-powered commit analysis
 */
@Service(Service.Level.APP)
public final class AIAnalyzerService {
    private static final Logger LOG = Logger.getInstance(AIAnalyzerService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    public AIAnalyzerService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)  // Increased from 60 to 180 seconds (3 minutes)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Analyze commits and generate a weekly report
     */
    public CompletableFuture<AnalysisResult> analyzeCommits(List<CommitInfo> commits, ConfigManager config) {
        return analyzeCommits(commits, null, config);
    }

    /**
     * Analyze commits with daily notes and generate a weekly report
     */
    public CompletableFuture<AnalysisResult> analyzeCommits(List<CommitInfo> commits,
                                                             List<org.flymars.devtools.midas.data.DailyNote> notes,
                                                             ConfigManager config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = PromptTemplate.buildWeeklyReportPrompt(commits, notes, config);

                AIResponse response = callAI(prompt, config);

                // Parse the response
                return parseAnalysisResponse(response.content);
            } catch (Exception e) {
                LOG.error("Error analyzing commits", e);
                return AnalysisResult.error(e.getMessage());
            }
        });
    }

    /**
     * Call the AI API
     */
    private AIResponse callAI(String prompt, ConfigManager config) throws IOException {
        String requestBody = buildRequestBody(prompt, config);
        String url = buildApiUrl(config);

        // Get and log API key for debugging
        String apiKey = config.getApiKey();
        LOG.info("AI Provider: " + config.getAIProvider());
        LOG.info("API Endpoint: " + url);
        LOG.info("API Key length: " + (apiKey != null ? apiKey.length() : 0));
        LOG.info("API Key preview: " + (apiKey != null && apiKey.length() > 8 ?
                apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4) : "null or too short"));

        if (apiKey == null || apiKey.isEmpty()) {
            LOG.error("API Key is null or empty! Please check your AI configuration.");
            throw new IOException("API Key is not configured. Please go to Settings → Midas → AI Configuration and enter your API key.");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json");

        // Add Authorization header based on provider
        if (config.getAIProvider() == PluginConfig.AIProvider.ZHIPU) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            LOG.info("Using Zhipu AI API with Bearer token");
        } else {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            LOG.info("Using OpenAI-compatible API with Bearer token");
        }

        Request request = requestBuilder
                .post(RequestBody.create(requestBody, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                LOG.error("AI API request failed with status " + response.code() + ", body: " + errorBody);
                throw new IOException("Unexpected code " + response + ", body: " + errorBody);
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            LOG.info("AI API response received, length: " + responseBody.length() + " characters");
            return parseAIResponse(responseBody, config.getAIProvider());
        }
    }

    /**
     * Build API request body based on provider
     */
    private String buildRequestBody(String prompt, ConfigManager config) {
        switch (config.getAIProvider()) {
            case OPENAI:
            case ZHIPU:
                // Zhipu uses OpenAI-compatible format
                OpenAIRequest openAIReq = new OpenAIRequest();
                openAIReq.model = config.getModel();
                openAIReq.messages = List.of(
                        new OpenAIRequest.Message("system", "You are a helpful technical report assistant."),
                        new OpenAIRequest.Message("user", prompt)
                );
                openAIReq.temperature = 0.7;
                openAIReq.max_tokens = 4000;  // Increased for JSON responses
                return gson.toJson(openAIReq);

            case CLAUDE:
                ClaudeRequest claudeReq = new ClaudeRequest();
                claudeReq.model = config.getModel();
                claudeReq.messages = List.of(
                        new ClaudeRequest.Message("user", prompt)
                );
                claudeReq.max_tokens = 4000;  // Increased for JSON responses
                return gson.toJson(claudeReq);

            case CUSTOM:
                // Default to OpenAI format for custom providers
                return gson.toJson(new OpenAIRequest());

            default:
                return "{}";
        }
    }

    /**
     * Build full API URL
     */
    private String buildApiUrl(ConfigManager config) {
        String endpoint = config.getEndpoint();
        if (config.getAIProvider() == PluginConfig.AIProvider.OPENAI || config.getAIProvider() == PluginConfig.AIProvider.ZHIPU) {
            return endpoint + "/chat/completions";
        } else if (config.getAIProvider() == PluginConfig.AIProvider.CLAUDE) {
            return endpoint + "/v1/messages";
        }
        return endpoint;
    }

    /**
     * Parse AI response based on provider
     */
    private AIResponse parseAIResponse(String responseBody, PluginConfig.AIProvider provider) {
        try {
            switch (provider) {
                case OPENAI:
                case ZHIPU:
                    // Zhipu uses OpenAI-compatible response format
                    OpenAIResponse openAIResp = gson.fromJson(responseBody, OpenAIResponse.class);
                    if (openAIResp.choices != null && !openAIResp.choices.isEmpty()) {
                        return new AIResponse(openAIResp.choices.get(0).message.content);
                    }
                    // Check for error in response
                    if (openAIResp.error != null) {
                        return new AIResponse("Error: " + openAIResp.error.message);
                    }
                    return new AIResponse(responseBody);

                case CLAUDE:
                    ClaudeResponse claudeResp = gson.fromJson(responseBody, ClaudeResponse.class);
                    return new AIResponse(claudeResp.content.get(0).text);

                case CUSTOM:
                    // Try OpenAI format first
                    try {
                        OpenAIResponse fallback = gson.fromJson(responseBody, OpenAIResponse.class);
                        if (fallback.choices != null && !fallback.choices.isEmpty()) {
                            return new AIResponse(fallback.choices.get(0).message.content);
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to parse as OpenAI format");
                    }
                    return new AIResponse(responseBody);

                default:
                    return new AIResponse(responseBody);
            }
        } catch (Exception e) {
            LOG.error("Error parsing AI response", e);
            return new AIResponse("Error parsing response: " + e.getMessage());
        }
    }

    /**
     * Parse the analysis result from AI response (JSON format)
     */
    private AnalysisResult parseAnalysisResponse(String content) {
        AnalysisResult result = new AnalysisResult();
        result.rawContent = content;

        LOG.info("Parsing AI JSON response, length: " + content.length());

        try {
            // Try to parse as JSON
            // First, clean up the response - remove markdown code blocks if present
            String cleanedContent = content.trim();

            // Remove ```json and ``` markers if present
            if (cleanedContent.startsWith("```json")) {
                cleanedContent = cleanedContent.substring(7);
            } else if (cleanedContent.startsWith("```")) {
                cleanedContent = cleanedContent.substring(3);
            }

            if (cleanedContent.endsWith("```")) {
                cleanedContent = cleanedContent.substring(0, cleanedContent.length() - 3);
            }

            cleanedContent = cleanedContent.trim();

            // Check if JSON is complete (must end with })
            if (!cleanedContent.endsWith("}")) {
                LOG.error("JSON response is incomplete! Does not end with }");
                LOG.error("Last 100 characters: " +
                        (cleanedContent.length() > 100 ? cleanedContent.substring(cleanedContent.length() - 100) : cleanedContent));
                return AnalysisResult.error(
                        "AI response was truncated. The JSON response is incomplete.\n\n" +
                                "This usually means the response exceeded the token limit.\n" +
                                "Please try:\n" +
                                "1. Reduce the number of commits being analyzed\n" +
                                "2. Check the AI model's token limit in settings\n" +
                                "3. Use a model with higher token limits (e.g., gpt-4 instead of gpt-3.5)\n\n" +
                                "Raw response preview (last 200 chars):\n" +
                                (content.length() > 200 ? content.substring(content.length() - 200) : content)
                );
            }

            LOG.info("Cleaned content preview: " +
                    (cleanedContent.length() > 100 ? cleanedContent.substring(0, 100) + "..." : cleanedContent));

            // Parse JSON
            AIAnalysisJSON analysis = gson.fromJson(cleanedContent, AIAnalysisJSON.class);

            if (analysis != null) {
                result.summary = analysis.summary;
                result.technicalHighlights = analysis.technicalHighlights;
                result.problemsAndSolutions = analysis.problemsAndSolutions;
                result.nextWeekPlans = analysis.nextWeekPlans;

                LOG.info("JSON parsing successful!");
                LOG.info("Summary length: " + (result.summary != null ? result.summary.length() : 0));
                LOG.info("Technical highlights length: " + (result.technicalHighlights != null ? result.technicalHighlights.length() : 0));
                LOG.info("Problems and solutions length: " + (result.problemsAndSolutions != null ? result.problemsAndSolutions.length() : 0));
                LOG.info("Next week plans length: " + (result.nextWeekPlans != null ? result.nextWeekPlans.length() : 0));

                return result;
            } else {
                LOG.error("Parsed JSON object is null");
                return AnalysisResult.error("Failed to parse AI response: JSON object is null");
            }

        } catch (Exception e) {
            LOG.error("Failed to parse AI response as JSON", e);
            LOG.error("Response content: " + content);

            // Check if it's a JSON syntax error (unterminated object)
            if (e.getClass().getSimpleName().contains("JsonSyntaxException") ||
                    (e.getMessage() != null && e.getMessage().contains("Unterminated"))) {
                return AnalysisResult.error(
                        "AI response JSON is malformed (unterminated object).\n\n" +
                                "This usually means the response exceeded the token limit.\n" +
                                "Please try:\n" +
                                "1. Reducing the number of commits being analyzed\n" +
                                "2. Using a model with higher token limits\n\n" +
                                "Error: " + e.getMessage()
                );
            }

            // Fallback: try to extract from markdown format (backward compatibility)
            LOG.info("Attempting fallback to markdown parsing...");
            return parseAnalysisResponseMarkdown(content);
        }
    }

    /**
     * Fallback parser for markdown format (backward compatibility)
     */
    private AnalysisResult parseAnalysisResponseMarkdown(String content) {
        AnalysisResult result = new AnalysisResult();
        result.rawContent = content;

        LOG.info("Parsing AI response as markdown (fallback)");

        // Parse sections from the markdown response
        // Split by ## headers (level 2 headings)
        String[] sections = content.split("(?=##\\s)");

        LOG.info("Found " + sections.length + " sections after split");

        for (int i = 0; i < sections.length; i++) {
            String section = sections[i];
            String trimmed = section.trim();

            // Skip empty sections
            if (trimmed.isEmpty()) {
                continue;
            }

            // Get the first line to check what section it is
            String firstLine = trimmed.split("\n")[0].trim();

            LOG.info("Section " + i + " first line: " + firstLine);

            if (firstLine.startsWith("## 本周工作总结") || firstLine.startsWith("## Work Summary")) {
                result.summary = extractSectionContent(trimmed);
                LOG.info("Parsed summary section, length: " + (result.summary != null ? result.summary.length() : 0));
            } else if (firstLine.startsWith("## 技术要点") || firstLine.startsWith("## Technical Highlights")) {
                result.technicalHighlights = extractSectionContent(trimmed);
                LOG.info("Parsed technicalHighlights section, length: " + (result.technicalHighlights != null ? result.technicalHighlights.length() : 0));
            } else if (firstLine.startsWith("## 问题与解决方案") || firstLine.startsWith("## Problems")) {
                result.problemsAndSolutions = extractSectionContent(trimmed);
                LOG.info("Parsed problemsAndSolutions section, length: " + (result.problemsAndSolutions != null ? result.problemsAndSolutions.length() : 0));
            } else if (firstLine.startsWith("## 下周计划") || firstLine.startsWith("## Next Week")) {
                result.nextWeekPlans = extractSectionContent(trimmed);
                LOG.info("Parsed nextWeekPlans section, length: " + (result.nextWeekPlans != null ? result.nextWeekPlans.length() : 0));
            } else {
                LOG.info("Section " + i + " did not match any known pattern: " + firstLine);
            }
        }

        LOG.info("Markdown parsing complete. Summary: " + (result.summary != null) +
                ", Technical: " + (result.technicalHighlights != null) +
                ", Problems: " + (result.problemsAndSolutions != null) +
                ", Next Week: " + (result.nextWeekPlans != null));

        return result;
    }

    private String extractSectionContent(String section) {
        // Remove the header
        int firstNewline = section.indexOf("\n");
        if (firstNewline == -1) {
            return section;
        }
        return section.substring(firstNewline + 1).trim();
    }

    /**
     * Test the AI connection
     */
    public CompletableFuture<Boolean> testConnection(ConfigManager config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String testPrompt = "Test connection. Respond with 'OK' only.";
                AIResponse response = callAI(testPrompt, config);
                return response.content != null && !response.content.isEmpty();
            } catch (Exception e) {
                LOG.error("AI connection test failed", e);
                return false;
            }
        });
    }

    /**
     * Get instance of the service
     */
    public static AIAnalyzerService getInstance() {
        return new AIAnalyzerService();
    }

    // Inner classes for API requests/responses
    private static class OpenAIRequest {
        String model;
        List<Message> messages;
        double temperature;
        int max_tokens;

        static class Message {
            String role;
            String content;

            public Message(String role, String content) {
                this.role = role;
                this.content = content;
            }
        }
    }

    private static class OpenAIResponse {
        List<Choice> choices;
        ErrorInfo error;

        static class Choice {
            Message message;
        }

        static class Message {
            String content;
        }

        static class ErrorInfo {
            String message;
            String type;
            String code;
        }
    }

    private static class ClaudeRequest {
        String model;
        List<Message> messages;
        int max_tokens;

        static class Message {
            String role;
            String content;

            public Message(String role, String content) {
                this.role = role;
                this.content = content;
            }
        }
    }

    private static class ClaudeResponse {
        List<ContentBlock> content;
    }

    private static class ContentBlock {
        String type;
        String text;
    }

    /**
     * JSON response structure for AI analysis
     */
    private static class AIAnalysisJSON {
        String summary;
        String technicalHighlights;
        String problemsAndSolutions;
        String nextWeekPlans;
    }

    private static class AIResponse {
        final String content;

        AIResponse(String content) {
            this.content = content;
        }
    }

    public static class AnalysisResult {
        public String rawContent;
        public String summary;
        public String technicalHighlights;
        public String problemsAndSolutions;
        public String nextWeekPlans;
        public String error;

        public static AnalysisResult error(String errorMessage) {
            AnalysisResult result = new AnalysisResult();
            result.error = errorMessage;
            return result;
        }

        public boolean hasError() {
            return error != null;
        }
    }
}