package com.example.memo;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;

public final class Database {

    private static Database INSTANCE;

    private static String key;
    private static String endpoint;
    private CosmosClient cosmosClient;

    public static Database getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new Database();
        }
        return INSTANCE;
    }

    private Database() {
        this.key = requireEnv("DATABASE_KEY");
        this.endpoint = requireEnv("DATABASE_ENDPOINT");
    }

    private void connect() {
        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .buildClient();
    }

    private static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return v;
    }

    public CosmosClient getCosmosClient() {
        if (cosmosClient == null) connect();
        return cosmosClient;
    }
}
