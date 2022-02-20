package hipstershop;

import com.google.protobuf.BoolValue;
import com.google.protobuf.Descriptors;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClients;
import com.mongodb.client.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.stub.StreamObserver;
import org.apache.log4j.BasicConfigurator;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.util.ArrayList;

import static java.lang.System.getenv;

public class ReviewService {

    //TODO: decide on tracing with jaeger (java library deprecated) or opentelemetry (version suggested by jaeger)
    //TODO: Properly set up log4j (and sl4j for mongodb)
    //TODO: Add authentication do mongodb
    private static final Logger logger = LogManager.getLogger(ReviewService.class);


    private HealthStatusManager healthMgr;

    private final int port;
    private final Server server;

    //Objects for mongodb
    private static MongoClient client;
    private static MongoDatabase db;

    public ReviewService(int port) {
        this(ServerBuilder.forPort(port), port);
    }

    public ReviewService(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;
        this.server = serverBuilder
                .addService(new ReviewServiceImpl())
                .build();
    }

    public ReviewService(int port, HealthStatusManager healthMgr) {
        this.port = port;
        this.server = ServerBuilder.forPort(port)
                .addService(new ReviewServiceImpl())
                .addService(healthMgr.getHealthService())
                .build();
        this.healthMgr = healthMgr;
    }

    /**
     * Method for starting the grpc server (Mostly taken from implementation of Adservice)
     * @throws IOException if JVM has shut down
     */
     void start() throws IOException {
         BasicConfigurator.configure();
        int port = Integer.parseInt(getenv().getOrDefault("PORT", "6666")); //does this part need to be that complicated?

        String mongodb_addr = System.getenv("MONGODB_ADDR");
        String mongo_initdb_root_username = System.getenv("MONGO_INITDB_ROOT_USERNAME");
        String mongo_initdb_root_password = System.getenv("MONGO_INITDB_ROOT_PASSWORD");
        if(mongo_initdb_root_password==null | mongo_initdb_root_username==null | mongodb_addr==null) {
            logger.log(Level.ERROR, "Environment variables could not be retrieved");
            return;
        }

        //TODO: Create seperate setup for tests to still work! -> Or let tests run once deployed
        MongoCredential credential = MongoCredential.createCredential(mongo_initdb_root_username,
                "mongodb-service", //check if db name is set correctly here
                mongo_initdb_root_password.toCharArray());
        MongoClientSettings.Builder mcsb = MongoClientSettings.builder();
        MongoClientSettings mcs = mcsb
                .credential(credential)
                .applyConnectionString(new ConnectionString("mongodb://"+mongodb_addr+":27017")) //check if the address is set correctly
                        .build();

        client = MongoClients.create(mcs);
        db = client.getDatabase("reviews");

        server.start();

        logger.info("Review Service started, listening on "+port);
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    System.err.println("*** shutting down grpc reviews server since JVM is shutting down");
                                    ReviewService.this.stop();
                                    System.err.println("*** server shut down");
                                }));
        healthMgr.setStatus("", HealthCheckResponse.ServingStatus.SERVING);

        try {
            Bson command = new BsonDocument("ping", new BsonInt64(1));
            Document commandResult = db.runCommand(command);
            logger.info("Connected successfully to server.");
        } catch (MongoException me) {
            logger.info("An error occurred while attempting to run a command: " + me);
            return;
        }

        if(!db.listCollectionNames().into(new ArrayList<>()).contains("reviews")) {
            db.createCollection("reviews");
        }
    }

    /**
     * Stops the server and tells other services through the health manager
     * Copied from the AdService implementation
     */
     void stop() {
        if(server!=null) {
            healthMgr.clearStatus("Review"); //This might have to applied to the ReviewService explicitly
            server.shutdown();
        }
    }

    static class ReviewServiceImpl extends hipstershop.ReviewServiceGrpc.ReviewServiceImplBase {

        @Override
        public void getReviews(hipstershop.ReviewServiceProto.ProductID request, StreamObserver<hipstershop.ReviewServiceProto.Reviews> responseObserver) {
            MongoCollection<Document> reviews = db.getCollection("reviews");
            FindIterable<Document> iterable = reviews.find(new Document("product_id", request.getProductId()));
            hipstershop.ReviewServiceProto.Reviews.Builder reviewList = hipstershop.ReviewServiceProto.Reviews.newBuilder();
            for(Document d : iterable) {
                reviewList.addReview(documentToRPC(d));
            }
            responseObserver.onNext(reviewList.build());
            responseObserver.onCompleted();
        }

        /**
         * Translates a Document taken from mongodb to the required grpc message
         * @param document document from mongodb
         * @return built "Review" message
         */
        private hipstershop.ReviewServiceProto.Review documentToRPC(Document document) {
            hipstershop.ReviewServiceProto.Review.Builder builder = hipstershop.ReviewServiceProto.Review.newBuilder();
            builder.setName((String) document.get("name"));
            builder.setStar((int) document.get("star"));
            builder.setText((String) document.get("text"));
            builder.setProductId((String) document.get("product_id"));
            return builder.build();
        }

        @Override
        public void putReviews(hipstershop.ReviewServiceProto.Review request, StreamObserver<BoolValue> responseObserver) {
            responseObserver.onNext(addToDB(request));
            responseObserver.onCompleted();
        }

        //TODO: Check if important fields are in the right format (product ID, ID, etc. -> Maybe send a boolean response message back rather than an empty message
        /**
         * Adds a review sent over grpc to the "reviews" mongodb
         * @param request the review
         * @return empty grpc message
         */
        private BoolValue addToDB(hipstershop.ReviewServiceProto.Review request) {
            if(checkReview(request)) {
                return BoolValue.newBuilder().setValue(false).build();
            }
            logger.info("*** Adding review to db!");
            MongoCollection<Document> reviews = db.getCollection("reviews");
            Document review = new Document().
                    append("name", request.getName()).
                    append("star", request.getStar()).
                    append("text", request.getText()).
                    append("product_id", request.getProductId());
            reviews.insertOne(review);
            return BoolValue.newBuilder().setValue(true).build();
        }

        private boolean checkReview(hipstershop.ReviewServiceProto.Review request) {
            MongoCollection<Document> reviews = db.getCollection("reviews");

            //Might not make much sense to let the frontend generate the id???
            Descriptors.FieldDescriptor id = request.getDescriptorForType().findFieldByName("id");
            Descriptors.FieldDescriptor name = request.getDescriptorForType().findFieldByName("name");
            Descriptors.FieldDescriptor star = request.getDescriptorForType().findFieldByName("star");
            Descriptors.FieldDescriptor product_id = request.getDescriptorForType().findFieldByName("product_id");


            if(!request.hasField(name) | !request.hasField(star) | !request.hasField(product_id)) {
                return true;
            }
            return false;
        }
    }

    /**
     * Taken from AdService
     * @throws InterruptedException
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) {
        HealthStatusManager healthMgr = new HealthStatusManager();
        final ReviewService service = new ReviewService(6666, healthMgr);
        try {
            service.start();
            service.blockUntilShutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
