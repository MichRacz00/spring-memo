package com.example.memo;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Component
public class Database {

    @Value("${database.key}")
    private String key;

    @Value("${database.endpoint}")
    private String endpoint;

    private CosmosClient cosmosClient;

    @PostConstruct
    private void connect() {
        cosmosClient = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .buildClient();
    }

    public CosmosClient getCosmosClient() {
        return cosmosClient;
    }
}
