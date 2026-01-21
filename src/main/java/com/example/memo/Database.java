package com.example.memo;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Component
public class Database {

    @Value("${keyvault.endpoint}")
    private String keyvaultEndpoint;

    @Value("${database.endpoint}")
    private String databaseEndpoint;

    private CosmosClient cosmosClient;

    @PostConstruct
    private void connect() {
        SecretClient secretClient = new SecretClientBuilder()
                .vaultUrl(keyvaultEndpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();

        String key = secretClient.getSecret("database-key").getValue();

        cosmosClient = new CosmosClientBuilder()
                .endpoint(databaseEndpoint)
                .key(key)
                .buildClient();
    }

    public CosmosClient getCosmosClient() {
        return cosmosClient;
    }
}
