# Serving from multiple regions
Follow the steps in [External load balanced service](external-load-balanced-service.md)

## VPC Connector Configuration
We must configure connectivity in a second region, us-east1.

### Subnet Creation
Create a subnet for the access connector
```bash
gcloud compute networks subnets create us-east1-vpc-connector-${SERVICE_PROJECT_ID} \
  --network $VPC_NETWORK \
  --range 10.50.100.16/28 \
  --region us-east1 \
  --project $HOST_PROJECT_ID
```

Add the Service Project's Google Service Accounts Compute Network User access to the subnet
```
serviceAccount:service-${SERVICE_PROJECT_NUMBER}@gcp-sa-vpcaccess.iam.gserviceaccount.com
serviceAccount:${SERVICE_PROJECT_NUMBER}@cloudservices.gserviceaccount.com
```

### Create the VPC Access Connector
```bash
gcloud beta compute networks vpc-access connectors create us-east1-connector \
  --region us-east1 \
  --subnet us-east1-vpc-connector-${SERVICE_PROJECT_ID} \
  --subnet-project ${HOST_PROJECT_ID} \
  --min-instances 2 \
  --max-instances 3 \
  --machine-type f1-micro \
  --project ${SERVICE_PROJECT_ID}
```

### Deploy the Cloud Run service
Deploy the receiving service in a second region
```bash
export SERVICE_NAME=receiving

gcloud beta run deploy ${SERVICE_NAME} \
  --source images/receiving \
  --platform managed \
  --region us-east1 \
  --ingress internal-and-cloud-load-balancing \
  --vpc-egress all-traffic \
  --vpc-connector us-east1-connector \
  --service-account cloud-run-demo-account@${SERVICE_PROJECT_ID}.iam.gserviceaccount.com \
  --no-allow-unauthenticated \
  --set-env-vars REGION=us-east1 \
  --project ${SERVICE_PROJECT_ID}
```

## Load Balancer Configuration
Create the serverless NEG
```bash
gcloud compute network-endpoint-groups create receiving-neg-useast1 \
  --region=us-east1 \
  --network-endpoint-type=serverless  \
  --cloud-run-service=receiving \
  --project ${SERVICE_PROJECT_ID}
```

Add the serverless NEG to the backend services
```bash
gcloud compute backend-services add-backend receiving-backend \
  --global \
  --network-endpoint-group=receiving-neg-useast1 \
  --network-endpoint-group-region=us-east1 \
  --project ${SERVICE_PROJECT_ID}
```

## Testing
Test the service directly
```bash
export RECEIVING_SERVICE_URL="$(gcloud run services describe receiving --format='value(status.url)' --platform=managed --region us-east1 --project ${SERVICE_PROJECT_ID})"

curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" ${RECEIVING_SERVICE_URL}
```

Test the load balancer
```bash
curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" https://${RECEIVING_LB_IP}.nip.io
```

If you'd like to see the load balancer return results from the secondary region, create a GCE instance in that same region and test from that instance.