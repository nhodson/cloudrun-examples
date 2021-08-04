# Spring Cloud Config Vault Backend

We will deploy a Vault instance on Cloud Run and demonstrate its integration as a backend for Config Server using GCP authentication.

## 1. Deploying Vault

Follow [Serverless Vault with Cloud Run](https://github.com/kelseyhightower/serverless-vault-with-cloud-run) to get an instance of Vault up and running.

## 2. Configure Vault

### 2.1 Login
Login to Vault from the CLI using the root_token provided when initializing Vault.
```
vault login
```

### 2.2 Enable Secrets Engine
Enable the Key/Value Secrets Engine and load with some data for our application to retrieve.
```
vault secrets enable -path=secret kv-v2
vault kv put secret/application foo=bar baz=bam
vault kv put secret/foo foo=myappsbar
vault kv put secret/bar bar=myappsfoo
```

### 2.3 Enable GCP Auth
Enable GCP Auth so we can use the Config Server's Cloud Run service account identity to securely authenticate with Vault.
```
vault auth enable gcp
```

### 2.4 Configure authentication and authorization for the Config Server service account
Create a policy for the Config Server service account to access secrets.
```
cat <<EOF > foo-policy.hcl
path "secret/data/*" {
  capabilities = ["read","list"]
}
EOF
vault policy write foo foo-policy.hcl
```

Create a role for the Config Server service account and attach our policy.
```
vault write auth/gcp/role/cloud-run-config-server \
    type="iam" \
    policies="foo" \
    bound_service_accounts="config-server-sa@$PROJECT_ID.iam.gserviceaccount.com"
```

### 2.5 Required GCP Permissions 
Provide the Vault Server service account access to verify authenticating service accounts. See [Vault Server Permissions](https://www.vaultproject.io/docs/auth/gcp#vault-server-permissions). We're creating a custom role with minimal permissions for the Vault Server.
```
gcloud iam roles create serviceAccountVerifier --project=$PROJECT_ID \
  --title="Service Account Verifier" --description="For Vault server to verify service accounts" \
  --permissions=iam.serviceAccounts.get,iam.serviceAccountKeys.get --stage=GA

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:vault-server@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="projects/$PROJECT_ID/roles/serviceAccountVerifier"
```

Provide the service account authenticating against Vault required permissions. Make sure this role is only applied so your service account can impersonate itself. If this role is applied GCP project-wide, this will allow the service account to impersonate any service account in the GCP project where it resides. See [Permissions For Authenticating Against Vault](https://www.vaultproject.io/docs/auth/gcp#permissions-for-authenticating-against-vault)
```
gcloud iam service-accounts add-iam-policy-binding \
    config-server-sa@$PROJECT_ID.iam.gserviceaccount.com \
    --member="serviceAccount:config-server-sa@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/iam.serviceAccountTokenCreator"
```

## 3 Config Server Redeployment
### 3.1 Vault Authentication Dependencies
Dependencies have already been included in `spring-config-server` to allow usage of authentication methods besides Tokens. See [Vault backend](https://cloud.spring.io/spring-cloud-config/reference/html/#vault-backend) and [GCP-IAM authentication](https://cloud.spring.io/spring-cloud-vault/reference/html/#vault.config.authentication.gcpiam) for the requirements.
```
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.vault</groupId>
      <artifactId>spring-vault-dependencies</artifactId>
      <version>2.2.3.RELEASE</version>
      <scope>import</scope>
      <type>pom</type>
    </dependency>
  </dependencies>
</dependencyManagement>
```

```
<dependency>
    <groupId>org.springframework.vault</groupId>
    <artifactId>spring-vault-core</artifactId>
</dependency>
<dependency>
    <groupId>com.google.apis</groupId>
    <artifactId>google-api-services-iam</artifactId>
    <version>v1-rev20210722-1.32.1</version>
</dependency>
<dependency>
    <groupId>com.google.http-client</groupId>
    <artifactId>google-http-client-jackson2</artifactId>
    <version>1.39.2</version>
</dependency>
<dependency>
    <groupId>com.google.auth</groupId>
    <artifactId>google-auth-library-oauth2-http</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3.2 Redeploy Config Server
Redeploy `config-server` with the `vault` profile and `spring.cloud.config.server.vault.*` properties. `config-server` will use the GCP IAM auth method to retrieve secrets from Vault backend.
```
export VAULT_ADDR=$(gcloud run services describe vault-server \
  --platform managed \
  --region ${REGION} \
  --format 'value(status.url)')

gcloud beta run deploy config-server \
  --source=images/spring-config-server \
  --no-allow-unauthenticated \
  --service-account=config-server-sa@${PROJECT_ID}.iam.gserviceaccount.com \
  --memory=1024Mi \
  --set-env-vars=spring_cloud_config_server_git_uri=${CONFIG_GIT_REPO} \
  --set-env-vars=spring_profiles_active=vault \
  --set-env-vars=spring_cloud_config_server_vault_scheme=https \
  --set-env-vars=spring_cloud_config_server_vault_port=443 \
  --set-env-vars=spring_cloud_config_server_vault_host=${VAULT_ADDR#https://} \
  --set-env-vars=spring_cloud_config_server_vault_kvVersion=2 \
  --set-env-vars=spring_cloud_config_server_vault_authentication=GCP_IAM \
  --set-env-vars=spring_cloud_config_server_vault_gcp-iam_role=cloud-run-config-server \
  --set-env-vars=spring_cloud_config_server_vault_gcp-iam_service-account-id=config-server-sa@${PROJECT_ID}.iam.gserviceaccount.com \
  --set-env-vars=spring_cloud_config_server_vault_gcp-iam_project-id=$PROJECT_ID \
  --platform=managed \
  --region=us-central1 \
  --project=${PROJECT_ID}
```

### 3.3 Testing
To test the app, invoke the `/actuator/env` endpoint of the client application and look for properties that were provided by the Vault backend.
```bash
export CLIENT_SERVICE_URL="$(gcloud run services describe config-client --format='value(status.url)' --platform=managed --region us-central1 --project ${PROJECT_ID})"

curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" ${CLIENT_SERVICE_URL}/actuator/env | python -m json.tool
```

```
{
    "name": "bootstrapProperties-vault:foo",
    "properties": {
        "foo": {
            "value": "myappsbar"
        }
    }
},
{
    "name": "bootstrapProperties-vault:application",
    "properties": {
        "baz": {
            "value": "bam"
        },
        "foo": {
            "value": "bar"
        }
    }
},
```

### 3.4 Composite Environment Repositories
If you would like to pull configuration data from both a Git repository and Vault server you can activate the `git` profile in addition to `vault` as part of the [Composite Environment Repositories](https://cloud.spring.io/spring-cloud-config/multi/multi__spring_cloud_config_server.html#composite-environment-repositories) functionality.
```
gcloud beta run deploy config-server \
  --source=images/spring-config-server \
  --no-allow-unauthenticated \
  --service-account=config-server-sa@${PROJECT_ID}.iam.gserviceaccount.com \
  --memory=1024Mi \
  --set-env-vars=spring_cloud_config_server_git_uri=${CONFIG_GIT_REPO} \
  --set-env-vars=^##^spring_profiles_active=vault,git \
  --set-env-vars=spring_cloud_config_server_vault_scheme=https \
  --set-env-vars=spring_cloud_config_server_vault_port=443 \
  --set-env-vars=spring_cloud_config_server_vault_host=${VAULT_ADDR#https://} \
  --set-env-vars=spring_cloud_config_server_vault_kvVersion=2 \
  --set-env-vars=spring_cloud_config_server_vault_authentication=GCP_IAM \
  --set-env-vars=spring_cloud_config_server_vault_gcp-iam_role=cloud-run-config-server \
  --set-env-vars=spring_cloud_config_server_vault_gcp-iam_service-account-id=config-server-sa@${PROJECT_ID}.iam.gserviceaccount.com \
  --set-env-vars=spring_cloud_config_server_vault_gcp-iam_project-id=$PROJECT_ID \
  --platform=managed \
  --region=us-central1 \
  --project=${PROJECT_ID}
```

Now when invoking `actuator/env` you should see both Git and Vault backend properties.
```
...
{
    "name": "bootstrapProperties-vault:application",
    "properties": {
        "baz": {
            "value": "bam"
        },
        "foo": {
            "value": "bar"
        }
    }
},
{
    "name": "bootstrapProperties-https://github.com/spring-cloud-samples/config-repo/foo-dev.yml",
    "properties": {
        "bar": {
            "value": "spam"
        },
        "foo": {
            "value": "from foo development"
        },
        "democonfigclient.message": {
            "value": "hello from dev profile"
        }
    }
},
...
```