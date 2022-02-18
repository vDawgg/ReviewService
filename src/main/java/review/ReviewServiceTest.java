package review;

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
import review.service.ReviewServiceGrpc;
import review.service.ReviewServiceProto;

import java.util.List;

import static org.junit.Assert.assertEquals;

//Adapted from https://github.com/grpc/grpc-java/blob/master/examples/src/test/java/io/grpc/examples/routeguide/RouteGuideServerTest.java
@RunWith(JUnit4.class)
public class ReviewServiceTest {

    //maybe add grpc cleanup rule here


    private ReviewService server;
    private ManagedChannel inProcessChannel;
    BoolValue t = BoolValue.newBuilder().setValue(true).build();
    BoolValue f = BoolValue.newBuilder().setValue(false).build();

    @Before
    public void setUp() throws Exception {
        String servername = InProcessServerBuilder.generateName();

        server = new ReviewService(
                InProcessServerBuilder.forName(servername), 0
        );

        server.start();

        inProcessChannel = InProcessChannelBuilder.forName(servername).directExecutor().build();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        MongoClient client = MongoClients.create();
        MongoDatabase db = client.getDatabase("reviewDB");
        Bson f1 = new Document("text","Hallihallo");
        db.getCollection("reviews").deleteMany(f1);

    }

    @Test
    public void putAndGetReviews() throws Exception { //Might have to be renamed

        ReviewServiceGrpc.ReviewServiceBlockingStub stub = ReviewServiceGrpc.newBlockingStub(inProcessChannel);

        ReviewServiceProto.Review normalReview = ReviewServiceProto.Review.newBuilder()
                .setText("Hallihallo")
                .setName("Mr X")
                .setStar(5)
                .setProductId("sunglasses") //Might have to be replaced with actual productids
                .build();

        BoolValue b = stub.putReviews(normalReview);

        assertEquals(t, b);

        List<ReviewServiceProto.Review> responseList = stub.getReviews(ReviewServiceProto.ProductID.newBuilder()
                .setProductId("sunglasses")
                .build())
                .getReviewList();

        //TODO: Find a prettier alternative to this -> this might lead to nullpointers
        ReviewServiceProto.Review response = responseList.stream().filter(t -> t.equals(normalReview)).findFirst().get();

        assertEquals(response, response);

    }

    @Test
    public void putWithMissingField() throws Exception {

        ReviewServiceGrpc.ReviewServiceBlockingStub stub = ReviewServiceGrpc.newBlockingStub(inProcessChannel);

        ReviewServiceProto.Review review = ReviewServiceProto.Review.newBuilder()
                .setText("Hallihallo")
                .setName("Mr X")
                .setStar(5)
                .build();

        BoolValue b = stub.putReviews(review);

        assertEquals(f, b);

    }
}