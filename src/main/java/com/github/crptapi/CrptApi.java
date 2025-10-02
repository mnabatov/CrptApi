package com.github.crptapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final HttpClient client;
    private final String token;

    private final ObjectMapper objectMapper;

    private final Semaphore semaphore;
    private final int requestLimit;
    private final ScheduledExecutorService scheduler;

    public CrptApi(HttpClient client,
                   String token,
                   ObjectMapper objectMapper,
                   TimeUnit timeUnit,
                   Semaphore semaphore,
                   int requestLimit,
                   ScheduledExecutorService scheduler
    ) {
        this.client = client;
        this.token = token;
        this.objectMapper = objectMapper;
        objectMapper.findAndRegisterModules();

        this.requestLimit = requestLimit;
        this.semaphore = semaphore;
        this.scheduler = scheduler;
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (semaphore) {
                int toRelease = requestLimit - semaphore.availablePermits();
                if (toRelease > 0) {
                    semaphore.release(toRelease);
                }
            }
        }, 1, 1, timeUnit);
    }

    public CrptApi(TimeUnit timeUnit,
                   int requestLimit,
                   String token) {
        this(HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build(),
             token,
             new ObjectMapper(),
             timeUnit,
             new Semaphore(requestLimit, true),
             requestLimit,
             Executors.newSingleThreadScheduledExecutor()
        );
    }

    public CreateDocResponse createDoc(ProductDocument productDocument,
                                       String signature,
                                       String productGroup) throws InterruptedException, IOException, URISyntaxException {

        semaphore.acquire();

        String requestBody = createDocumentRequestBody(productDocument, productGroup, signature);
        String responseStr = sendCreateDocRequest(requestBody, productGroup);
        return objectMapper.readValue(responseStr, CreateDocResponse.class);
    }

    private String createDocumentRequestBody(ProductDocument productDocument,
                                             String productGroup,
                                             String signature) throws JsonProcessingException {
        Map<String, Object> map = Map.of(
                "document_format", "MANUAL",
                "product_document", toBase64String(objectMapper.writeValueAsString(productDocument)),
                "product_group", productGroup,
                "signature", toBase64String(signature),
                "type", "LP_INTRODUCE_GOODS"
        );
        return objectMapper.writeValueAsString(map);
    }

    private String sendCreateDocRequest(String requestPayload,
                                        String productGroup) throws IOException, InterruptedException, URISyntaxException {

        HttpRequest request = HttpRequest.newBuilder(
                        new URI("https://ismp.crpt.ru/api/v3/lk/documents/create?pg=" + productGroup))
                .version(HttpClient.Version.HTTP_2)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String toBase64String(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes());
    }

    static class CreateDocResponse {
        private String value;
        private String code;
        private String errorMessage;
        private String description;

        public CreateDocResponse() {}

        public CreateDocResponse(String value, String code, String errorMessage, String description) {
            this.value = value;
            this.code = code;
            this.errorMessage = errorMessage;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getCode() {
            return code;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getDescription() {
            return description;
        }
    }

    static class ProductDocument {
        private final Description description;
        private final @JsonProperty("doc_id") String docId;
        private final @JsonProperty("doc_status") String docStatus;
        private final @JsonProperty("doc_type") String docType;
        private final boolean importRequest;
        private final @JsonProperty("owner_inn") String ownerInn;
        private final @JsonProperty("participant_inn") String participantInn;
        private final @JsonProperty("producer_inn") String producerInn;
        private final @JsonProperty("production_date") LocalDate productionDate;
        private final @JsonProperty("production_type") String productionType;
        private final List<Product> products;
        private final @JsonProperty("reg_date") LocalDate regDate;
        private final @JsonProperty("reg_number") String regNumber;

        ProductDocument(
                Description description,
                String docId,
                String docStatus,
                String docType,
                boolean importRequest,
                String ownerInn,
                String participantInn,
                String producerInn,
                LocalDate productionDate,
                String productionType,
                List<Product> products,
                LocalDate regDate,
                String regNumber
        ) {
            this.description = description;
            this.docId=docId;
            this.docStatus = docStatus;
            this.docType=docType;
            this.importRequest = importRequest;
            this.ownerInn = ownerInn;
            this.participantInn = participantInn;
            this.producerInn= producerInn;
            this.productionDate = productionDate;
            this.productionType = productionType;
            this.products = products;
            this.regDate = regDate;
            this.regNumber = regNumber;
        }
        public Description getDescription() {
            return description;
        }

        public String getDocId() {
            return docId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public String getDocType() {
            return docType;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public String getProductionType() {
            return productionType;
        }

        public List<Product> getProducts() {
            return products;
        }

        public LocalDate getRegDate() {
            return regDate;
        }

        public String getRegNumber() {
            return regNumber;
        }
    }

    static class Description {
        private final String participateInn;
        Description(String participateInn) {
            this.participateInn = participateInn;
        }
        public String getParticipateInn() {
            return participateInn;
        }
    }

    static class Product {
        private final @JsonProperty("certificate_document") String certificateDocument;
        private final @JsonProperty("certificate_document_date") LocalDate certificateDocumentDate;
        private final @JsonProperty("certificate_document_number") String certificateDocumentNumber;
        private final @JsonProperty("owner_inn") String ownerInn;
        private final @JsonProperty("producer_inn") String producerInn;
        private final @JsonProperty("production_date") LocalDate productionDate;
        private final @JsonProperty("tnved_code") String tnvedCode;
        private final @JsonProperty("uit_code") String ultCode;
        private final @JsonProperty("uitu_code") String uituCode;

        Product(String certificateDocument,
                LocalDate certificateDocumentDate,
                String certificateDocumentNumber,
                String ownerInn,
                String producerInn,
                LocalDate productionDate,
                String tnvedCode,
                String ultCode,
                String uituCode) {
            this.certificateDocument = certificateDocument;
            this.certificateDocumentDate = certificateDocumentDate;
            this.certificateDocumentNumber = certificateDocumentNumber;
            this.ownerInn = ownerInn;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.tnvedCode = tnvedCode;
            this.ultCode = ultCode;
            this.uituCode = uituCode;
        }
        public String getCertificateDocument() {
            return certificateDocument;
        }

        public LocalDate getCertificateDocumentDate() {
            return certificateDocumentDate;
        }

        public String getCertificateDocumentNumber() {
            return certificateDocumentNumber;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public String getTnvedCode() {
            return tnvedCode;
        }

        public String getUltCode() {
            return ultCode;
        }

        public String getUituCode() {
            return uituCode;
        }
    }
}