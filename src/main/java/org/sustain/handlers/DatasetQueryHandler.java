package org.sustain.handlers;

import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sustain.DatasetRequest;
import org.sustain.DatasetResponse;
import org.sustain.controllers.DatasetController;

import java.util.concurrent.LinkedBlockingQueue;

public class DatasetQueryHandler extends GrpcHandler<DatasetRequest, DatasetResponse> {

    private static final Logger log = LogManager.getLogger(DatasetQueryHandler.class);

    private boolean fetchingCompleted = false;

    public DatasetQueryHandler(DatasetRequest request, StreamObserver<DatasetResponse> responseObserver) {
       super(request, responseObserver);
    }

    @Override
    public void handleRequest() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        new StreamWriter(queue, responseObserver).start();
        DatasetController.getData(request, queue);
        fetchingCompleted = true;
    }

    private class StreamWriter extends Thread {
        private volatile LinkedBlockingQueue<String> data;
        private StreamObserver<DatasetResponse> responseObserver;

        public StreamWriter(LinkedBlockingQueue<String> data, StreamObserver<DatasetResponse> responseObserver) {
            this.data = data;
            this.responseObserver = responseObserver;
        }

        @Override
        public void run() {
            log.info("Starting StreamWriter thread");
            int count = 0;
            while (!fetchingCompleted) {
                if (data.size() > 0) {
                    String datum = data.remove();
                    responseObserver.onNext(DatasetResponse.newBuilder().setResponse(datum).build());
                    count++;
                }
            }

            // if there is any data remaining in the queue after fetching is completed
            for (String datum : data) {
                responseObserver.onNext(DatasetResponse.newBuilder().setResponse(datum).build());
                count++;
            }

            log.info("Streaming completed! No. of entries: " + count);
            responseObserver.onCompleted();
        }
    }
}
