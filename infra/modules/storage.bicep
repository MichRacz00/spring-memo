param location string
param appName string
param environment string
param appConfigName string // Need this to save config

// --- Create Storage Resources ---
resource storage 'Microsoft.Storage/storageAccounts@2022-09-01' = {
  name: 'blob${appName}${uniqueString(resourceGroup().id)}'
  location: location
  sku: { name: 'Standard_LRS' }
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
  properties: { publicAccess: 'None' }
}

// --- Save Configuration to App Config ---
resource appConfig 'Microsoft.AppConfiguration/configurationStores@2023-03-01' existing = {
  name: appConfigName
}

// Save the Endpoint (Standard Spring Cloud Azure key)
resource kvStorageEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.storage.blob.endpoint$${environment}'
  properties: {
    value: storage.properties.primaryEndpoints.blob
    contentType: 'text/plain'
  }
}

// Save the Account Name (Optional, but good for completeness)
resource kvStorageAccountName 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.storage.blob.account-name$${environment}'
  properties: {
    value: storage.name
    contentType: 'text/plain'
  }
}

output name string = storage.name