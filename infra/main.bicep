// Run at subscription level - needed to create a resource group
targetScope = 'subscription'

param containerImage string // Passed from GitHub Actions. Should be configured from github context.
param appName string // Passed from GitHub Actions

param location string = 'polandcentral'
param resourceGroupName string = 'rg-${appName}'
param environment string = 'production'

// 1. Create the Resource Group
resource rg 'Microsoft.Resources/resourceGroups@2022-09-01' = {
  name: resourceGroupName
  location: location
}

module appResources './resources.bicep' = {
  name: 'deploy-app-resources'
  scope: rg
  params: {
    location: location
    appName: appName
    containerImage: containerImage
    environment: environment
  }
}