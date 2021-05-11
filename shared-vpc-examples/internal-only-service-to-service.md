# Internal only Cloud Run service to service communication
Follow the steps in [Internal only service](internal-only-service.md)

## Deploy the Cloud Run service

Deploy the calling service which will call the receiving service deployed in the previous step
```
export RECEIVING_SERVICE_URL="$(gcloud run services describe receiving --format='value(status.url)' --platform=managed --region us-central1 --project ${SERVICE_PROJECT_ID})"

export SERVICE_NAME=calling
gcloud beta run deploy ${SERVICE_NAME} \
  --source images/calling \
  --platform managed \
  --region us-central1 \
  --ingress internal \
  --vpc-egress all-traffic \
  --vpc-connector us-central1-${SERVICE_PROJECT_ID} \
  --service-account cloud-run-demo-account@${SERVICE_PROJECT_ID}.iam.gserviceaccount.com \
  --no-allow-unauthenticated \
  --set-env-vars RECEIVING_URL=${RECEIVING_SERVICE_URL} \
  --project ${SERVICE_PROJECT_ID}
```

## Test the service
Test the calling service. This should fail.
```
export CALLING_SERVICE_URL="$(gcloud run services describe calling --format='value(status.url)' --platform=managed --region us-central1 --project ${SERVICE_PROJECT_ID})"

curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" ${CALLING_SERVICE_URL}
```

Grant the service account access to invoke the calling service
```
gcloud run services add-iam-policy-binding receiving \
  --member='serviceAccount:cloud-run-demo-account@${SERVICE_PROJECT_ID}.iam.gserviceaccount.com' \
  --role='roles/run.invoker'
```

Test the calling service again. This time it should pass.
```
export CALLING_SERVICE_URL="$(gcloud run services describe calling --format='value(status.url)' --platform=managed --region us-central1 --project ${SERVICE_PROJECT_ID})"

curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" ${CALLING_SERVICE_URL}
```