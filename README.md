
## 🚀 Infrastructure Provisioning

[![Provision Azure Infrastructure](https://github.com/MichRacz00/spring-memo/actions/workflows/create-azure-resources.yml/badge.svg)](https://github.com/MichRacz00/spring-memo/actions/workflows/create-azure-resources.yml)

This project uses a robust **GitHub Actions** workflow (`create-azure-resources.yml`) to manage the entire Azure infrastructure lifecycle. It is designed to be **idempotent** and **self-healing**, handling complex dependencies like Identity, Key Vaults, and Role Assignments automatically. Deploys a complete project environment from the ground up, automatically handling App Registration in the default Entra ID tenant within a fresh Azure account.

### Workflow Architecture

The pipeline is split into three logical jobs to ensure dependencies are resolved before deployment attempts begin.

#### 1. Prerequisites Job
This job prepares the Azure environment state to prevent common deployment failures:
*   **Docker Image Resolution:** Calculates the correct `ghcr.io` image tag based on the repository.
*   **Key Vault Self-Healing:** Checks for "Soft-Deleted" Key Vaults (which block deployment) and automatically **purges** them to allow fresh creation.
*   **Identity Management:**
    *   Checks if the Azure App Registration exists; creates it if missing.
    *   **Secret Rotation:** Checks Key Vault for an existing Client Secret. If missing, it rotates the credentials in Entra ID (Azure AD) and passes the new secret to the deployment job.

#### 2. Deploy Infrastructure Job
This job consumes the outputs from the prerequisites and executes **Bicep** templates at the Subscription scope:
*   **Resource Group:** Creates the target resource group.
*   **Base Infra:** Deploys Key Vault and App Configuration.
*   **Database:** Deploys Cosmos DB and saves the Master Key directly to Key Vault.
*   **Storage:** Deploys Blob Storage.
*   **Application:** Deploys the Spring Boot Container App with **System-Assigned Managed Identity**.
*   **RBAC:** Assigns granular permissions (Data Reader, Secrets User) to the Managed Identity.
*   **Post-Config:** Dynamically updates the App Registration with the new Container App URL (`redirect_uri`) and configures it for Personal Microsoft Accounts.

### Key Features

*   **Idempotency:** Safe to run multiple times. It updates existing resources or creates missing ones without duplication.
*   **Secret Zero:** No application secrets are stored in GitHub. Secrets are generated during the pipeline and stored directly in **Azure Key Vault**.
*   **Secure by Default:** The application uses **Managed Identity** for all backend connections (Config, Storage, Key Vault). No connection strings are passed to the container environment.

### How to Run

1.  **Prerequisites:**
    *   **Configure OIDC Federation:** Connect GitHub Actions to Azure without client secrets.
        *   Follow the [Azure OIDC Setup Guide](https://learn.microsoft.com/en-us/azure/developer/github/connect-from-azure?tabs=azure-portal%2Clinux#create-an-azure-active-directory-application-and-service-principal).
    *   **Add Repository Secrets:**
        *   `AZURE_CLIENT_ID` (The Application ID of your GitHub Service Principal)
        *   `AZURE_TENANT_ID`
        *   `AZURE_SUBSCRIPTION_ID`
    *   **Required Permissions (Crucial):**
        The Service Principal used by GitHub Actions must have the following permissions to run the self-healing scripts:
        1.  **Subscription:** `Contributor` (To create Resource Groups and resources).
        2.  **Subscription** or **Key Vault:** `Key Vault Contributor` (To purge soft-deleted Key Vaults).
        3.  **Entra ID (Azure AD):** `Application.ReadWrite.All` (API Permission) OR the `Cloud Application Administrator` role (To create/update App Registrations and rotate secrets).

2.  **Trigger:**
    *   Push changes to the `infra/` folder on the `main` branch.
    *   Or run manually via the **Actions** tab -> **Provision Azure Infrastructure** -> **Run workflow**.

### Bicep Structure

*   `main.bicep`: Orchestrator (Subscription Scope).
*   `modules/config.bicep`: App Configuration & Key Vault.
*   `modules/database.bicep`: Cosmos DB & Secret registration.
*   `modules/storage.bicep`: Storage Account & Blob Service.
*   `modules/app.bicep`: Container App & Environment variables.
*   `modules/permissions.bicep`: RBAC Role Assignments (using stable UUID generation).