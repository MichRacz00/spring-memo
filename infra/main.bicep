param location string = resourceGroup().location
param appName string = 'memo'
param containerImage string // Passed from GitHub Actions. Should be configured from github context.

// 1. Minimal Log Analytics (Required by the Environment)
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: 'logs-${appName}'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
    // Daily cap ensures logs do not exceed the free 5GB limit
    workspaceCapping: {
      dailyQuotaGb: 0.19
    }
  }
}

// 2. Minimal Environment (The "Cluster" for your app)
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

// 3. Minimal Container App
resource app 'Microsoft.App/containerApps@2023-05-01' = {
  name: appName
  location: location
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
          resources: { cpu: 0.25, memory: '0.5Gi' }
        }
      ]
      scale: {
        minReplicas: 0 // Scale to zero to save money
        maxReplicas: 1
      }
    }
  }
}

output url string = app.properties.configuration.ingress.fqdn
