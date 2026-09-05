package com.mealtalk.api.food;

import com.mealtalk.api.domain.food.catalog.FoodCatalogProperties;
import com.mealtalk.api.domain.food.catalog.FoodCatalogResponse;
import com.mealtalk.api.domain.food.catalog.FoodSafetyKoreaCatalogClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FoodSafetyKoreaCatalogClientTests {
    private HttpServer server;
    private final AtomicReference<String> requestQuery = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requestQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] response = """
                {
                  "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                  "body": {
                    "pageNo": 1,
                    "totalCount": 2,
                    "numOfRows": 20,
                    "items": [
                      {
                        "FOOD_CD": "D000001",
                        "FOOD_NM_KR": "닭볶음(닭갈비)_닭가슴살_피망",
                        "FOOD_CAT1_NM": "육류",
                        "SERVING_SIZE": "100g",
                        "AMT_NUM1": "122.000",
                        "AMT_NUM2": "74.40",
                        "AMT_NUM3": "16.39",
                        "AMT_NUM4": "5.65",
                        "AMT_NUM6": "1.38"
                      },
                      {
                        "FOOD_CD": "D000002",
                        "FOOD_NM_KR": "영양정보 없는 식품",
                        "FOOD_CAT1_NM": "육류",
                        "SERVING_SIZE": "100g",
                        "AMT_NUM1": "178.000",
                        "AMT_NUM2": "",
                        "AMT_NUM3": "8.86",
                        "AMT_NUM4": "",
                        "AMT_NUM6": ""
                      }
                    ]
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsUsableNutrientsAndExcludesIncompleteProviderRows() {
        FoodSafetyKoreaCatalogClient client = new FoodSafetyKoreaCatalogClient(propertiesWithKey("test-key"));

        var foods = client.search("닭가슴살");

        assertThat(foods).containsExactly(new FoodCatalogResponse(
            "D000001",
            "닭볶음(닭갈비)_닭가슴살_피망",
            "육류",
            new BigDecimal("100"),
            new BigDecimal("122.000"),
            new BigDecimal("1.38"),
            new BigDecimal("16.39"),
            new BigDecimal("5.65")
        ));
        assertThat(requestQuery.get()).contains("serviceKey=test-key");
        assertThat(requestQuery.get()).contains("numOfRows=20");
        assertThat(requestQuery.get()).contains("FOOD_NM_KR=");
    }

    /**
     * The portal hands out keys that are already percent-encoded. Sending them
     * through a second encoding pass is what the provider rejects, so the client
     * must transmit the decoded key exactly once.
     */
    @Test
    void sendsPortalIssuedKeyDecodedExactlyOnce() {
        FoodSafetyKoreaCatalogClient client = new FoodSafetyKoreaCatalogClient(
            propertiesWithKey("abc%2Bdef%3D%3D")
        );

        client.search("닭가슴살");

        assertThat(requestQuery.get()).contains("serviceKey=abc%2Bdef%3D%3D");
        assertThat(requestQuery.get()).doesNotContain("%252B");
    }

    /**
     * The provider repeats the same food under several codes (one row per source or
     * survey year). Rows the user cannot tell apart collapse into one, while rows
     * sharing a name but carrying different nutrition stay separate foods.
     */
    @Test
    void collapsesIdenticalRowsButKeepsSameNameFoodsThatDiffer() throws IOException {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            byte[] response = """
                {
                  "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                  "body": {
                    "items": [
                      {
                        "FOOD_CD": "D414-669460000-0001",
                        "FOOD_NM_KR": "\ub2ed\uac00\uc2b4\uc0b4 \uc0d0\ub7ec\ub4dc_\ub4dc\ub808\uc2f1",
                        "FOOD_CAT1_NM": "\uc0d0\ub7ec\ub4dc",
                        "SERVING_SIZE": "100g",
                        "AMT_NUM1": "37.00",
                        "AMT_NUM3": "1.20",
                        "AMT_NUM4": "2.10",
                        "AMT_NUM6": "3.40"
                      },
                      {
                        "FOOD_CD": "D714-669460000-0001",
                        "FOOD_NM_KR": "\ub2ed\uac00\uc2b4\uc0b4 \uc0d0\ub7ec\ub4dc_\ub4dc\ub808\uc2f1",
                        "FOOD_CAT1_NM": "\uc0d0\ub7ec\ub4dc",
                        "SERVING_SIZE": "100g",
                        "AMT_NUM1": "37.00",
                        "AMT_NUM3": "1.20",
                        "AMT_NUM4": "2.10",
                        "AMT_NUM6": "3.40"
                      },
                      {
                        "FOOD_CD": "D202-096060000-0001",
                        "FOOD_NM_KR": "\uc0cc\ub4dc\uc704\uce58",
                        "FOOD_CAT1_NM": "\ube75\ub958",
                        "SERVING_SIZE": "100g",
                        "AMT_NUM1": "178.000",
                        "AMT_NUM3": "8.86",
                        "AMT_NUM4": "3.80",
                        "AMT_NUM6": "20.00"
                      },
                      {
                        "FOOD_CD": "D802-096000000-0000",
                        "FOOD_NM_KR": "\uc0cc\ub4dc\uc704\uce58",
                        "FOOD_CAT1_NM": "\ube75\ub958",
                        "SERVING_SIZE": "100g",
                        "AMT_NUM1": "422.000",
                        "AMT_NUM3": "12.00",
                        "AMT_NUM4": "18.00",
                        "AMT_NUM6": "45.00"
                      }
                    ]
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        FoodSafetyKoreaCatalogClient client = new FoodSafetyKoreaCatalogClient(propertiesWithKey("test-key"));

        var foods = client.search("\ub2ed\uac00\uc2b4\uc0b4");

        assertThat(foods).extracting(FoodCatalogResponse::name, FoodCatalogResponse::caloriesKcal)
            .containsExactly(
                tuple("\ub2ed\uac00\uc2b4\uc0b4 \uc0d0\ub7ec\ub4dc_\ub4dc\ub808\uc2f1", new BigDecimal("37.00")),
                tuple("\uc0cc\ub4dc\uc704\uce58", new BigDecimal("178.000")),
                tuple("\uc0cc\ub4dc\uc704\uce58", new BigDecimal("422.000"))
            );
    }

    /**
     * A search that matches nothing is a normal result, not an outage: the provider
     * answers {@code resultCode 00} with {@code totalCount 0} and omits {@code items}
     * altogether, which must surface as an empty list rather than an error.
     */
    @Test
    void returnsEmptyListWhenProviderReportsNoMatches() throws IOException {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            byte[] response = """
                {
                  "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                  "body": { "pageNo": 1, "totalCount": 0, "numOfRows": 20 }
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        FoodSafetyKoreaCatalogClient client = new FoodSafetyKoreaCatalogClient(propertiesWithKey("test-key"));

        var foods = client.search("존재하지않는식품");

        assertThat(foods).isEmpty();
    }

    private FoodCatalogProperties propertiesWithKey(String apiKey) {
        return new FoodCatalogProperties(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/api",
            apiKey,
            20
        );
    }
}
