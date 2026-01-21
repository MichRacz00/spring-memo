param location string = resourceGroup().location
param appName string = 'memo'
param containerImage string // Passed from GitHub Actions. Should be configured from github context.
param environment string = 'production'

// Minimal log analytics, required
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
      dailyQuotaGb: json('0.19')
    }
  }
}

// Minimal environment for the container app
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

// Container app hosting docker image
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
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
          env: [
            {
                name: 'ENVIRONMENT'
                value: 'production'
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 1
      }
    }
  }
}

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: 'kv-${appName}-${uniqueString(resourceGroup().id)}' // Must be globally unique
  location: location
  properties: {
    sku: { family: 'A', name: 'standard' }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enabledForTemplateDeployment: true
  }
}

// Assign access to the key vault for the app
resource kvRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, app.id, 'KeyVaultSecretsUser')
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')
    principalId: app.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource cosmosAccount 'Microsoft.DocumentDB/databaseAccounts@2023-04-15' = {
  name: 'cosmos-${appName}-${uniqueString(resourceGroup().id)}'
  location: location
  kind: 'GlobalDocumentDB'
  properties: {
    databaseAccountOfferType: 'Standard'
    capabilities: [
      { name: 'EnableServerless' }
    ]
    locations: [
      {
        locationName: location
        failoverPriority: 0
        isZoneRedundant: false
      }
    ]
    consistencyPolicy: {
      defaultConsistencyLevel: 'Session'
    }
  }
}

resource cosmosDb 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases@2023-04-15' = {
  parent: cosmosAccount
  name: 'memo'
  properties: {
    resource: { id: 'memo' }
  }
}

resource cosmosContainer 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers@2023-04-15' = {
  parent: cosmosDb
  name: 'memo-cards'
  properties: {
    resource: {
      id: 'memo-cards'
      partitionKey: { paths: ['/userId'], kind: 'Hash' }
    }
  }
}

resource cosmosDatabaseKey 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'database-key'
  properties: { value: cosmosAccount.listKeys().primaryMasterKey }
}

resource storage 'Microsoft.Storage/storageAccounts@2022-09-01' = {
  name: 'blob${appName}${uniqueString(resourceGroup().id)}' // must be also configured via env vars
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    accessTier: 'Hot'
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
  }
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2022-09-01' = {
  parent: storage
  name: 'default'
}

resource backgroundsContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2022-09-01' = {
  parent: blobService
  name: 'backgrounds'
  properties: {
    publicAccess: 'None'
  }
}

resource blobStorageRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(storage.id, app.id, 'StorageBlobDataContributor')
  scope: storage
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', 'ba92f5b4-2d11-453d-a403-e96b0029c9fe')
    principalId: app.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource appConfig 'Microsoft.AppConfiguration/configurationStores@2023-03-01' = {
  name: 'config-${appName}'
  location: location
  sku: {
    name: 'free'
  }
  properties: {
    enablePurgeProtection: false
    softDeleteRetentionInDays: 0
  }
  identity: {
    type: 'SystemAssigned'
  }
}

resource kvCosmosEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.servlet.multipart.max-file-size'
  properties: {
    value: '10MB'
    contentType: 'text/plain'
  }
}

resource kvCosmosEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.servlet.multipart.max-request-size'
  properties: {
    value: '10MB'
    contentType: 'text/plain'
  }
}

resource kvCosmosEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.active-directory.profile.tenant-id'
  properties: {
    value: 'common'
    contentType: 'text/plain'
  }
}

resource kvCosmosEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.active-directory.enabled'
  properties: {
    value: 'true'
    contentType: 'text/plain'
  }
}

resource kvCosmosEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.active-directory.authorization-clients.graph.scopes[0]'
  properties: {
    value: 'https://graph.microsoft.com/User.Read'
    contentType: 'text/plain'
  }
}

resource kvCosmosEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.cosmos.endpoint$${environment}'
  properties: {
    value: cosmosAccount.properties.documentEndpoint
    contentType: 'text/plain'
  }
}

resource kvCosmosDbName 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.cosmos.database$${environment}'
  properties: {
    value: 'MemoDB'
    contentType: 'text/plain'
  }
}

resource kvStorageEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.storage.blob.endpoint$${environment}'
  properties: {
    value: storage.properties.primaryEndpoints.blob
    contentType: 'text/plain'
  }
}

resource kvStorageAccount 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.storage.blob.account-name$${environment}'
  properties: {
    value: storage.name
    contentType: 'text/plain'
  }
}

resource kvCosmosEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.active-directory.post-logout-redirect-uri'
  properties: {
    value: 'https://${app.properties.configuration.ingress.fqdn}$${production}'
    contentType: 'text/plain'
  }
}
