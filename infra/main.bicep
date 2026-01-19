@description('The location for all resources.')
param location string = resourceGroup().location

@description('The name of the Container App.')
param appName string = 'memo-app'

@description('The Docker image to deploy. Fetched from github context in github actions.')
param containerImage string

// --- 1. Log Analytics Workspace (Required for Container Apps) ---
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: 'log-${appName}'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

// --- 2. Container Apps Environment ---
resource containerAppEnv 'Microsoft.App/managedEnvironments@2023-05-01' = {
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
    // "Consumption" workload profile is the standard serverless option
    workloadProfiles: [
      {
        name: 'Consumption'
        workloadProfileType: 'Consumption'
      }
    ]
  }
}

// --- 3. Container App ---
resource containerApp 'Microsoft.App/containerApps@2023-05-01' = {
  name: appName
  location: location
  properties: {
    managedEnvironmentId: containerAppEnv.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true // Publicly accessible
        targetPort: targetPort
        allowInsecure: false
        traffic: [
          {
            latestRevision: true
            weight: 100
          }
        ]
      }
      // If using a private registry (GHCR), uncomment and fill these:
      /*
      registries: [
        {
          server: 'ghcr.io'
          username: 'GITHUB_USERNAME'
          passwordSecretRef: 'ghcr-password'
        }
      ]
      secrets: [
        {
          name: 'ghcr-password'
          value: 'YOUR_PAT_TOKEN'
        }
      ]
      */
    }
    template: {
      containers: [
        {
          name: appName
          image: containerImage
          resources: {
            cpu: json(cpuCore)
            memory: memorySize
          }
          // Add your Environment Variables here
          env: [
            {
              name: 'SPRING_PROFILES_ACTIVE'
              value: 'prod'
            }
          ]
        }
      ]
