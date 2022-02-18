package review;

import com.mongodb.MongoException;
import com.mongodb.client.*;
import io.grpc.Internal;
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

    // New
    //private static final Logger logger = LogManager.getLogger(ReviewService.class);
    //private static final Tracer tracer = Tracing.getTracer();
  

    private Server server;
    private HealthStatusManager healthMgr;

    //Objects for mongodb
    /*
    private static final MongoClient client = MongoClients.create();
    private static final MongoDatabase db = client.getDatabase("reviewDB");
    */

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
            /*
            Bson command = new BsonDocument("ping", new BsonInt64(1));
            Document commandResult = db.runCommand(command);
            */
            logger.info("Connected successfully to server.");
        } catch (MongoException me) {
            logger.info("An error occurred while attempting to run a command: " + me);
            return;
        }

        /*
        if(!db.listCollectionNames().into(new ArrayList<>()).contains("reviews")) {
            db.createCollection("reviews");
        }
        */
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
        public void getReviews(ReviewServiceProto.Product request, StreamObserver<ReviewServiceProto.Review> responseObserver) {
            /*
            MongoCollection<Document> reviews = db.getCollection("reviews");
            FindIterable<Document> iterable = reviews.find(new Document("product_id", request.getProductId()));
            for(Document d : iterable) {
                responseObserver.onNext(documentToRPC(d));
            }
            */

            logger.info("getReviews ProductId: " + request.getProductId());

            ReviewServiceProto.Review.Builder builder = ReviewServiceProto.Review.newBuilder();
            builder.setId((String)"1");
            builder.setName((String)"Name1");
            builder.setStar((int)1);
            builder.setText((String)"Text1");
            builder.setProductId((String)"Product1");
            ReviewServiceProto.Review review = builder.build();
            responseObserver.onNext(review);
            builder.setId((String)"2");
            builder.setName((String)"Name2");
            builder.setStar((int)1);
            builder.setText((String)"Text2");
            builder.setProductId((String)"Product2");
            review = builder.build();
            responseObserver.onNext(review);
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
            logger.info("putReviews request" + 
            "; name:" + request.getName() + 
            "; star:" + request.getStar() + 
            "; id:" + request.getId() +
            "; productId:" + request.getProductId() +
            "; text:" + request.getText());

            responseObserver.onNext(addToDB(request));
            responseObserver.onCompleted();
        }

        /**
         * Adds a review sent over grpc to the "reviews" mongodb
         * @param request the review
         * @return empty grpc message
         */
        private ReviewServiceProto.Empty addToDB(ReviewServiceProto.Review request) {
            logger.info("*** Adding review to db!");
            /*
            MongoCollection<Document> reviews = db.getCollection("reviews");
            Document review = new Document("id", request.getId()).
                    append("name", request.getName()).
                    append("star", request.getStar()).
                    append("text", request.getText()).
                    append("product_id", request.getProductId());
            reviews.insertOne(review);
            */
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

        /*
        if(!db.listCollectionNames().into(new ArrayList<>()).contains("reviews")) {
            db.createCollection("reviews");
        }
        */

        System.out.println("Starting...");

        logger.info("Starting ReviewService");
    
        final ReviewService service = new ReviewService();
        try {
            service.start();
            System.out.println("Started... blocking until shutdown");
            service.blockUntilShutdown();
        } catch (Exception e) {
            e.printStackTrace();
            logger.info("Exception!");
        }

        System.out.println("Finishing...");

        /**Should be working fine now, but still here to fall back on
        MongoCollection<Document> reviews = db.getCollection("reviews");

        Document review = new Document("id", "one").
                append("name","peter").
                append("star", 5).
                append("text", "I like").
                append("product_id", 5);

        reviews.insertOne(review);

        Document test = reviews.findOneAndDelete(new Document("id", "one"));
        logger.info(test.toJson());
         **/
    }

}
