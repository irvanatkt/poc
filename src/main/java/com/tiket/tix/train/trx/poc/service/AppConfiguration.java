package com.tiket.tix.train.trx.poc.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;

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
    return Executors.newFixedThreadPool(10);
  }

}
