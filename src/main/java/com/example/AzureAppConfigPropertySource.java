package com.example;

import com.azure.core.credential.TokenCredential;
import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.data.appconfiguration.models.ConfigurationSetting;
import com.azure.data.appconfiguration.models.SettingSelector;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertiesPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class AzureAppConfigPropertySource implements EnvironmentPostProcessor {

    // Mapper to parse the Key Vault JSON reference
    private final ObjectMapper jsonMapper = new ObjectMapper();

    // Cache Key Vault clients to avoid recreating them for every secret
    private final Map<String, SecretClient> secretClientCache = new HashMap<>();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            System.out.println("--- Starting Azure App Config Load (With Key Vault Support) ---");

            // 1. Share the credential between App Config and Key Vault
            TokenCredential credential = new DefaultAzureCredentialBuilder().build();

            String appConfigEndpoint = System.getenv("AZURE_APP_CONFIG_ENDPOINT");
            if (appConfigEndpoint == null || appConfigEndpoint.isBlank()) {
                // FALLBACK: Try reading from .env file in project root
                try {
                    java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
                    if (java.nio.file.Files.exists(envPath)) {
                        System.out.println("Reading configuration from local .env file...");
                        appConfigEndpoint = java.nio.file.Files.lines(envPath)
                                .filter(line -> line.trim().startsWith("AZURE_APP_CONFIG_ENDPOINT="))
                                .map(line -> line.split("=", 2)[1].trim())
                                .findFirst()
                                .orElse(null);
                    }
                } catch (Exception ignored) {
                    // Ignore parsing errors, we will throw exception below if still null
                }
            }

            ConfigurationClientBuilder builder = new ConfigurationClientBuilder()
                    .endpoint(appConfigEndpoint)
                    .credential(credential);

            ConfigurationClient client = builder.buildClient();

            boolean isProduction = environment.acceptsProfiles(Profiles.of("production"));

            Properties properties = new Properties();
            SettingSelector selector = new SettingSelector();

            if (isProduction) {
                selector.setLabelFilter("\0,production");
            } else {
                selector.setLabelFilter("\0");
            }

            for (ConfigurationSetting setting : client.listConfigurationSettings(selector)) {
                if (setting.getKey() != null && setting.getValue() != null) {

                    String key = setting.getKey();
                    String value = setting.getValue();
                    String contentType = setting.getContentType();
                    String currentLabel = setting.getLabel();

                    // CRITICAL FIX 2: Resolve Key Vault References
                    if ("application/vnd.microsoft.appconfig.keyvaultref+json;charset=utf-8".equals(contentType)) {
                        try {
                            value = resolveKeyVaultSecret(value, credential);
                            System.out.println("Resolved Key Vault Secret for: " + key);
                        } catch (Exception e) {
                            System.err.println("ERROR: Could not resolve secret for " + key + ". Using raw value.");
                            e.printStackTrace();
                        }
                    }

                    // LOGIC: Priority handling
                    if (!properties.containsKey(key)) {
                        properties.setProperty(key, value);
                        // Don't print value if it's a secret!
                        System.out.println("Loaded: " + key);
                    } else {
                        if (isProduction && "production".equals(currentLabel)) {
                            properties.setProperty(key, value);
                            System.out.println("Overwriting with Production value: " + key);
                        }
                    }
                }
            }

            if (!properties.isEmpty()) {
                PropertiesPropertySource propertySource = new PropertiesPropertySource("azureAppConfig", properties);
                environment.getPropertySources().addFirst(propertySource);
                System.out.println("--- Azure Config Loaded Successfully ---");
            }

        } catch (Exception e) {
            System.err.println("FATAL: Failed to load Azure App Configuration: " + e.getMessage());
            throw new RuntimeException("Failed to load mandatory Azure App Configuration", e);
        }
    }

    /**
     * Parses the JSON Key Vault reference and fetches the actual secret.
     */
    private String resolveKeyVaultSecret(String jsonValue, TokenCredential credential) throws Exception {
        // 1. Parse JSON: {"uri":"https://myvault.vault.azure.net/secrets/mySecret/..."}
        JsonNode root = jsonMapper.readTree(jsonValue);
        String secretUriString = root.get("uri").asText();
        URI secretUri = new URI(secretUriString);

        // 2. Extract Vault URL (https://myvault.vault.azure.net)
        String vaultUrl = "https://" + secretUri.getHost();

        // 3. Get or Create Secret Client
        SecretClient secretClient = secretClientCache.computeIfAbsent(vaultUrl, url ->
                new SecretClientBuilder()
                        .vaultUrl(url)
                        .credential(credential)
                        .buildClient()
        );

        // 4. Extract Secret Name
        // URI Path is usually: /secrets/<name>/<version>
        String[] pathSegments = secretUri.getPath().split("/");
        String secretName = pathSegments[2]; // Index 2 is the name

        // 5. Fetch Value
        return secretClient.getSecret(secretName).getValue();
    }
}
