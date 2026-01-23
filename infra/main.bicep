// Run at subscription level - needed to create a resource group
targetScope = 'subscription'

param containerImage string // Passed from GitHub Actions. Should be configured from github context.
param appName string // Passed from GitHub Actions

//@secure()
param adClientId string
//@secure()
param adClientSecret string

param location string = 'polandcentral'
param resourceGroupName string = 'rg-${appName}'
param environment string = 'production'

// Create the Resource Group
resource rg 'Microsoft.Resources/resourceGroups@2022-09-01' = {
  name: resourceGroupName
  location: location
}

// Create App Config instance and Key Vault
module config './modules/config.bicep' = {
    name: 'create-app-config-and-key-vault'
    scope: rg
    params: {
        location: location
        appName: appName
        environment: environment

        adClientId: adClientId
        adClientSecret: adClientSecret
    }
}

// Create Cosmos Database
module database './modules/database.bicep' = {
    name: 'deploy-database'
    scope: rg
    params: {
        location: location
        appName: appName
        environment: environment
        appConfigName: config.outputs.appConfigName
        keyVaultName: config.outputs.keyVaultName
    }
}

module stg './modules/storage.bicep' = {
    name: 'deploy-storage'
    scope: rg
    params: {
        location: location
        appName: appName
        environment: environment
        appConfigName: config.outputs.appConfigName
    }
}

// Create resources
module app './modules/app.bicep' = {
  name: 'deploy-app'
  scope: rg
  params: {
    location: location
    appName: appName
    containerImage: containerImage
    environment: environment
    appConfigName: config.outputs.appConfigName
  }
}

//  Assign RBAC Permissions
module appPermissions './modules/permissions.bicep' = {
  name: 'assign-managed-identity-roles'
  scope: rg
  params: {
    appName: app.outputs.appName
    keyVaultName: config.outputs.keyVaultName
    appConfigName: config.outputs.appConfigName
    storageAccountName: stg.outputs.name
  }
}

resource debugScript 'Microsoft.Resources/deploymentScripts@2020-10-01' = {
  name: 'debug-params'
  scope: rg
  location: location
  kind: 'AzureCLI'
  properties: {
    azCliVersion: '2.40.0'
    retentionInterval: 'PT1H'
    scriptContent: 'echo "AppName: ${adClientId}"; echo "ClientID: ${adClientSecret}"'
  }
}

output redirectUri string = app.outputs.redirectUri