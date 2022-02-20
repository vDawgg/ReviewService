package hipstershop;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import hipstershop.ReviewServiceGrpc;
import hipstershop.ReviewServiceProto;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ReviewServiceClient {

    Logger logger = LogManager.getLogger(ReviewServiceClient.class);

    private final ManagedChannel channel;
    private final ReviewServiceGrpc.ReviewServiceBlockingStub blockingStub;

    private ReviewServiceClient(String host, int port) {
        //Builds a channel for communication with the server
        this(
                ManagedChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .build());
    }

    private ReviewServiceClient(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = ReviewServiceGrpc.newBlockingStub(channel);
    }

    //TODO: implement boolean logic!
    public void getReviews(String product_id) {
        logger.info("Getting reviews of product: "+product_id);
        ReviewServiceProto.ProductID product = ReviewServiceProto.ProductID.newBuilder()
                .setProductId(product_id)
                .build();

        ReviewServiceProto.Reviews reviews;

        try {
            reviews = blockingStub.getReviews(product);
        } catch (StatusRuntimeException e) {
            logger.log(org.apache.logging.log4j.Level.WARN, "RPC failed: " + e.getStatus());
            return;
        }

        List<ReviewServiceProto.Review> reviewList = reviews.getReviewList();
        for(ReviewServiceProto.Review r : reviewList) {
            logger.info(r.getText()); //Should be changed to something that has more information
        }
    }

    //Taken from AdServiceClient
    private void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void putReview(ReviewServiceProto.Review review) {
        logger.info("Sending in a review: "+review);

        try {
            blockingStub.putReviews(review);
        } catch (StatusRuntimeException e){
            logger.log(org.apache.logging.log4j.Level.WARN, "RPC failed: " + e.getStatus());
        }
    }

    public static void main(String[] args) throws InterruptedException {

        //TODO: Find out what RpcViews does and whether or not it is needed for this service

        //This should be done more intelligently
        final String host = "0.0.0.0";
        final int serverPort = 6666;

        ReviewServiceClient client = new ReviewServiceClient(host, serverPort);

        /** Shouldnt be needed in production
        ReviewServiceProto.Review r = ReviewServiceProto.Review.newBuilder()
                .setProductId("sunglasses")
                .setStar(5)
                .setName("Client")
                .setText("This is a nice review")
                .setId("1")
                .build();


        try {
            client.putReview(r);
            client.getReviews("sunglasses");
        } finally {
            client.shutdown();
        }**/

    }

}
