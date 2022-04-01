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

@Service
@Slf4j
public class ChangeSeatService {

  public ChangeSeatService() {
  }

  @Autowired
  private RedissonReactiveClient redissonReactiveClient;

  @Autowired
  private ReactiveMongoTemplate reactiveMongoTemplate;

  @Autowired
  private AsyncWorker asyncWorker;

  public Mono<String> changeSeat(String cartId, String newSeat, String bookingCode) {
    return Mono.just(redissonReactiveClient.getLock(String.format(Constant.LOCK_CART, cartId)))
        .flatMap(fLock ->
            fLock.lock(-1, TimeUnit.MILLISECONDS, 1)
                .doOnSubscribe(l -> log.info("getting cart lock, updating changeseat cart"))
                .then(reactiveMongoTemplate.findAndModify(
                    new Query().addCriteria(Criteria.where("cart_id").is(cartId)),
                    new Update().push("events",
                        Event.builder().event("changeseat").status("TODO").build()),
                    Cart.class))
                .flatMap(cart -> fLock.unlock(1).thenReturn(cart))
                .doOnNext(cart -> asyncWorker.longAsyncProcess(cartId))
                .doOnNext(result -> log.info("finished update cart & booking thread"))
                .map(result -> String.valueOf(result.getCartId()))
                .switchIfEmpty(Mono.error(new Throwable("error")))
        );
  }

}
