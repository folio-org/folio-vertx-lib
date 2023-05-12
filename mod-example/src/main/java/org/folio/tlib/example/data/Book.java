package org.folio.tlib.example.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.sqlclient.templates.annotations.Column;
import io.vertx.sqlclient.templates.annotations.RowMapped;
import java.util.UUID;

/**
 * Book DAO.
 */
@DataObject
@RowMapped
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Book {

  private UUID id;

  private String title;

  @Column(name = "index_title")
  private String indexTitle;

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

  public String getIndexTitle() {
    return indexTitle;
  }

  public void setIndexTitle(String indexTitle) {
    this.indexTitle = indexTitle;
  }
}
