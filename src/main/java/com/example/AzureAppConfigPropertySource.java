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
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AzureAppConfigPropertySource implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            ConfigurationClientBuilder builder = new ConfigurationClientBuilder()
                    .endpoint("https://memo-config.azconfig.io")
                    .credential(new DefaultAzureCredentialBuilder().build());

            ConfigurationClient client = builder.buildClient();

            Properties properties = new Properties();

            // Fetch all configuration settings
            for (ConfigurationSetting setting : client.listConfigurationSettings(new SettingSelector())) {
                if (setting.getKey() != null && setting.getValue() != null) {
                    properties.setProperty(setting.getKey(), setting.getValue());
                    System.out.println(setting.getKey());
                }
            }

            // Add to environment
            PropertiesPropertySource propertySource = new PropertiesPropertySource("azureAppConfig", properties);
            environment.getPropertySources().addFirst(propertySource);

        } catch (Exception e) {
            System.err.println("Failed to load Azure App Configuration: " + e.getMessage());
        }
    }
}

