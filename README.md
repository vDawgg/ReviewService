# ReviewService

Review service is a service for getting and putting reviews for the product page in the frontend.

To deploy the service on GCP with the rest of the services follow the instructions on: https://github.com/vDawgg/MicroservicesDemo


To deploy the service locally on a minikube follow the steps:
requirements: have minikube and skaffold installed.
1. go to the root directory of this repo
2. start minikube with: minikube start
3. run skaffold deployment with: skaffold run


If you want to build the service use one of the following:

build an image with docker from the root directory: docker build ./

or

build the project with gradle: ./gradlew build


If you want to run the test in the hipstershop package things get a little more complicated:

- Import this project into IntelliJ and set IntelliJ as your build runner

- Install and run a local instance of mongodb in the default settings (no authentication and using the standard port 27017)

- Run the tests

