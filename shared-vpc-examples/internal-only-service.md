# Internal only Cloud Run service deployment
Follow the steps in [Serverless VPC Connector setup](serverless-connector.md)

If deploying to a Shared VPC service project and you want Cloud Run services to be able to ONLY communicate with internal ingress, the projects must be a member of a VPC-SC perimeter. [Restricting ingress](https://cloud.google.com/run/docs/securing/ingress)

> Resources in Shared VPC networks can only call internal services if the Shared VPC resources and the internal service are in the same VPC SC perimeter and the Cloud Run API is enabled as a VPC accessible service

## Configure Org Policies for Cloud Run

1. `constraints/run.allowedIngress` set to `internal` or `internal-and-cloud-load-balancing` or `all`
1. `constraints/run.allowedVPCEgress` set to `all-traffic` or `private-ranges-only`

## VPC SC Protection
In your service perimeter, protect the Host and Service project and all available APIs. Requests to the *.run.app URLs must come from within the perimeter.

### Cloud Run
Ensure DNS records exist for `run.app` to use Private Google Access
```
gcloud dns managed-zones create run-app \
--visibility=private \
--networks=https://www.googleapis.com/compute/v1/projects/${HOST_PROJECT_ID}/global/networks/${VPC_NETWORK} \
--description=none \
--dns-name=run.app

gcloud dns record-sets transaction start --zone=run-app

gcloud dns record-sets transaction add --name=*.run.app. \
--type=A 199.36.153.4 199.36.153.5 199.36.153.6 199.36.153.7 \
--zone=run-app \
--ttl=300

gcloud dns record-sets transaction execute --zone=run-app
```

### Cloud Build
Add the Cloud Build service account to an access level. Create a CONDITIONS.yaml file with the following
```
- members:
    - serviceAccount:${SERVICE_PROJECT_NUMBER}@cloudbuild.gserviceaccount.com
```

Create the access level
```
gcloud access-context-manager levels create Trusted_Service_Accounts \
   --title ‘Trusted Service Accounts’ \
   --basic-level-spec CONDITIONS.yaml \
   --combine-function=OR \
   --policy=${ACCESS_POLICY_NUMBER}

```

Add the access level to your service perimeter

## Create a Custom Service Account for Cloud Run services to deploy with
```
gcloud iam service-accounts create cloud-run-demo-account \
    --display-name="Cloud Run Demo Account" \
    --project ${SERVICE_PROJECT_ID}
```

## Deploy the Cloud Run service
Deploy the receiving service
```
export SERVICE_NAME=receiving

gcloud beta run deploy ${SERVICE_NAME} \
  --source images/receiving \
  --platform managed \
  --region us-central1 \
  --ingress internal \
  --vpc-egress all-traffic \
  --vpc-connector us-central1-${SERVICE_PROJECT_ID} \
  --service-account cloud-run-demo-account@${SERVICE_PROJECT_ID}.iam.gserviceaccount.com \
  --no-allow-unauthenticated \
  --project ${SERVICE_PROJECT_ID}
```

## Test the service
Test the receiving service. This should succeed.
```
export RECEIVING_SERVICE_URL="$(gcloud run services describe receiving --format='value(status.url)' --platform=managed --region us-central1 --project ${SERVICE_PROJECT_ID})"

curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" ${RECEIVING_SERVICE_URL}
```