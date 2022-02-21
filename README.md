
# ReviewService

Review service is a service for getting and putting reviews a product page in the frontend.
Reviews are used for customers to leave feedback about a product. Fronted uses grpc calls to make calls to the review service, which puts and gets reviews stored in a MongoDB. For our purposes we also deploy a mongodb service with skuffold. 

## Screenshots

| Review Form                                                                                                       | Product Reviews                                                                                                     |
| ----------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| [ ![Screenshot of review form](https://user-images.githubusercontent.com/50115776/154873254-8beda6a5-10e2-4df6-8e15-4493ce794642.png)](https://user-images.githubusercontent.com/50115776/154873254-8beda6a5-10e2-4df6-8e15-4493ce794642.png) | [![Screenshot of reviews](https://user-images.githubusercontent.com/50115776/154873327-ad7ac763-6d0e-44a8-a6f7-7ecff58cf98d.png)](https://user-images.githubusercontent.com/50115776/154873327-ad7ac763-6d0e-44a8-a6f7-7ecff58cf98d.png) |






## Deployment

### Full Deployment with GKE on Google Cloud Platform
To deploy the full Microservices demo follow these steps:

1. **[Create a Google Cloud Platform project](https://cloud.google.com/resource-manager/docs/creating-managing-projects#creating_a_project)** or use an existing project. Set the `PROJECT_ID` environment variable and ensure the Google Kubernetes Engine and Cloud Operations APIs are enabled.

```
PROJECT_ID="<your-project-id>"
gcloud services enable container.googleapis.com --project ${PROJECT_ID}
gcloud services enable monitoring.googleapis.com \
    cloudtrace.googleapis.com \
    clouddebugger.googleapis.com \
    cloudprofiler.googleapis.com \
    --project ${PROJECT_ID}
```


2. **Clone our fork of the Microservices Demo repository.**

```
git clone https://github.com/vDawgg/MicroservicesDemo.git
cd MicroservicesDemo
```

3. **Create a GKE cluster.**

```
ZONE=europe-west3-a
gcloud container clusters create onlineboutique \
    --project=${PROJECT_ID} --zone=${ZONE} \
    --machine-type=e2-standard-2 --num-nodes=4
```
4. **Deploy the app (with our own review microservice) to the cluster using skaffold.**
```
skaffold run -f=skaffold.yaml --default-repo=gcr.io/$PROJECT_ID
```


5. **Wait for the Pods to be ready.**

```
kubectl get pods
```
After a few minutes, you should see:

```
NAME                                     READY   STATUS    RESTARTS   AGE
adservice-56c4cc7988-rgrls               1/1     Running   0          45s
cartservice-658c7bd57c-tmjlb             1/1     Running   0          46s
checkoutservice-79f7d7d5c6-zzphz         1/1     Running   0          46s
currencyservice-6f6469f9b4-f55tv         1/1     Running   0          46s
emailservice-5b889bf9d5-g9m8w            1/1     Running   0          46s
frontend-78ffb884d-vg87p                 1/1     Running   0          45s
loadgenerator-6db85656c6-tdljc           1/1     Running   0          45s
mongodb-deployment-685f9f74f4-dgl5j      1/1     Running   0          44s
paymentservice-5dd7b44b9-9llhg           1/1     Running   0          45s
productcatalogservice-84f69bc74d-865fn   1/1     Running   0          45s
recommendationservice-76d449cb7-zbddz    1/1     Running   0          45s
redis-cart-5484848d78-cb8sl              1/1     Running   0          45s
reviewservice-6669b5b696-hpkb4           1/1     Running   0          43s
shippingservice-85c79db888-54clp         1/1     Running   0          45s
```
7. **Access the web frontend in a browser** using the frontend's `EXTERNAL_IP`.

```
kubectl get service frontend-external | awk '{print $4}'
```

*Example output - do not copy*

```
EXTERNAL-IP
<your-ip>
```

**Note**- you may see `<pending>` while GCP provisions the load balancer. If this happens, wait a few minutes and re-run the command.


8. [Optional] **Clean up**:

```
gcloud container clusters delete onlineboutique \
    --project=${PROJECT_ID} --zone=${ZONE}
```

## Deploy the microservice locally and standalone on Minicube cluster


To deploy the service locally on a minikube cluster follow the steps:
**Requirements**: have minikube and skaffold installed.

1. **Clone our Review Service repository.**

```
git clone https://github.com/vDawgg/ReviewService.git
cd ReviewService
```
2.  **Start minikube**

```
minikube start
```

3.  **Run skaffold deployment**

```
skaffold run
```
4.  **Wait to deploy and check if running**

After a few minutes run the command
```
kubectl get pods
```
And you should see the service and db deployed like this:
```
NAME                                  READY   STATUS    RESTARTS   AGE
mongodb-deployment-78f94db8d9-ll94j   1/1     Running   0          94s
reviewservice-5f5c59f664-l29ds        1/1     Running   0          94s
```


## Build the service
If you want to build the service use one of the following:

### Build the service with docker
From the root directory run 

```
docker build ./
```
**Please Note:** If you want to run the built service just in a docker container, you should supply your own MongoDB. Put your credentials and MongoDB deployment IP in the docker environment variables. See further down Notes MongoDB.
### Build the project with gradle
From the root directory run 
```
./gradlew build
```

## Tests
If you want to run the test in the hipstershop package things get a little complicated:

- Import this project into IntelliJ and set IntelliJ as your build runner

- Install and run a local instance of mongodb in the default settings (no authentication and using the standard port 27017)

- Run the tests



## Notes

### MongoDB
You could provide your own MongoDB my setting the Environment variables: MONGODB_ADDR, MONGO_INITDB_ROOT_USERNAME and MONGO_INITDB_ROOT_PASSWORD accordingly.


Kubernetes deplyoment: If you wish to change MongoDB credentials, you can change them in ```kubernetes-manifests/mongo-secret.yaml```. Credentials should be base64 encoded. If planning to deploy to production, deploy your kubernetes secrets directly to the cluster, so to not expose them in your repo.

### skaffold
We deploy the kubernetes manifests with skaffold

