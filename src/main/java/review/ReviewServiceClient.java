package review;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import review.service.ReviewServiceGrpc;
import review.service.ReviewServiceProto;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReviewServiceClient {

    Logger logger = Logger.getLogger("ReviewServiceClient");

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

    public void getReviews(String product_id) {
        logger.info("Getting reviews of product: "+product_id);
        ReviewServiceProto.Product product = ReviewServiceProto.Product.newBuilder().setProductId("sunglasses").build(); //Needs to be changed for real testing
        Iterator<ReviewServiceProto.Review> reviews;

        try {
            reviews = blockingStub.getReviews(product);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: " + e.getStatus());
            return;
        }

        for (Iterator<ReviewServiceProto.Review> it = reviews; it.hasNext(); ) {
            ReviewServiceProto.Review review = it.next();
            logger.info(review.getText()); //Should be changed to something that has more information
        }
    }

    //Taken from AdServiceClient
    private void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    //TODO: Implement Review.toString()

    public void putReview(ReviewServiceProto.Review review) {
        logger.info("Sending in a review: "+review);

        try {
            blockingStub.putReviews(review);
        } catch (StatusRuntimeException e){
            logger.log(Level.WARNING, "RPC failed: " + e.getStatus());
            return;
        }
    }

    public static void main(String[] args) throws InterruptedException {

        //TODO: Find out what RpcViews does and whether or not it is needed for this service

        //This should be done more intelligently
        final String host = "localhost";
        final int serverPort = 6666;

        ReviewServiceProto.Review r = ReviewServiceProto.Review.newBuilder()
                .setProductId("sunglasses")
                .setStar(5)
                .setName("Client")
                .setText("This is a nice review")
                .setId("1")
                .build();

        ReviewServiceClient client = new ReviewServiceClient(host, serverPort);
        try {
            client.putReview(r);
            client.getReviews("sunglasses");
        } finally {
            client.shutdown();
        }

    }

}
