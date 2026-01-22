param location string
param appName string
param environment string

param appConfigName string
param keyVaultName string

// Create Cosmos Account
resource cosmosAccount 'Microsoft.DocumentDB/databaseAccounts@2023-04-15' = {
  name: 'cosmos-${appName}-${uniqueString(resourceGroup().id)}'
  location: location
  kind: 'GlobalDocumentDB'
  properties: {
    databaseAccountOfferType: 'Standard'
    capabilities: [{ name: 'EnableServerless' }]
    locations: [
      {
        locationName: location
        failoverPriority: 0
        isZoneRedundant: false
      }
    ]
    consistencyPolicy: { defaultConsistencyLevel: 'Session' }
  }
}

// Create Database
resource cosmosDb 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases@2023-04-15' = {
  parent: cosmosAccount
  name: 'memo'
  properties: {
    resource: { id: 'memo' }
  }
}

// Create Container (table)
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

resource kvCosmosDbName 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.cosmos.database$${environment}'
  properties: {
    value: cosmosDb.name
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

// Save Key to Key Vault (Using existing reference)
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: keyVaultName
}

resource cosmosDatabaseKey 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'database-key'
  properties: { value: cosmosAccount.listKeys().primaryMasterKey }
}

resource appConfig 'Microsoft.AppConfiguration/configurationStores@2023-03-01' existing = {
  name: appConfigName
}

resource kvDatabaseEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'database.endpoint$${environment}'
  properties: {
    value: cosmosAccount.properties.documentEndpoint
    contentType: 'text/plain'
  }
}
