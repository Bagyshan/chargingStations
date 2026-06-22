//package charg.ing.stations.kafka;
//
//import charg.ing.stations.dto.BookingPaymentRequest;
//import charg.ing.stations.dto.BookingPaymentResponse;
//import charg.ing.stations.service.PaymentService;
//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.messaging.handler.annotation.Payload;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Mono;
//
//@Component
//public class BookingPaymentListener {
//
//    private final PaymentService paymentService;
//    private final KafkaTemplate<String, BookingPaymentResponse> kafkaTemplate;
//    private final Log log = LogFactory.getLog(BookingPaymentListener.class);
//
//    public BookingPaymentListener(PaymentService paymentService,
//                                  KafkaTemplate<String, BookingPaymentResponse> kafkaTemplate) {
//        this.paymentService = paymentService;
//        this.kafkaTemplate = kafkaTemplate;
//    }
//
//    @KafkaListener(topics = "booking.payment.requests", groupId = "payment-service-group", containerFactory = "kafkaListenerContainerFactory")
//    public void onMessage(@Payload BookingPaymentRequest request) {
//        log.info("Received booking.payment.request: " + request.getRequestId() + " type=" + request.getType());
//
//        try {
//            if ("get_balance".equalsIgnoreCase(request.getType())) {
//                handleGetBalance(request);
//            } else if ("top_up".equalsIgnoreCase(request.getType())) {
//                handleTopUp(request);
//            } else {
//                sendErrorResponse(request, "unsupported_type");
//            }
//        } catch (Exception ex) {
//            log.error("Error handling message", ex);
//            sendErrorResponse(request, ex.getMessage());
//        }
//    }
//
//    private void handleGetBalance(BookingPaymentRequest request) {
//        paymentService.getBalance(request.getUserId())
//                .doOnNext(ub -> {
//                    BookingPaymentResponse resp = new BookingPaymentResponse();
//                    resp.setRequestId(request.getRequestId());
//                    resp.setUserId(ub.getUserId());
//                    resp.setBalance(ub.getBalance());
//                    resp.setBooking(ub.isBooking());
//                    resp.setStatus("OK");
//                    kafkaTemplate.send("booking.payment.responses", resp.getRequestId(), resp);
//                })
//                .subscribe(); // fire-and-forget
//    }
//
//    private void handleTopUp(BookingPaymentRequest request) {
//        if (request.getAmount() == null) {
//            sendErrorResponse(request, "missing_amount");
//            return;
//        }
//        paymentService.topUp(request.getUserId(), request.getAmount())
//                .doOnNext(ub -> {
//                    BookingPaymentResponse resp = new BookingPaymentResponse();
//                    resp.setRequestId(request.getRequestId());
//                    resp.setUserId(ub.getUserId());
//                    resp.setBalance(ub.getBalance());
//                    resp.setBooking(ub.isBooking());
//                    resp.setStatus("OK");
//                    kafkaTemplate.send("booking.payment.responses", resp.getRequestId(), resp);
//                })
//                .doOnError(err -> sendErrorResponse(request, err.getMessage()))
//                .subscribe();
//    }
//
//    private void sendErrorResponse(BookingPaymentRequest request, String errorMessage) {
//        BookingPaymentResponse resp = new BookingPaymentResponse();
//        resp.setRequestId(request != null ? request.getRequestId() : null);
//        if (request != null) resp.setUserId(request.getUserId());
//        resp.setStatus("ERROR");
//        resp.setErrorMessage(errorMessage);
//        kafkaTemplate.send("booking.payment.responses", resp.getRequestId(), resp);
//    }
//}
