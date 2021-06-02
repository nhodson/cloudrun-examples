package com.example.configuration;

import java.io.IOException;
import java.net.URL;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class CloudRunClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
      URL requestUrl = request.getURI().toURL();
  
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      if (!(credentials instanceof IdTokenProvider)) {
        throw new IllegalArgumentException("Credentials are not an instance of IdTokenProvider.");
      }
      IdTokenCredentials tokenCredentials = IdTokenCredentials.newBuilder()
          .setIdTokenProvider((IdTokenProvider) credentials).setTargetAudience(requestUrl.getProtocol() + "://" + requestUrl.getHost()).build();
  
      request.getHeaders().setBearerAuth(tokenCredentials.refreshAccessToken().getTokenValue());
      return execution.execute(request, body);
    }
}
