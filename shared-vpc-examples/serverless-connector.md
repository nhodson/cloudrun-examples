# Serverless VPC Connector Setup

## Firewalls
One time setup for all vpc-connectors
```
gcloud compute firewall-rules create serverless-to-vpc-connector \
--allow tcp:667,udp:665-666,icmp \
--source-ranges 107.178.230.64/26,35.199.224.0/19 \
--direction=INGRESS \
--target-tags vpc-connector \
--network=${VPC_NETWORK}

gcloud compute firewall-rules create vpc-connector-to-serverless \
--allow tcp:667,udp:665-666,icmp \
--destination-ranges 107.178.230.64/26,35.199.224.0/19 \
--direction=EGRESS \
--target-tags vpc-connector \
--network=${VPC_NETWORK}

gcloud compute firewall-rules create vpc-connector-health-checks \
--allow tcp:667 \
--source-ranges 130.211.0.0/22,35.191.0.0/16,108.170.220.0/23 \
--direction=INGRESS \
--target-tags vpc-connector \
--network=${VPC_NETWORK}
```

Broad firewall to allow all connectors access to every resource in the customer network. Can be narrowed down further.
```
gcloud compute firewall-rules create \
vpc-connector-egress \
--allow tcp,udp,icmp \
--direction=INGRESS \
--source-tags vpc-connector \
--network=${VPC_NETWORK}
```

## Enable VPC Access API in the Service Project
```
gcloud services enable vpcaccess.googleapis.com --project ${SERVICE_PROJECT_ID}
```

## Subnet Creation
Create a subnet for the access connector
```
gcloud compute networks subnets create us-central1-vpc-connector-${SERVICE_PROJECT_ID} \
--network $VPC_NETWORK \
--range 10.50.100.0/28 \
--region us-central1 \
--project $HOST_PROJECT_ID
```

Add the Service Project's Google Service Accounts Compute Network User access to the subnet
```
serviceAccount:service-${SERVICE_PROJECT_NUMBER}@gcp-sa-vpcaccess.iam.gserviceaccount.com
serviceAccount:${SERVICE_PROJECT_NUMBER}@cloudservices.gserviceaccount.com
```

## Configure Org Policies(if necessary)

1. `constraints/compute.trustedImageProjects` must allow use of `projects/serverless-vpc-access-images` in the service project
1. `constraints/compute.vmCanIpForward` must allow VMs to enable IP forwarding(Default)

## Create the VPC Access Connector
```
gcloud beta compute networks vpc-access connectors create us-central1-connector \
--region us-central1 \
--subnet us-central1-vpc-connector-${SERVICE_PROJECT_ID} \
--subnet-project ${HOST_PROJECT_ID} \
--min-instances 2 \
--max-instances 3 \
--machine-type f1-micro \
--project ${SERVICE_PROJECT_ID}
```