param location string
param appName string

param environment string

resource appConfig 'Microsoft.AppConfiguration/configurationStores@2023-03-01' = {
  name: 'config-${appName}-${uniqueString(resourceGroup().id)}'
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

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: 'kv-${appName}-${uniqueString(resourceGroup().id)}'
  location: location
  properties: {
    sku: { family: 'A', name: 'standard' }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enabledForTemplateDeployment: true
  }
}

// Save key vault endpoint in app config
resource kvKeyVaultEndpoint 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'keyvault.endpoint$${environment}'
  properties: {
    value: keyVault.properties.vaultUri
    contentType: 'text/plain'
  }
}

// -------------- static config below --------------
resource kvMaxFileSize 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.servlet.multipart.max-file-size'
  properties: {
    value: '10MB'
    contentType: 'text/plain'
  }
}

resource kvMaxRequestSize 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.servlet.multipart.max-request-size'
  properties: {
    value: '10MB'
    contentType: 'text/plain'
  }
}

resource kvTenantId 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.active-directory.profile.tenant-id'
  properties: {
    value: 'common'
    contentType: 'text/plain'
  }
}

resource kvADEnabled 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.active-directory.enabled'
  properties: {
    value: 'true'
    contentType: 'text/plain'
  }
}

resource kvGraphScopes 'Microsoft.AppConfiguration/configurationStores/keyValues@2023-03-01' = {
  parent: appConfig
  name: 'spring.cloud.azure.active-directory.authorization-clients.graph.scopes[0]'
  properties: {
    value: 'https://graph.microsoft.com/User.Read'
    contentType: 'text/plain'
  }
}

output appConfigName string = appConfig.name
output keyVaultName string = keyVault.name