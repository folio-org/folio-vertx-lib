package org.folio.tlib.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.sqlclient.templates.annotations.RowMapped;
import java.util.UUID;

@DataObject
@RowMapped
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Book {

  private UUID id;

  private String title;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }
}
