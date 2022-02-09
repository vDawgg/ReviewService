package review;

import com.mongodb.MongoException;
import com.mongodb.client.*;
import io.grpc.stub.StreamObserver;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.Document;
import org.bson.conversions.Bson;
import review.service.ReviewServiceGrpc;
import review.service.ReviewServiceProto;

import java.util.ArrayList;
import java.util.logging.Logger;

public class ReviewService {

    private static final Logger logger = Logger.getLogger("ReviewServiceLogger");

    //TODO: decide on tracing with jaeger (java library deprecated) or opentelemetry (version suggested by jaeger)

    //Objects for mongodb
    private static final MongoClient client = MongoClients.create();
    private static final MongoDatabase db = client.getDatabase("reviewDB");

    private static class ReviewServiceImpl extends ReviewServiceGrpc.ReviewServiceImplBase {

        @Override
        public void getReviews(ReviewServiceProto.Product request, StreamObserver<ReviewServiceProto.Review> responseObserver) {
            MongoCollection<Document> reviews = db.getCollection("reviews");
            FindIterable<Document> iterable = reviews.find(new Document("product_id", request.getProductId()));
            for(Document d : iterable) {
                responseObserver.onNext(documentToRPC(d));
            }
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

        /**
         * Adds a review sent over grpc to the "reviews" mongodb
         * @param request the review
         * @return empty grpc message
         */
        private ReviewServiceProto.Empty addToDB(ReviewServiceProto.Review request) {
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

    public static void main(String[] args) {

        //Testing of connection below - Taken from https://docs.mongodb.com/drivers/java/sync/current/fundamentals/connection/connect/
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
