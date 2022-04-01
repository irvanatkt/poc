package com.tiket.tix.train.trx.poc.entity;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "cart")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cart implements Serializable {
  @Id
  @Field(value = "_id")
  private String id;

  @Field(value = "cart_id")
  private String cartId;

  @Field(value = "events")
  private List<Event> events;

  @SuperBuilder(toBuilder = true)
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static final class Event{
    private String event;
    private String status;


  }
}
