package com.tiket.tix.train.trx.poc.service;

import com.tiket.tix.train.trx.poc.entity.Cart;
import com.tiket.tix.train.trx.poc.entity.Cart.Event;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
@Slf4j
public class BookingService {

  public BookingService() {
  }

  @Autowired
  private RedissonReactiveClient redissonReactiveClient;

  @Autowired
  private ReactiveMongoTemplate reactiveMongoTemplate;

  @Autowired
  private AsyncWorker asyncWorker;

  @Autowired
  private Scheduler scheduler;

  public Mono<String> booking(String cartId) {

    return Mono.just(redissonReactiveClient.getLock(String.format(Constant.LOCK_CART, cartId)))
        .flatMap(fLock ->
            fLock.lock(-1, TimeUnit.MILLISECONDS, 1)
                .doOnSuccess(l -> log.info("getting cart lock, updating booking cart"))
                .then(reactiveMongoTemplate.upsert(
                    new Query().addCriteria(Criteria.where("cart_id").is(cartId)),
                    new Update().push("events",
                        Event.builder().event("book").status("TODO").build()),
                    Cart.class))
                .flatMap(cart -> fLock.unlock(1).doOnSuccess(y -> log.info("released cart lock"))
                    .thenReturn(cart))
                .doOnSuccess(
                    cart -> asyncWorker.reactiveAsync(cartId).subscribeOn(scheduler).subscribe())
                .doOnNext(result -> log.info("finished update cart & booking thread"))
                .map(result -> String.valueOf(result.getModifiedCount()))
                .switchIfEmpty(Mono.error(new Throwable("error")))
        );
  }

}
