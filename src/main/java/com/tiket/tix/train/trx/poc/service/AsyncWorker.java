package com.tiket.tix.train.trx.poc.service;


import com.tiket.tix.train.trx.poc.entity.Cart;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
@Slf4j
public class AsyncWorker {

  @Autowired
  private ExecutorService executorService;

  @Autowired
  private RedissonClient redissonClient;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private Scheduler scheduler;

  public AsyncWorker() {
  }

  public Mono<Void> reactiveAsync(String cartId) {
    RLockReactive orderLock = redissonClient.reactive()
        .getLock(String.format(Constant.LOCK_ORDER, cartId));

    CountDownLatch countDownLatch = new CountDownLatch(2);
    return orderLock.tryLock(1).filter(Boolean.TRUE::equals)
        .switchIfEmpty(Mono.defer(() -> {
          log.info("order is being updated by another process");
          return Mono.empty();
        }))
        .doOnSuccess(y -> log.info("order lock acquired"))
        .flatMap(orderIsLocked ->
            Mono.defer(() -> {
              AtomicBoolean isContinue = new AtomicBoolean(true);
              return cartProcess(cartId)
                  .doOnNext(y -> {
                    if (y == 3) {
                      isContinue.set(false);
                    }
                  })
                  .repeat(isContinue::get).collectList();
            }))
        .doOnSuccess(y -> log.info("finishing all"))
        .then(orderLock.unlock(1));
  }

  private Mono<Integer> cartProcess(String cartId) {
    RLockReactive cartLock = redissonClient.reactive()
        .getLock(String.format(Constant.LOCK_CART, cartId));
    return cartLock.lock(-1, TimeUnit.SECONDS, 1).thenReturn(cartLock)
        .doOnSuccess(n -> log.info("cartLock acquired for in progress"))
//        .map(lock -> 1) // update to progress
        .flatMap(lock -> lock.unlock(1).thenReturn(lock))
        .doOnSuccess(n -> log.info("executing log process"))

//        .flatMap(counter -> Mono.fromCallable(() -> counter + 1)) // call KAI & order
        .flatMap(lock -> lock.lock(-1, TimeUnit.SECONDS, 1).thenReturn(lock))
        .doOnNext(n -> log.info("cartLock acquired for done"))
        .flatMap(lock -> cartLock.unlock(1))
        .map(y -> 3);
  }

  public void longAsyncProcess(String cartId) {
    executorService.execute(() -> {
      RLock orderLock = redissonClient.getLock(String.format(Constant.LOCK_ORDER, cartId));
      RLock cartLock = redissonClient.getLock(String.format(Constant.LOCK_CART, cartId));

      try {
        log.info("running longBookingProcess");
        if (orderLock.isLocked()) {
          log.info("order is being updated by another process");
        } else {
          orderLock.lock(-1, TimeUnit.SECONDS);
          do {
            // update to IN_PROGRESS
            cartLock.lock();
            Cart cart = mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("cart_id").is(cartId)), Cart.class);
            assert cart != null;
            for (int i = 0; i < cart.getEvents().size(); i++) {
              if ("TODO".equalsIgnoreCase(cart.getEvents().get(i).getStatus())) {
                mongoTemplate.updateFirst(
                    new Query().addCriteria(Criteria.where("cart_id").is(cartId)),
                    new Update().set("events." + i + ".status", "IN_PROGRESS"),
                    Cart.class
                );
                break;
              }
            }
            cartLock.unlock();

            // long call to productOrder + KAI based on cart with stated TODO
            TimeUnit.SECONDS.sleep(10);

            // update to DONE
            cartLock.lock();
            cart = mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("cart_id").is(cartId)), Cart.class);
            assert cart != null;
            for (int i = 0; i < cart.getEvents().size(); i++) {
              if ("IN_PROGRESS".equalsIgnoreCase(cart.getEvents().get(i).getStatus())) {
                mongoTemplate.updateFirst(
                    new Query().addCriteria(Criteria.where("cart_id").is(cartId)),
                    new Update().set("events." + i + ".status", "DONE"),
                    Cart.class
                );
                break;
              }
            }
            cartLock.unlock();

          } while ("cart has its state to process".equalsIgnoreCase(cartId));
          // cart has its state to process : retrieve next TODO status if any
          orderLock.unlock();
          log.info("finishing for cartid {}", cartId);
        }


      } catch (InterruptedException e) {
        log.error("Error getting lock", e);
      }
    });

  }
}
