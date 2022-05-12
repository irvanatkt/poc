package com.tiket.tix.train.trx.poc.service;


import com.tiket.tix.train.trx.poc.entity.Cart;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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

  public Mono<String> reactiveAsync(String cartId) {
    RLockReactive orderLock = redissonClient.reactive()
        .getLock(String.format(Constant.LOCK_ORDER, cartId));

    CountDownLatch countDownLatch = new CountDownLatch(5);
    return orderLock.tryLock().filter(Boolean.TRUE::equals)
        .switchIfEmpty(Mono.defer(() -> {
          log.info("order is being updated by another process");
          return Mono.empty();
        }))
        .doOnSuccess(y -> log.info("order lock acquired"))
        .flatMap(orderIsLocked ->
            cartProcess(cartId)
                .repeat()
                .doOnNext(y -> {
                  countDownLatch.countDown();
                  log.info("countdown {} ", countDownLatch.getCount());
                })
                .takeUntil(counter -> countDownLatch.getCount() < 0)
                .collectList()
                .map(y -> "yeah"));
  }

  private Mono<Void> cartProcess(String cartId) {
    RLockReactive cartLock = redissonClient.reactive()
        .getLock(String.format(Constant.LOCK_CART, cartId));
    return cartLock.lock(-1, TimeUnit.SECONDS)
        .doOnNext(n -> log.info("cartLock acquired for in progress"))
        .map(cartIsLocked -> {
          log.info("cartLock acquired for in progress {}", 1);
          return 1;
        }) // update to progress
        .flatMap(counter -> cartLock.unlock().thenReturn(counter))
        .flatMap(counter -> Mono.fromCallable(() -> {
          TimeUnit.SECONDS.sleep(2);
          return counter + 1;
        })) // call KAI & order
        .flatMap(counter -> cartLock.lock(-1, TimeUnit.SECONDS))
        .doOnNext(n -> log.info("cartLock acquired for done"))
        .flatMap(counter1 -> cartLock.unlock());
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
