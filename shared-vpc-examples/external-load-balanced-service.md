# External load balanced service
Follow the steps in [Internal only service](internal-only-service.md)

## Configure Org Policies for Cloud Run

Ensure `constraints/run.allowedIngress` includes `internal-and-cloud-load-balancing`.

## Update Service Ingress
Update your service's ingress setting
```
gcloud run services update receiving \
  --ingress=internal-and-cloud-load-balancing \
  --platform managed \
  --region us-central1 \
  --project ${SERVICE_PROJECT_ID}
```

## VPC SC Protection

Your service must still be protected by VPC-SC if part of a Shared VPC for calls to *.run.app URLs. Calls through the LB will work without VPC-SC protection.

## Load Balancer Configuration

### Reserve an external IP
```
gcloud compute addresses create receiving-ip \
  --ip-version=IPV4 \
  --global \
  --project ${SERVICE_PROJECT_ID}
```

Save the IP for certificate creation
```bash
export RECEIVING_LB_IP=$(gcloud compute addresses describe receiving-ip --format="get(address)" --global --project ${SERVICE_PROJECT_ID})
```

### Create a Google-managed SSL certificate
```
gcloud compute ssl-certificates create receiving-cert \
  --domains=${RECEIVING_LB_IP}.nip.io \
  --global \
  --project ${SERVICE_PROJECT_ID}
```

### Create the external HTTP(S) load balancer
Create the serverless NEG
```
gcloud compute network-endpoint-groups create receiving-neg-uscentral1 \
  --region=us-central1 \
  --network-endpoint-type=serverless  \
  --cloud-run-service=receiving \
  --project ${SERVICE_PROJECT_ID}
```

Create a backend service
```
gcloud compute backend-services create receiving-backend \
  --global \
  --project ${SERVICE_PROJECT_ID}
```

Add the serverless NEG to the backend services
```
gcloud compute backend-services add-backend receiving-backend \
  --global \
  --network-endpoint-group=receiving-neg-uscentral1 \
  --network-endpoint-group-region=us-central1 \
  --project ${SERVICE_PROJECT_ID}
```

Create a URL map to route incoming requests to the backend service
```
gcloud compute url-maps create receiving-urlmap \
  --default-service receiving-backend \
  --project ${SERVICE_PROJECT_ID}
```

Create a target HTTP(S) proxy
```
gcloud compute target-https-proxies create receiving-https \
    --ssl-certificates=receiving-cert \
    --url-map=receiving-urlmap \
    --project ${SERVICE_PROJECT_ID}
```

Create a global forwarding rule
```
gcloud compute forwarding-rules create receiving-lb \
  --address=receiving-ip \
  --target-https-proxy=receiving-https \
  --global \
  --ports=443 \
  --project ${SERVICE_PROJECT_ID}
```

## Testing
You may need to wait up to 30 minutes for the managed TLS certificate to be provisioned. You can check its status for `ACTIVE`
```
gcloud beta compute ssl-certificates describe receiving-cert --project ${SERVICE_PROJECT_ID}
```

Execute the curl command
```bash
curl -H "Authorization: Bearer $(gcloud auth print-identity-token)" https://${RECEIVING_LB_IP}.nip.io
```

## Next Steps
You can now configure additional services for use with your load balancer
- [Cloud Armor](https://cloud.google.com/armor/docs/security-policy-overview)
- [Cloud CDN](https://cloud.google.com/cdn/docs/setting-up-cdn-with-serverless)
- [Cloud IAP](https://cloud.google.com/iap/docs/concepts-overview)