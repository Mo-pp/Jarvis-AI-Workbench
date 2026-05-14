package com.msz.resume.ai.integrations.openviking.tooling;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSearchRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSearchResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.tool.ToolRuntimeContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenVikingSearchTool 单元测试。
 */
class OpenVikingSearchToolTest {

    @Test
    @DisplayName("空 query 应返回明确错误")
    void shouldRejectBlankQuery() {
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                new StubOpenVikingClient(null),
                createProperties(5, 8, 8000)
        );

        String result = tool.openviking_find("   ", null, null);

        assertEquals("OpenViking find failed: query is empty.", result);
    }

    @Test
    @DisplayName("find 空结果应返回成功空结果")
    void shouldReturnSuccessfulFindEmptyResult() {
        OpenVikingFindResponse response = new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(), List.of(), 0),
                null,
                0.1
        );
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                new StubOpenVikingClient(response),
                createProperties(5, 8, 8000)
        );

        String result = tool.openviking_find("test", null, null);

        assertTrue(result.contains("OpenViking find result"));
        assertTrue(result.contains("status=ok"));
        assertTrue(result.contains("result=empty"));
        assertTrue(!result.contains("failed"));
    }

    @Test
    @DisplayName("有结果时应格式化关键字段并限制 limit")
    void shouldFormatResultsAndClampLimit() {
        OpenVikingFindResponse.MatchedContext context = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/docs/auth",
                "resource",
                false,
                "Authentication guide covering OAuth 2.0 and login flow.",
                "docs",
                0.9234,
                "Semantic match on authentication"
        );
        OpenVikingFindResponse response = new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(context), List.of(), 1),
                null,
                0.1
        );
        StubOpenVikingClient client = new StubOpenVikingClient(response);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                client,
                createProperties(5, 3, 8000)
        );

        String result = tool.openviking_find("auth", "viking://resources/", 99);

        assertEquals(3, client.lastLimit);
        assertEquals(3, client.lastNodeLimit);
        assertTrue(result.contains("OpenViking find results for: auth"));
        assertTrue(result.contains("Score: 0.923"));
        assertTrue(result.contains("URI: viking://resources/docs/auth"));
        assertTrue(result.contains("Reason: Semantic match on authentication"));
        assertTrue(result.contains("Total: 1"));
    }

    @Test
    @DisplayName("客户端异常时应返回友好错误")
    void shouldReturnFriendlyMessageWhenClientThrows() {
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                new ErrorStubOpenVikingClient("OpenViking find failed: current key requires tenant headers."),
                createProperties(5, 8, 8000)
        );

        String result = tool.openviking_find("test", null, null);

        assertEquals("OpenViking find failed: current key requires tenant headers.", result);
    }

    @Test
    @DisplayName("read 应按 level 路由到 abstract/overview/read 接口")
    void shouldRouteReadByDisclosureLevel() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        client.readResponse = new OpenVikingReadResponse("ok", "exact content", null, 0.1);
        client.abstractResponse = new OpenVikingReadResponse("ok", "short abstract", null, 0.1);
        client.overviewResponse = new OpenVikingReadResponse("ok", "navigation overview", null, 0.1);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 8, 8000));

        String abstractResult = tool.openviking_read("viking://resources/a.md", "abstract");
        String overviewResult = tool.openviking_read("viking://resources/a.md", "overview");
        String readResult = tool.openviking_read("viking://resources/a.md", "read");

        assertTrue(abstractResult.contains("level=abstract"));
        assertTrue(abstractResult.contains("short abstract"));
        assertTrue(overviewResult.contains("level=overview"));
        assertTrue(overviewResult.contains("navigation overview"));
        assertTrue(readResult.contains("level=read"));
        assertTrue(readResult.contains("exact content"));
        assertEquals("viking://resources/a.md", client.lastReadAbstractUri);
        assertEquals("viking://resources/a.md", client.lastReadOverviewUri);
        assertEquals("viking://resources/a.md", client.lastReadUri);
    }

    @Test
    @DisplayName("read 应校验空 uri 和非法 level")
    void shouldValidateReadArguments() {
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                new StubOpenVikingClient(null),
                createProperties(5, 8, 8000)
        );

        assertEquals("OpenViking read failed: uri is empty.", tool.openviking_read(" ", "read"));
        assertEquals("OpenViking read failed: level is empty.", tool.openviking_read("viking://resources/a.md", " "));
        assertEquals("OpenViking read failed: level must be abstract, overview, or read.", tool.openviking_read("viking://resources/a.md", "detail"));
    }

    @Test
    @DisplayName("list 应调用浏览接口并限制 nodeLimit")
    void shouldCallListAndClampNodeLimit() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        client.listResponse = new OpenVikingReadResponse("ok", List.of("a.md", "b.md"), null, 0.1);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 8, 8000));

        String result = tool.openviking_list("viking://resources/", 9999);

        assertEquals("viking://resources/", client.lastListUri);
        assertEquals(1000, client.lastListNodeLimit);
        assertTrue(result.contains("OpenViking list result"));
        assertTrue(result.contains("status=ok"));
        assertTrue(result.contains("node_limit=1000"));
        assertTrue(result.contains("a.md"));
    }

    @Test
    @DisplayName("tree 应调用层级浏览接口并限制 nodeLimit")
    void shouldCallTreeAndClampNodeLimit() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        client.treeResponse = new OpenVikingReadResponse("ok", List.of("root", "child"), null, 0.1);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 8, 8000));

        String result = tool.openviking_tree("viking://resources/", 0);

        assertEquals("viking://resources/", client.lastTreeUri);
        assertEquals(1, client.lastTreeNodeLimit);
        assertTrue(result.contains("OpenViking tree result"));
        assertTrue(result.contains("status=ok"));
        assertTrue(result.contains("node_limit=1"));
        assertTrue(result.contains("root"));
    }

    @Test
    @DisplayName("read/list/tree 空结果应返回成功空结果")
    void shouldReturnSuccessfulEmptyReadAndBrowseResults() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        client.readResponse = new OpenVikingReadResponse("ok", null, null, 0.1);
        client.listResponse = new OpenVikingReadResponse("ok", List.of(), null, 0.1);
        client.treeResponse = new OpenVikingReadResponse("ok", null, null, 0.1);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 8, 8000));

        String readResult = tool.openviking_read("viking://resources/a.md", "read");
        String listResult = tool.openviking_list("viking://resources/", null);
        String treeResult = tool.openviking_tree("viking://resources/", null);

        assertTrue(readResult.contains("OpenViking read result"));
        assertTrue(readResult.contains("result=empty"));
        assertTrue(!readResult.contains("failed"));
        assertTrue(listResult.contains("OpenViking list result"));
        assertTrue(listResult.contains("result=empty"));
        assertTrue(!listResult.contains("failed"));
        assertTrue(treeResult.contains("OpenViking tree result"));
        assertTrue(treeResult.contains("result=empty"));
        assertTrue(!treeResult.contains("failed"));
    }

    @Test
    @DisplayName("glob 应调用路径匹配接口并限制 nodeLimit")
    void shouldCallGlobAndClampNodeLimit() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        client.globResponse = new OpenVikingReadResponse("ok", List.of("viking://resources/docs/api.md"), null, 0.1);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 3, 8000));

        String result = tool.openviking_glob("**/*.md", " viking://resources/ ", 99);

        assertEquals("**/*.md", client.lastGlobPattern);
        assertEquals("viking://resources/", client.lastGlobUri);
        assertEquals(3, client.lastGlobNodeLimit);
        assertTrue(result.contains("OpenViking glob result"));
        assertTrue(result.contains("status=ok"));
        assertTrue(result.contains("pattern=**/*.md"));
        assertTrue(result.contains("node_limit=3"));
        assertTrue(result.contains("viking://resources/docs/api.md"));
    }

    @Test
    @DisplayName("grep 应调用内容匹配接口并限制 nodeLimit")
    void shouldCallGrepAndClampNodeLimit() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        client.grepResponse = new OpenVikingReadResponse("ok", List.of("viking://resources/docs/auth.md:15 authentication"), null, 0.1);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 3, 8000));

        String result = tool.openviking_grep(" viking://resources/ ", " authentication ", true, 99);

        assertEquals("viking://resources/", client.lastGrepUri);
        assertEquals("authentication", client.lastGrepPattern);
        assertEquals(true, client.lastGrepCaseInsensitive);
        assertEquals(3, client.lastGrepNodeLimit);
        assertTrue(result.contains("OpenViking grep result"));
        assertTrue(result.contains("status=ok"));
        assertTrue(result.contains("pattern=authentication"));
        assertTrue(result.contains("node_limit=3"));
        assertTrue(result.contains("authentication"));
    }

    @Test
    @DisplayName("glob 和 grep 空匹配应返回成功空结果")
    void shouldReturnSuccessfulEmptyNarrowResults() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        client.globResponse = new OpenVikingReadResponse("ok", null, null, 0.1);
        client.grepResponse = new OpenVikingReadResponse("ok", List.of(), null, 0.1);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 8, 8000));

        String globResult = tool.openviking_glob("**/*.missing", "viking://resources/", null);
        String grepResult = tool.openviking_grep("viking://resources/", "missing", false, null);

        assertTrue(globResult.contains("OpenViking glob result"));
        assertTrue(globResult.contains("result=empty"));
        assertTrue(!globResult.contains("failed"));
        assertTrue(grepResult.contains("OpenViking grep result"));
        assertTrue(grepResult.contains("result=empty"));
        assertTrue(!grepResult.contains("failed"));
    }

    @Test
    @DisplayName("glob/grep 上游结构化空匹配应归一为成功空结果")
    void shouldNormalizeStructuredEmptyNarrowResults() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        client.globResponse = new OpenVikingReadResponse("ok", Map.of("matches", List.of(), "count", 0), null, 0.1);
        client.grepResponse = new OpenVikingReadResponse("ok", Map.of("matches", List.of(), "count", 0), null, 0.1);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 8, 8000));

        String globResult = tool.openviking_glob("**/*.missing", "viking://resources/", null);
        String grepResult = tool.openviking_grep("viking://resources/", "missing", false, null);

        assertTrue(globResult.contains("OpenViking glob result"));
        assertTrue(globResult.contains("result=empty"));
        assertTrue(!globResult.contains("\"matches\":[]"));
        assertTrue(grepResult.contains("OpenViking grep result"));
        assertTrue(grepResult.contains("result=empty"));
        assertTrue(!grepResult.contains("\"matches\":[]"));
    }
    @Test
    @DisplayName("glob 应校验 pattern、uri 和 nodeLimit")
    void shouldValidateGlobArguments() {
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                new StubOpenVikingClient(null),
                createProperties(5, 8, 8000)
        );

        assertEquals("OpenViking glob failed: pattern is empty. Provide a glob pattern such as **/*.md.", tool.openviking_glob(" ", "viking://resources/", null));
        assertEquals("OpenViking glob failed: pattern is too long. Keep it under 256 characters.", tool.openviking_glob("x".repeat(257), "viking://resources/", null));
        assertEquals("OpenViking glob failed: uri must start with viking://.", tool.openviking_glob("**/*.md", "file://resources/", null));
        assertEquals("OpenViking glob failed: nodeLimit must be a positive integer.", tool.openviking_glob("**/*.md", "viking://resources/", 0));
    }

    @Test
    @DisplayName("grep 应校验 uri、pattern 和 nodeLimit")
    void shouldValidateGrepArguments() {
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                new StubOpenVikingClient(null),
                createProperties(5, 8, 8000)
        );

        assertEquals("OpenViking grep failed: uri is empty. Provide an authorized scope such as viking://resources/.", tool.openviking_grep(" ", "auth", false, null));
        assertEquals("OpenViking grep failed: uri must start with viking://.", tool.openviking_grep("file://resources/", "auth", false, null));
        assertEquals("OpenViking grep failed: pattern is empty. Provide text or a regular expression to match.", tool.openviking_grep("viking://resources/", " ", false, null));
        assertEquals("OpenViking grep failed: pattern is too long. Keep it under 256 characters.", tool.openviking_grep("viking://resources/", "x".repeat(257), false, null));
        assertEquals("OpenViking grep failed: nodeLimit must be a positive integer.", tool.openviking_grep("viking://resources/", "auth", false, 0));
    }

    @Test
    @DisplayName("find 和 search 应校验 limit")
    void shouldValidateFindAndSearchLimit() {
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                new StubOpenVikingClient(null),
                createProperties(5, 8, 8000)
        );

        assertEquals("OpenViking find failed: limit must be a positive integer.", tool.openviking_find("auth", null, 0));
        assertEquals("OpenViking search failed: limit must be a positive integer.", tool.openviking_search("auth", null, 0));
    }

    @Test
    @DisplayName("find 和 search 空结果应返回成功空结果")
    void shouldReturnSuccessfulEmptyRetrieveResults() {
        OpenVikingFindResponse findResponse = new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(), List.of(), 0),
                null,
                0.1
        );
        OpenVikingSearchResponse searchResponse = new OpenVikingSearchResponse(
                "ok",
                new OpenVikingSearchResponse.Result(List.of(), List.of(), List.of(), 0),
                null,
                0.1
        );
        StubOpenVikingClient client = new StubOpenVikingClient(findResponse);
        client.searchResponse = searchResponse;
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 8, 8000));

        ToolRuntimeContext.setSessionId("session-001");
        String searchResult;
        try {
            searchResult = tool.openviking_search("auth", null, null);
        } finally {
            ToolRuntimeContext.clear();
        }
        String findResult = tool.openviking_find("auth", null, null);

        assertTrue(findResult.contains("OpenViking find result"));
        assertTrue(findResult.contains("result=empty"));
        assertTrue(!findResult.contains("failed"));
        assertTrue(searchResult.contains("OpenViking search result"));
        assertTrue(searchResult.contains("session_bound=true"));
        assertTrue(searchResult.contains("result=empty"));
        assertTrue(!searchResult.contains("failed"));
    }

    @Test
    @DisplayName("search 未绑定当前 session 时应返回明确错误")
    void shouldRejectSearchWhenSessionUnbound() {
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                new StubOpenVikingClient(null),
                createProperties(5, 8, 8000)
        );

        String result = tool.openviking_search("auth", null, null);

        assertEquals("OpenViking search failed: current session is not bound.", result);
    }

    @Test
    @DisplayName("search 应从运行时上下文绑定 sessionId 并隐藏输出")
    void shouldBindSessionIdFromRuntimeContextAndHideItFromOutput() {
        OpenVikingFindResponse.MatchedContext context = new OpenVikingFindResponse.MatchedContext(
                "viking://session/current/memory.md",
                "memory",
                true,
                "Current session memory about authentication preferences.",
                "session",
                0.8123,
                "Session-aware match"
        );
        OpenVikingSearchResponse response = new OpenVikingSearchResponse(
                "ok",
                new OpenVikingSearchResponse.Result(List.of(context), List.of(), List.of(), 1),
                null,
                0.1
        );
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        client.searchResponse = response;
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 3, 8000));

        ToolRuntimeContext.setSessionId("session-001");
        String result;
        try {
            result = tool.openviking_search(" auth ", " viking://resources/ ", 99);
        } finally {
            ToolRuntimeContext.clear();
        }

        assertEquals(" auth ", client.lastSearchRequest.query());
        assertEquals("viking://resources/", client.lastSearchRequest.targetUri());
        assertEquals("session-001", client.lastSearchRequest.sessionId());
        assertEquals(3, client.lastSearchRequest.limit());
        assertEquals(3, client.lastSearchRequest.nodeLimit());
        assertEquals(true, client.lastSearchRequest.includeProvenance());
        assertTrue(result.contains("OpenViking session-aware search results for:  auth "));
        assertTrue(result.contains("session_bound=true"));
        assertTrue(result.contains("Score: 0.812"));
        assertTrue(result.contains("Reason: Session-aware match"));
        assertTrue(!result.contains("session-001"));
        assertTrue(!result.contains("session_id="));
    }
    @Test
    @DisplayName("forget 应校验 uri、确认状态和协议")
    void shouldValidateForgetArguments() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 8, 8000));

        assertEquals("OpenViking forget failed: uri is empty.", tool.openviking_forget(" ", true));
        assertEquals("OpenViking forget failed: uri must start with viking://.", tool.openviking_forget("file://resources/a.md", true));
        assertEquals("OpenViking forget failed: deletion is irreversible and requires confirmed=true after the user confirms the exact URI.", tool.openviking_forget("viking://resources/a.md", null));
        assertEquals("OpenViking forget failed: deletion is irreversible and requires confirmed=true after the user confirms the exact URI.", tool.openviking_forget("viking://resources/a.md", false));
        assertEquals(null, client.lastRemoveUri);
    }

    @Test
    @DisplayName("forget 应调用删除接口并使用精确 URI")
    void shouldCallRemoveWithTrimmedUriAndHardDelete() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        OpenVikingSearchTool tool = new OpenVikingSearchTool(client, createProperties(5, 8, 8000));

        String result = tool.openviking_forget(" viking://user/u-1/memories/feedback.md ", true);

        assertEquals("viking://user/u-1/memories/feedback.md", client.lastRemoveUri);
        assertEquals(false, client.lastRemoveRecursive);
        assertTrue(result.contains("OpenViking forget result"));
        assertTrue(result.contains("status=deleted"));
        assertTrue(result.contains("recursive=false"));
    }

    @Test
    @DisplayName("forget 客户端异常时应返回友好错误")
    void shouldReturnFriendlyMessageWhenForgetClientThrows() {
        OpenVikingSearchTool tool = new OpenVikingSearchTool(
                new ErrorStubOpenVikingClient("OpenViking remove failed: current key requires tenant headers."),
                createProperties(5, 8, 8000)
        );

        String result = tool.openviking_forget("viking://user/u-1/memories/feedback.md", true);

        assertEquals("OpenViking remove failed: current key requires tenant headers.", result);
    }

    @Test
    @DisplayName("search 公开方法不暴露 sessionId 参数")
    void shouldNotExposeSessionIdParameter() throws Exception {
        Method method = OpenVikingSearchTool.class.getMethod("openviking_search", String.class, String.class, Integer.class);

        assertEquals(3, method.getParameterCount());
    }

    private static OpenVikingProperties createProperties(int defaultLimit, int maxLimit, int maxResultChars) {
        OpenVikingProperties properties = new OpenVikingProperties();
        properties.setBaseUrl("http://localhost:1933");
        properties.setApiKey("dummy");
        properties.setTimeout(Duration.ofSeconds(10));
        properties.setDefaultLimit(defaultLimit);
        properties.setMaxLimit(maxLimit);
        properties.setMaxResultChars(maxResultChars);
        return properties;
    }

    private static final class StubOpenVikingClient extends OpenVikingClient {
        private final OpenVikingFindResponse response;
        private OpenVikingReadResponse readResponse;
        private OpenVikingReadResponse abstractResponse;
        private OpenVikingReadResponse overviewResponse;
        private OpenVikingReadResponse listResponse;
        private OpenVikingReadResponse treeResponse;
        private OpenVikingReadResponse globResponse;
        private OpenVikingReadResponse grepResponse;
        private OpenVikingSearchResponse searchResponse;
        private Integer lastLimit;
        private Integer lastNodeLimit;
        private String lastReadUri;
        private String lastReadAbstractUri;
        private String lastReadOverviewUri;
        private String lastListUri;
        private Integer lastListNodeLimit;
        private String lastTreeUri;
        private Integer lastTreeNodeLimit;
        private String lastGlobPattern;
        private String lastGlobUri;
        private Integer lastGlobNodeLimit;
        private String lastGrepUri;
        private String lastGrepPattern;
        private Boolean lastGrepCaseInsensitive;
        private Integer lastGrepNodeLimit;
        private String lastRemoveUri;
        private Boolean lastRemoveRecursive;
        private OpenVikingSearchRequest lastSearchRequest;

        private StubOpenVikingClient(OpenVikingFindResponse response) {
            super(createProperties(5, 8, 8000));
            this.response = response;
        }

        @Override
        public OpenVikingFindResponse find(com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindRequest request) {
            this.lastLimit = request.limit();
            this.lastNodeLimit = request.nodeLimit();
            return response;
        }

        @Override
        public OpenVikingReadResponse read(String uri) {
            this.lastReadUri = uri;
            return readResponse;
        }

        @Override
        public OpenVikingReadResponse readAbstract(String uri) {
            this.lastReadAbstractUri = uri;
            return abstractResponse;
        }

        @Override
        public OpenVikingReadResponse readOverview(String uri) {
            this.lastReadOverviewUri = uri;
            return overviewResponse;
        }

        @Override
        public OpenVikingReadResponse list(String uri, Integer nodeLimit) {
            this.lastListUri = uri;
            this.lastListNodeLimit = nodeLimit;
            return listResponse;
        }

        @Override
        public OpenVikingReadResponse tree(String uri, Integer nodeLimit) {
            this.lastTreeUri = uri;
            this.lastTreeNodeLimit = nodeLimit;
            return treeResponse;
        }

        @Override
        public OpenVikingReadResponse glob(String pattern, String uri, Integer nodeLimit) {
            this.lastGlobPattern = pattern;
            this.lastGlobUri = uri;
            this.lastGlobNodeLimit = nodeLimit;
            return globResponse;
        }

        @Override
        public OpenVikingReadResponse grep(String uri, String pattern, Boolean caseInsensitive, Integer nodeLimit) {
            this.lastGrepUri = uri;
            this.lastGrepPattern = pattern;
            this.lastGrepCaseInsensitive = caseInsensitive;
            this.lastGrepNodeLimit = nodeLimit;
            return grepResponse;
        }

        @Override
        public void remove(String uri, boolean recursive) {
            this.lastRemoveUri = uri;
            this.lastRemoveRecursive = recursive;
        }

        @Override
        public OpenVikingSearchResponse search(OpenVikingSearchRequest request) {
            this.lastSearchRequest = request;
            return searchResponse;
        }
    }

    private static final class ErrorStubOpenVikingClient extends OpenVikingClient {
        private final String message;

        private ErrorStubOpenVikingClient(String message) {
            super(createProperties(5, 8, 8000));
            this.message = message;
        }

        @Override
        public OpenVikingFindResponse find(com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindRequest request) {
            throw new OpenVikingClientException(message);
        }

        @Override
        public void remove(String uri, boolean recursive) {
            throw new OpenVikingClientException(message);
        }
    }
}
