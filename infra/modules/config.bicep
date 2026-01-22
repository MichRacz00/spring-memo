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

var keyVaultName = 'kv-${appName}-${uniqueString(resourceGroup().id)}'

resource recoverKeyVault 'Microsoft.Resources/deploymentScripts@2020-10-01' = {
  name: 'recover-kv-script'
  location: location
  kind: 'AzureCLI'
  properties: {
    azCliVersion: '2.40.0'
    retentionInterval: 'PT1H'
    timeout: 'PT5M'
    environmentVariables: [
      { name: 'KV_NAME', value: keyVaultName }
      { name: 'LOCATION', value: location }
    ]
    scriptContent: '''
      echo "Checking for soft-deleted Key Vault: $KV_NAME"

      # Check if vault exists in deleted state
      DELETED_STATE=$(az keyvault list-deleted --resource-type vault --query "[?name=='$KV_NAME'].id" -o tsv)

      if [ -n "$DELETED_STATE" ]; then
        echo "Found soft-deleted vault. Recovering..."
        az keyvault recover --name $KV_NAME --location $LOCATION
        echo "Recovery command sent. Waiting for propagation..."
        sleep 20
      else
        echo "No soft-deleted vault found. Proceeding with creation."
      fi
    '''
  }
}

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: keyVaultName
  location: location
  properties: {
    sku: { family: 'A', name: 'standard' }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enabledForTemplateDeployment: true
  }
  // CRITICAL: Wait for the recovery script to finish
    dependsOn: [
      recoverKeyVault
    ]
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