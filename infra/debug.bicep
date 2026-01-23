param location string
param clientId string
param clientSecret string

resource debugScript 'Microsoft.Resources/deploymentScripts@2020-10-01' = {
  name: 'debug-params'
  location: location
  kind: 'AzureCLI'
  properties: {
    azCliVersion: '2.40.0'
    retentionInterval: 'PT1H'
    // WARNING: This prints the secret to Azure Activity Logs!
    scriptContent: 'echo "ClientID: ${clientId}"; echo "Secret: ${clientSecret}"' 
  }
}
