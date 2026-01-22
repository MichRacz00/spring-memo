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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class AzureAppConfigPropertySource implements EnvironmentPostProcessor {

    private static final String APP_CONFIG_ENV_VAR = "AZURE_APP_CONFIG_ENDPOINT";
    private static final String ENVIRONMENT_VAR = "ENVIRONMENT";
    private static final String KEY_VAULT_CONTENT_TYPE = "application/vnd.microsoft.appconfig.keyvaultref+json;charset=utf-8";

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final Map<String, SecretClient> secretClientCache = new ConcurrentHashMap<>();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {

            System.out.println("--- Starting Azure App Config Load ---");

            // 1. Resolve Variables (Cloud Env -> Local .env)
            String endpoint = resolveEnvVar(APP_CONFIG_ENV_VAR);
            String envName = resolveEnvVar(ENVIRONMENT_VAR);

            System.out.println("Resolved Endpoint: " + endpoint);
            System.out.println("Resolved Environment: " + envName);

            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("Missing required configuration: " + APP_CONFIG_ENV_VAR);
            }

            // 2. Setup Clients
            TokenCredential credential = new DefaultAzureCredentialBuilder().build();
            ConfigurationClient client = new ConfigurationClientBuilder()
                    .endpoint(endpoint)
                    .credential(credential)
                    .buildClient();

            // 3. Determine Production Status
            boolean isProduction = "production".equalsIgnoreCase(envName);
            System.out.println("isProduction set to: " + isProduction);

            // 4. Fetch Configuration
            Properties properties = fetchConfiguration(client, credential, isProduction);

            if (!properties.isEmpty()) {
                PropertiesPropertySource propertySource = new PropertiesPropertySource("azureAppConfig", properties);
                environment.getPropertySources().addFirst(propertySource);
                System.out.println("--- Azure Config Loaded Successfully ---");
            }
        } catch (Exception e) {
            System.err.println("FATAL: Failed to load Azure App Configuration: " + e.getMessage());
            // Fail hard to prevent app starting with partial config
            throw new RuntimeException("Failed to load mandatory Azure App Configuration", e);
        }
    }

    private String resolveEnvVar(String variableName) {
        // Priority 1: System Environment (Cloud/Docker)
        String value = System.getenv(variableName);
        if (value != null && !value.isBlank()) {
            return value;
        }

        // Priority 2: Local .env file
        try {
            Path envPath = Paths.get(".env");
            if (Files.exists(envPath)) {
                try (Stream<String> lines = Files.lines(envPath)) {
                    return lines
                            .filter(line -> line.trim().startsWith(variableName + "="))
                            .map(line -> line.split("=", 2)[1].trim())
                            .findFirst()
                            .orElse(null);
                }
            }
        } catch (Exception ignored) {
            // Ignore parsing errors
        }
        return null;
    }

    private Properties fetchConfiguration(ConfigurationClient client, TokenCredential credential, boolean isProduction) {
        Properties properties = new Properties();
        SettingSelector selector = new SettingSelector();

        // Fetch keys with no label (\0) AND 'production' label if applicable
        if (isProduction) {
            selector.setLabelFilter("\0,production");
        } else {
            selector.setLabelFilter("\0");
        }

        for (ConfigurationSetting setting : client.listConfigurationSettings(selector)) {
            if (setting.getKey() == null || setting.getValue() == null) continue;

            String key = setting.getKey();
            String value = setting.getValue();
            String label = setting.getLabel();

            // Handle Key Vault References
            if (KEY_VAULT_CONTENT_TYPE.equals(setting.getContentType())) {
                try {
                    value = resolveKeyVaultSecret(value, credential);
                    System.out.println("Resolved Key Vault Secret for: " + key);
                } catch (Exception e) {
                    System.err.println("ERROR: Could not resolve secret for " + key + ". Using raw value.");
                    e.printStackTrace();
                }
            }

            // Priority Logic: Add if new, or overwrite if Production label matches
            if (!properties.containsKey(key)) {
                properties.setProperty(key, value);
                System.out.println("Loaded: " + key);
            } else if (isProduction && "production".equals(label)) {
                properties.setProperty(key, value);
                System.out.println("Overwriting with Production value: " + key);
            }
        }
        return properties;
    }

    private String resolveKeyVaultSecret(String jsonValue, TokenCredential credential) throws Exception {
        JsonNode root = jsonMapper.readTree(jsonValue);
        URI secretUri = new URI(root.get("uri").asText());

        String vaultUrl = "https://" + secretUri.getHost();

        SecretClient secretClient = secretClientCache.computeIfAbsent(vaultUrl, url ->
                new SecretClientBuilder()
                        .vaultUrl(url)
                        .credential(credential)
                        .buildClient()
        );

        // URI Path format: /secrets/<name>/<version>
        String[] pathSegments = secretUri.getPath().split("/");
        String secretName = pathSegments[2];

        return secretClient.getSecret(secretName).getValue();
    }
}
