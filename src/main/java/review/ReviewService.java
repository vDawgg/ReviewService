package review;

import com.mongodb.MongoException;
import com.mongodb.client.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.stub.StreamObserver;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.Document;
import org.bson.conversions.Bson;
import review.service.ReviewServiceGrpc;
import review.service.ReviewServiceProto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

//TODO: Start testing as much as possible
public class ReviewService {

    //TODO: decide on tracing with jaeger (java library deprecated) or opentelemetry (version suggested by jaeger)
    //TODO: Improve logging (for java/gradle and mongodb!)
    private static final Logger logger = Logger.getLogger("ReviewServiceLogger");

    private Server server;
    private HealthStatusManager healthMgr;

    //Objects for mongodb
    private static final MongoClient client = MongoClients.create();
    private static final MongoDatabase db = client.getDatabase("reviewDB");

    //TODO: Create mock-client for interaction with the server

    /**
     * Method for starting the grpc server (Mostly taken from implementation of Adservice)
     * @throws IOException if JVM has shut down
     */
    private void start() throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "6666")); //does this part need to be that complicated?
        healthMgr = new HealthStatusManager();

        server  =
                ServerBuilder.forPort(port)
                        .addService(new ReviewServiceImpl())
                        .addService(healthMgr.getHealthService())
                        .build()
                        .start();
        logger.info("Review Service started, listening on "+port);
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    System.err.println("*** shutting down grpc reviews server since JVM is shutting down");
                                    ReviewService.this.stop();
                                    System.err.println("*** server shut down");
                                }));
        healthMgr.setStatus("Review", HealthCheckResponse.ServingStatus.SERVING);

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
    private void stop() {
        if(server!=null) {
            healthMgr.clearStatus("Review"); //This might have to applied to the ReviewService explicitly
            server.shutdown();
        }
    }

    private static class ReviewServiceImpl extends ReviewServiceGrpc.ReviewServiceImplBase {

        @Override
        public void getReviews(ReviewServiceProto.ProductID request, StreamObserver<ReviewServiceProto.Reviews> responseObserver) {
            MongoCollection<Document> reviews = db.getCollection("reviews");
            FindIterable<Document> iterable = reviews.find(new Document("product_id", request.getProductId()));
            ReviewServiceProto.Reviews.Builder reviewList = ReviewServiceProto.Reviews.newBuilder();
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
        private ReviewServiceProto.Review documentToRPC(Document document) {
            ReviewServiceProto.Review.Builder builder = ReviewServiceProto.Review.newBuilder();
            builder.setId((String) document.get("id"));
            builder.setName((String) document.get("name"));
            builder.setStar((int) document.get("star"));
            builder.setText((String) document.get("text"));
            builder.setProductId((String) document.get("product_id"));
            return builder.build();
        }

        @Override
        public void putReviews(ReviewServiceProto.Review request, StreamObserver<ReviewServiceProto.Empty> responseObserver) {
            responseObserver.onNext(addToDB(request));
            responseObserver.onCompleted();
        }

        //TODO: Check if important fields are in the right format (product ID, ID, etc. -> Maybe send a boolean response message back rather than an empty message
        /**
         * Adds a review sent over grpc to the "reviews" mongodb
         * @param request the review
         * @return empty grpc message
         */
        private ReviewServiceProto.Empty addToDB(ReviewServiceProto.Review request) {
            logger.info("*** Adding review to db!");
            MongoCollection<Document> reviews = db.getCollection("reviews");
            Document review = new Document("id", request.getId()).
                    append("name", request.getName()).
                    append("star", request.getStar()).
                    append("text", request.getText()).
                    append("product_id", request.getProductId());
            reviews.insertOne(review);
            return ReviewServiceProto.Empty.newBuilder().build();
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
        if(!db.listCollectionNames().into(new ArrayList<>()).contains("reviews")) {
            db.createCollection("reviews");
        }

        final ReviewService service = new ReviewService();
        try {
            service.start();
            service.blockUntilShutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
