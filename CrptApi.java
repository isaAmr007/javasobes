import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final Semaphore requestSemaphore;
    private final ObjectMapper objectMapper;
    private final String apiUrl;

    public CrptApi(TimeUnit timeUnit, int requestLimit, String apiUrl) {
        this.requestSemaphore = new Semaphore(requestLimit);
        this.objectMapper = new ObjectMapper();
        this.apiUrl = apiUrl;

        Runnable scheduler = () -> {
            while (true) {
                try {
                    Thread.sleep(timeUnit.toMillis(1));
                    requestSemaphore.release(requestLimit - requestSemaphore.availablePermits());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(scheduler).start();
    }

    public void createDocument(Document document, String signature) {
        try {
            requestSemaphore.acquire();
            String requestBody = buildRequestBody(document, signature);
            sendPostRequest(apiUrl, requestBody);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            requestSemaphore.release();
        }
    }

    private String buildRequestBody(Document document, String signature) throws IOException {
        return objectMapper.writeValueAsString(document);
    }

    private void sendPostRequest(String url, String requestBody) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(requestBody));

            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }
        }
    }

    private static class Document {
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5, "https://ismp.crpt.ru/api/v3/lk/documents/create");

        Document document = new Document();
        String signature = "exampleSignature";

        crptApi.createDocument(document, signature);
    }
}
