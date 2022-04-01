
# POC Train 4.0

To ensure is it feasible to develop Train 4.0 with async worker thread and redis locking mechanism




## Main Service

- [Booking Service](https://github.com/irvanatkt/poc/blob/main/src/main/java/com/tiket/tix/train/trx/poc/service/BookingService.java) -> Upsert booking & trigger async worker (booking).
- [Change Seat Service](https://github.com/irvanatkt/poc/blob/main/src/main/java/com/tiket/tix/train/trx/poc/service/ChangeSeatService.java) -> Update based on cartId & trigger async worker (changeseat).
- [Async Worker](https://github.com/irvanatkt/poc/blob/main/src/main/java/com/tiket/tix/train/trx/poc/service/AsyncWorker.java) -> Main async worker for booking & changeseat. Also running incomplete state if any.

### Quick Notes
- We have 2 locks for whole process `LOCK_CART` & `LOCK_ORDER`
- We using ready [redisson](https://github.com/redisson/redisson) which supports [many distributed locks strategies](https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers) and [with reactive way](https://github.com/redisson/redisson/wiki/3.-operations-execution#32-reactive-way)
  also has feature to immediate unlock + releasing lock after `n` time
### TODO & Need investigation
- Default TTL & configuration lock -> [Done](https://github.com/redisson/redisson/wiki/2.-Configuration#lockwatchdogtimeout)
- Handling Error within async worker
- Continuing `TODO` cart status after finish one state in async worker
## Samples
**Booking**
```java
public Mono<String> booking(String cartId) {

    return Mono.just(redissonReactiveClient.getLock(String.format(Constant.LOCK_CART, cartId)))
        .flatMap(fLock ->
            fLock.lock(-1, TimeUnit.MILLISECONDS, 1)
                .doOnSubscribe(l -> log.info("getting cart lock, updating booking cart"))
                .then(reactiveMongoTemplate.upsert(
                    new Query().addCriteria(Criteria.where("cart_id").is(cartId)),
                    new Update().push("events",
                        Event.builder().event("book").status("TODO").build()),
                    Cart.class))
                .flatMap(cart -> fLock.unlock(1).thenReturn(cart))
                .doOnNext(cart -> asyncWorker.longAsyncProcess(cartId))
                .doOnNext(result -> log.info("finished update cart & booking thread"))
                .map(result -> String.valueOf(result.getModifiedCount()))
                .switchIfEmpty(Mono.error(new Throwable("error")))
        );
  }
```
**Async Worker Snippet**
```java
public void longAsyncProcess(String cartId) {
    executorService.execute(() -> {
      RLock orderLock = redissonClient.getLock(String.format(Constant.LOCK_ORDER, cartId));
      RLock cartLock = redissonClient.getLock(String.format(Constant.LOCK_CART, cartId));

      try {
        log.info("running longBookingProcess");
        if (orderLock.isLocked()) {
          log.info("order is being updated by another process");
        } else {
```


## API Reference

#### Upsert booking

```http
  GET /booking
```

| Parameter | Type     | Description                |
| :-------- | :------- | :------------------------- |
| `cartId` | `string` | **Required**. Cart ID |

#### Upsert booking

```http
  GET /changeseat
```

| Parameter | Type     | Description                |
| :-------- | :------- | :------------------------- |
| `cartId` | `string` | **Required**. Cart ID |
| `newSeat` | `string` | **Required**. New Seat |
| `booking` | `string` | **Required**. Booking Code |


## Authors

- [@irvanatkt](https://github.com/irvanatkt)

