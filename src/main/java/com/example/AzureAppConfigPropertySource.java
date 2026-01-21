package com.example;

import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.data.appconfiguration.models.ConfigurationSetting;
import com.azure.data.appconfiguration.models.SettingSelector;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.Profiles;

import java.util.Properties;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class AzureAppConfigPropertySource implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            ConfigurationClientBuilder builder = new ConfigurationClientBuilder()
                    .endpoint("https://memo-config.azconfig.io")
                    .credential(new DefaultAzureCredentialBuilder().build());

            ConfigurationClient client = builder.buildClient();

            // Better way to check profiles in Spring
            boolean isProduction = environment.acceptsProfiles(Profiles.of("production"));

            Properties properties = new Properties();
            SettingSelector selector = new SettingSelector();

            if (isProduction) {
                // Fetch both default (no label) and production labeled keys
                selector.setLabelFilter("\0,production");
            } else {
                // Fetch only default keys
                selector.setLabelFilter("\0");
            }

            for (ConfigurationSetting setting : client.listConfigurationSettings(selector)) {
                if (setting.getKey() != null && setting.getValue() != null) {

                    // RAW KEY - No replacement of "/" with "."
                    String key = setting.getKey();
                    String currentLabel = setting.getLabel();

                    // LOGIC: Priority handling
                    if (!properties.containsKey(key)) {
                        // New key found, add it
                        properties.setProperty(key, setting.getValue());
                        System.out.println("Loaded: " + key);
                    } else {
                        // Key exists. Only overwrite if this is the "production" override
                        if (isProduction && "production".equals(currentLabel)) {
                            properties.setProperty(key, setting.getValue());
                            System.out.println("Overwriting with Production value: " + key);
                        }
                    }
                }
            }


            if (!properties.isEmpty()) {
                PropertiesPropertySource propertySource = new PropertiesPropertySource("azureAppConfig", properties);
                // Add first to override local application.properties
                environment.getPropertySources().addFirst(propertySource);
            }

        } catch (Exception e) {
            // Log the error first
            System.err.println("FATAL: Failed to load Azure App Configuration: " + e.getMessage());
            throw new RuntimeException("Failed to load mandatory Azure App Configuration", e);
        }
    }
}