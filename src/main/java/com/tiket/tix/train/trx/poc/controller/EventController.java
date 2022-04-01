package com.tiket.tix.train.trx.poc.controller;

import com.tiket.tix.train.trx.poc.service.BookingService;
import com.tiket.tix.train.trx.poc.service.ChangeSeatService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@Api(value = "event controller")
public class EventController {

  @Autowired
  private BookingService bookingService;

  @Autowired
  private ChangeSeatService changeSeatService;

  @GetMapping(path = "/booking")
  public Mono<String> booking(@RequestParam(required = false) String cartId,
      @RequestParam(required = false) String schedule) {
    return bookingService.booking(cartId).publishOn(Schedulers.elastic());
  }

  @GetMapping(path = "/changeseat")
  public Mono<String> changeSeat(@RequestParam(required = false) String cartId,
      @RequestParam(required = false) String newSeat,
      @RequestParam(required = false) String bookingCode) {
    return changeSeatService.changeSeat(cartId, newSeat, bookingCode)
        .publishOn(Schedulers.elastic());
  }
}
