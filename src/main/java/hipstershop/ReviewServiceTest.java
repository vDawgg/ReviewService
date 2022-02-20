package hipstershop;

import com.google.protobuf.BoolValue;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import hipstershop.ReviewServiceGrpc;
import hipstershop.ReviewServiceProto;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;


//Adapted from https://github.com/grpc/grpc-java/blob/master/examples/src/test/java/io/grpc/examples/routeguide/RouteGuideServerTest.java

/**
 * Tests for the ReviewService
 * Has to be run locally; Used with local mongodb server and IntelliJs test runner
 */
@RunWith(JUnit4.class)
public class ReviewServiceTest {

    private ReviewService server;
    private ManagedChannel inProcessChannel;
    BoolValue t = BoolValue.newBuilder().setValue(true).build();
    BoolValue f = BoolValue.newBuilder().setValue(false).build();

    /**
     * starts an in-process server and establishes an in-process channel with it
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        String servername = InProcessServerBuilder.generateName();
        server = new ReviewService(
                InProcessServerBuilder.forName(servername), 0
        );
        server.start();
        inProcessChannel = InProcessChannelBuilder.forName(servername).directExecutor().build();
    }

    /**
     * stops the server and deletes the reviews that were added from the db again
     */
    @After
    public void tearDown() {
        server.stop();
        MongoClient client = MongoClients.create();
        MongoDatabase db = client.getDatabase("reviewDB");
        Bson f1 = new Document("text","Hallihallo");
        db.getCollection("reviews").deleteMany(f1);
    }

    /**
     * Sends a review to the service and retrieves it again, checking if its the same review
     */
    @Test
    public void putAndGetReviews() {
        ReviewServiceGrpc.ReviewServiceBlockingStub stub = ReviewServiceGrpc.newBlockingStub(inProcessChannel);
        ReviewServiceProto.Review normalReview = ReviewServiceProto.Review.newBuilder()
                .setText("Hallihallo")
                .setName("Mr X")
                .setStar(5)
                .setProductId("sunglasses")
                .build();
        BoolValue b = stub.putReviews(normalReview);
        assertEquals(t, b);
        List<ReviewServiceProto.Review> responseList = stub.getReviews(ReviewServiceProto.ProductID.newBuilder()
                .setProductId("sunglasses")
                .build())
                .getReviewList();

        ReviewServiceProto.Review response = responseList.stream().filter(t -> t.equals(normalReview)).findFirst().get();
        assertEquals(response, response);
    }

    /**
     * tries to let the service add a malformed review to the db, checks if it fails
     */
    @Test
    public void putWithMissingField() {
        ReviewServiceGrpc.ReviewServiceBlockingStub stub = ReviewServiceGrpc.newBlockingStub(inProcessChannel);
        ReviewServiceProto.Review review = ReviewServiceProto.Review.newBuilder()
                .setText("Hallihallo")
                .setName("Mr X")
                .setStar(5)
                .build();
        BoolValue b = stub.putReviews(review);
        assertEquals(f, b);
    }

    /**
     * Sends and retrieves multiple reviews to and from the service, checking if they are the same
     */
    @Test
    public void putAndRetrieveMultiple() {
        ReviewServiceGrpc.ReviewServiceBlockingStub stub = ReviewServiceGrpc.newBlockingStub(inProcessChannel);

        List<ReviewServiceProto.Review> reviewList= new ArrayList<>();

        ReviewServiceProto.Review r1 = ReviewServiceProto.Review.newBuilder()
                .setText("Hallihallo")
                .setName("Mr X")
                .setStar(5)
                .setProductId("shirt")
                .build();
        reviewList.add(r1);
        ReviewServiceProto.Review r2 = ReviewServiceProto.Review.newBuilder()
                .setText("Hallihallo")
                .setName("Mr X")
                .setStar(5)
                .setProductId("shirt")
                .build();
        reviewList.add(r2);
        ReviewServiceProto.Review r3 = ReviewServiceProto.Review.newBuilder()
                .setText("Hallihallo")
                .setName("Mr X")
                .setStar(5)
                .setProductId("shirt")
                .build();
        reviewList.add(r3);

        BoolValue b1 = stub.putReviews(r1); assertEquals(t, b1);
        BoolValue b2 = stub.putReviews(r2); assertEquals(t, b2);
        BoolValue b3 = stub.putReviews(r3); assertEquals(t, b3);

        List<ReviewServiceProto.Review> responseList = stub.getReviews(ReviewServiceProto.ProductID.newBuilder()
                        .setProductId("shirt")
                        .build())
                .getReviewList();

        for(int i = 0; i<3; i++) {
            assertEquals(responseList.get(i), reviewList.get(i));
        }

    }
}