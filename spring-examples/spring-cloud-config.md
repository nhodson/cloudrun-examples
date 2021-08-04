# Spring Cloud Config on Cloud Run

We will deploy two instances on Cloud Run
- spring-cloud-server: A vanilla Config Server to be deployed on Cloud Run
- spring-cloud-client: An example of necessary code changes for clients to be able to communicate with a Config Server instance secured by Cloud Run

## 1. Deploying the Server

We will deploy a standalone [Spring Cloud Config Server](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/#_spring_cloud_config_server) that provides access to a remote git repository. 
- The Config Server instance uses the `@EnableConfigServer` annotation. 
- The `spring.cloud.config.server.git.default-label` property was set. Spring Config defaults to `master` while GitHub has changed default branches to `main` from `master`.
- We will set an environment variable at deployment time to specify a [Git backend](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/#_git_backend) as the Environment Repository. In this example we will use a public GitHub repo. The official docs explain how to setup authentication for multiple backends, including [Cloud Source Repositories](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/#_authentication_with_google_cloud_source) and using [Git SSH](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/#_git_ssh_configuration_using_properties).

### 1.1 Create Config Server Service Account

Google recommends that you give each of your services a dedicated identity by assigning it a user-managed service account instead of using a default service account. User-managed service accounts allow you to control access by granting a minimal set of permissions using Identity and Access Management. Here we are creating a dedicated service account for Config Server. In a future step we will grant a client service account the `run.invoker` role so that it may call the Config Server service.

Replace with the desired value for `PROJECT_ID`

```bash
export PROJECT_ID=

gcloud iam service-accounts create config-server-sa \
  --display-name="Config Server service account" \
  --project=${PROJECT_ID}
```

### 1.2 Config Server Deployment

Deploy the `config-server` service.
```bash
export CONFIG_GIT_REPO=https://github.com/spring-cloud-samples/config-repo

gcloud beta run deploy config-server \
  --source=images/spring-config-server \
  --no-allow-unauthenticated \
  --service-account=config-server-sa@${PROJECT_ID}.iam.gserviceaccount.com \
  --memory=1024Mi \
  --set-env-vars=spring_cloud_config_server_git_uri=${CONFIG_GIT_REPO} \
  --platform=managed \
  --region=us-central1 \
  --project=${PROJECT_ID}
```

## 2. Deploying the Client

The client app is another Spring Boot application that fetches its configuration from the Config Server
- Spring Config Clients fetch the configuration by passing the application name via `spring.application.name` property in the `application.yml` file. Make sure you change the name of the app to match whatever configuration file you have at your source repo.
- We will set an environment variable at deployment time to specify the Config Server address in `spring.cloud.config.uri`
- We will set an environment variable at deployment time for the `spring.profiles.active` property

### 2.1 Cloud Run Auth Modifications

#### 2.1.1 ClientHttpRequestInterceptor

The `CloudRunClientHttpRequestInterceptor` was implemented to support [Cloud Run service-to-service authentication](https://cloud.google.com/run/docs/authenticating/service-to-service#java).

```java
public class CloudRunClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
      URL requestUrl = request.getURI().toURL();
  
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      IdTokenCredentials tokenCredentials = IdTokenCredentials.newBuilder()
          .setIdTokenProvider((IdTokenProvider) credentials).setTargetAudience(requestUrl.getProtocol() + "://" + requestUrl.getHost()).build();
  
      String token = tokenCredentials.refreshAccessToken().getTokenValue();
  
      request.getHeaders().setBearerAuth(token);
      return execution.execute(request, body);
    }
}
```

#### 2.1.2 Custom RestTemplate
A custom `RestTemplate` is required to pass our Cloud Run authentication token to the server. See [Providing a Custom RestTemplate](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/#custom-rest-template)

```java
@Configuration
public class CustomConfigServerBootstrapConfiguration {

    @Bean
    public ConfigServicePropertySourceLocator configServicePropertySourceLocator(ConfigClientProperties configClientProperties) throws IOException {
        ConfigServicePropertySourceLocator configServicePropertySourceLocator =  new ConfigServicePropertySourceLocator(configClientProperties);
        configServicePropertySourceLocator.setRestTemplate(customRestTemplate());
        return configServicePropertySourceLocator;
    }

    private RestTemplate customRestTemplate() {
        return new RestTemplateBuilder()
                .interceptors(new CloudRunClientHttpRequestInterceptor())
                .build();
    }
}
```

#### 2.1.3 Bootstrap Configuration
Due to the fact that the Config Server Client must be created during the bootstrap context of Spring Boot, one must also provide a `spring.factories` file in `resources/META-INF`
```properties
org.springframework.cloud.bootstrap.BootstrapConfiguration=com.example.configuration.CustomConfigServerBootstrapConfiguration
```

### 2.2 Create and Configure Client Service Account

Here we are creating a dedicated service account for the Config Server Client.

```bash
gcloud iam service-accounts create config-client-sa \
  --display-name="Config Client service account" \
  --project=${PROJECT_ID}
```

We must configure the receiving service, `config-server`, to accept requests from the `config-client-sa` identity.

```bash
gcloud run services add-iam-policy-binding config-server \
  --member=serviceAccount:config-client-sa@${PROJECT_ID}.iam.gserviceaccount.com \
  --region=us-central1 \
  --platform=managed \
  --role='roles/run.invoker' \
  --project=${PROJECT_ID}
```

### 2.3 Config Client Deployment

Deploy the `config-client` service
```bash
export SERVER_SERVICE_URL="$(gcloud run services describe config-server --format='value(status.url)' --platform=managed --region us-central1 --project ${PROJECT_ID})"

gcloud beta run deploy config-client \
  --source=images/spring-config-client \
  --no-allow-unauthenticated \
  --service-account=config-client-sa@${PROJECT_ID}.iam.gserviceaccount.com \
  --memory=1024Mi \
  --set-env-vars=spring_cloud_config_uri=${SERVER_SERVICE_URL} \
  --set-env-vars=spring_profiles_active=dev \
  --platform=managed \
  --region=us-central1 \
  --project=${PROJECT_ID}
```

### 2.4 Testing

To test the app, invoke the `/actuator/env` endpoint of the client application and look for environment variables that were imported via config server.

```bash
export CLIENT_SERVICE_URL="$(gcloud run services describe config-client --format='value(status.url)' --platform=managed --region us-central1 --project ${PROJECT_ID})"

curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" ${CLIENT_SERVICE_URL}/actuator/env | python -m json.tool
```

## Next Steps
- It may be possible to create a jar with the interceptor code and the factories file for reuse across all the apps as a dependency. Apps would still need the `spring.cloud.config.uri` environment variable.
- For a private GitHub repo, you can [mirror a GitHub repository to Cloud Source Repositories](https://cloud.google.com/source-repositories/docs/mirroring-a-github-repository) and grant the `config-server-sa` the `source.reader` role on the repository.
- To reduce cold start latency you may want to consider setting a [minimum number of instances](https://cloud.google.com/run/docs/configuring/min-instances#command-line) for the Config Server