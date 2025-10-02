package com.github.crptapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CrptApiTest {
    private static Logger logger = LoggerFactory.getLogger(CrptApiTest.class);

    @Mock
    private HttpClient client;
    @Mock
    private HttpResponse<String> response;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() throws IOException, InterruptedException {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();


        when(response.body()).thenReturn(
                objectMapper.writeValueAsString(new CrptApi.CreateDocResponse(
                        "value", "code", "error", "descrp"
                ))
        );

        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }

    @Test
    public void testRateLimitOnePerSecond() throws InterruptedException, URISyntaxException, IOException {
        var api = createTestCrptApi(TimeUnit.SECONDS, 1);
        int numOfCalls = 10;

        long start = System.currentTimeMillis();
        for (int i = 1; i <= numOfCalls; i++) {
            api.createDoc(createTestProduct(), "signature", "productGroup");
            logger.info("{}/{} call executed", i, numOfCalls);
        }
        long duration = System.currentTimeMillis() - start;

        assertTrue(duration >= 9000);
    }

    @Test
    public void testRateLimitTwoPerMinute() throws InterruptedException, URISyntaxException, IOException {
        var api = createTestCrptApi(TimeUnit.MINUTES, 2);
        int numOfCalls = 3;

        long start = System.currentTimeMillis();
        for (int i = 1; i <= numOfCalls; i++) {
            api.createDoc(createTestProduct(), "signature", "productGroup");
            logger.info("{}/{} call executed", i, numOfCalls);
            Thread.sleep(5000);
        }
        long duration = System.currentTimeMillis() - start;

        assertTrue(duration >= 60000);
    }

    private CrptApi createTestCrptApi(TimeUnit timeUnit, int requestLimit) {
        return new CrptApi(
                client,
                "",
                objectMapper,
                timeUnit,
                new Semaphore(requestLimit, true),
                requestLimit,
                Executors.newSingleThreadScheduledExecutor()
        );
    }

    private CrptApi.ProductDocument createTestProduct() {
        return Mockito.mock(CrptApi.ProductDocument.class);
    }
}