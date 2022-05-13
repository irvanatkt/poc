package com.tiket.tix.train.trx.poc.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@org.springframework.context.annotation.Configuration
public class AppConfiguration {

  public AppConfiguration() {
  }

  private RedissonClient redissonClient;

  @Bean
  public RedissonReactiveClient redissonReactiveClient() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://localhost:6379");
    redissonClient =Redisson.create(config);
    return redissonClient.reactive();
  }

  @Bean
  public RedissonClient redissonClient(){
    return redissonClient;
  }

  @Bean
  public ExecutorService executorService() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("async-train-%d").build();
    return Executors.newFixedThreadPool(10, threadFactory);
  }

  @Bean
  public Scheduler scheduler(){
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("reactive-%d").build();
    return Schedulers.fromExecutorService(Executors.newFixedThreadPool(10, threadFactory));
  }

}
