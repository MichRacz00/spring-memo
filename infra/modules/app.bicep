param location string
param appName string
param containerImage string
param environment string

// --- INFRASTRUCTURE REFERENCES (Passed from main.bicep) ---
param appConfigName string

// Log Analytics & Environment (required)
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: 'logs-${appName}'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
    workspaceCapping: { dailyQuotaGb: json('0.19') }
  }
}

resource env 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: 'env-${appName}'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
    workloadProfiles: [{ name: 'Consumption', workloadProfileType: 'Consumption' }]
  }
}

// Reference Existing Infrastructure (To write properties)
resource appConfig 'Microsoft.AppConfiguration/configurationStores@2023-03-01' existing = {
  name: appConfigName
}

// Container App
resource app 'Microsoft.App/containerApps@2023-05-01' = {
  name: appName
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      ingress: {
        external: true
        targetPort: 8080
      }
    }
    template: {
      containers: [
        {
          name: appName
          image: containerImage
          resources: { cpu: json('0.5'), memory: '1.0Gi' }
          env: [
            {
              name: 'ENVIRONMENT'
              value: environment
            }
            {
              name: 'AZURE_APP_CONFIG_ENDPOINT'
              value: appConfig.properties.endpoint
            }
            {
              // CRITICAL: Fixes Key Vault Auth Error
              name: 'AZURE_TENANT_ID'
              value: subscription().tenantId
            }
          ]
          probes: [
            {
              type: 'Startup'
              tcpSocket: { port: 8080 }
              initialDelaySeconds: 10
              periodSeconds: 5
              failureThreshold: 30
            }
          ]
        }
      ]
      scale: { minReplicas: 0, maxReplicas: 1 }
    }
  }
}

// Save App Specific Config
resource kvRedirectUri 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.active-directory.post-logout-redirect-uri$${environment}'
  properties: {
    value: 'https://${app.properties.configuration.ingress.fqdn}'
    contentType: 'text/plain'
  }
}

output principalId string = app.identity.principalId