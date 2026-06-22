//package charg.ing.stations.websocketservice.config;
//
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import lombok.extern.slf4j.Slf4j;
//import org.reactivestreams.Publisher;
//import org.springframework.context.annotation.Configuration;
//import reactor.core.publisher.Hooks;
//import reactor.core.publisher.Operators;
//
//import java.util.function.Function;
//
//
//@Configuration
//@Slf4j
//public class ReactorDebugConfig {
//
//    private static final String REACTOR_DEBUG_KEY = "websocket-service";
//
//    @PostConstruct
//    public void enableReactorDebug() {
//        log.info("Enabling Reactor debug logging...");
//
//        // Включаем отладку Reactor
//        Hooks.onOperatorDebug();
//
//        // Добавляем обработчик ошибок
//        Hooks.onErrorDropped(error ->
//                log.error("Reactor dropped error: {}", error.getMessage(), error)
//        );
//
//        // Логирование всех операций Reactor
//        Function<?, ? extends Publisher<Object>> loggingLift = Operators.liftPublisher((publisher, subscriber) -> {
//            log.debug("Reactor Publisher created: {}", publisher.getClass().getSimpleName());
//            return subscriber;
//        });
//
//        Hooks.onEachOperator(REACTOR_DEBUG_KEY, (Function<? super Publisher<Object>, ? extends Publisher<Object>>) loggingLift);
//    }
//
//    @PreDestroy
//    public void cleanup() {
//        log.info("Cleaning up Reactor debug hooks...");
//        Hooks.resetOnOperatorDebug();
//        Hooks.resetOnErrorDropped();
//        Hooks.resetOnEachOperator(REACTOR_DEBUG_KEY);
//    }
//}